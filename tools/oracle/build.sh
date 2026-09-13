#!/usr/bin/env bash
# T02 build recipe for the SWT Visual Oracle.
#
# Builds one SWT rendering backend with plain javac and prints its runtime
# classpath on stdout (nothing else on stdout; diagnostics go to stderr).
#
# Usage:
#   build.sh <backend-id>    build one backend, print its classpath
#   build.sh --all           build every backend declared available
#
# Backends: native | skia-canvas | skija-proto | native-baseline | native-candidate
#
# native-baseline and native-candidate build stock SWT from another source,
# given by ORACLE_BASELINE (default master) and ORACLE_CANDIDATE: either a git
# ref of this repository or a directory holding an SWT checkout (uncommitted
# changes included). Their classpath has a sibling lib/ with that source's
# natives.
#
# Idempotent: unchanged sources make a run a no-op. All outputs live in a
# cache directory outside the worktree:
#   ${ORACLE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/swt-visual-oracle}
#
# Set ORACLE_REFRESH=1 to re-fetch the prototype-skija fork from origin.
set -euo pipefail

CACHE_ROOT="${ORACLE_CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/swt-visual-oracle}"
MAVEN_DIR="$CACHE_ROOT/maven"
CHECKOUT_DIR="$CACHE_ROOT/checkouts"
BUILD_DIR="$CACHE_ROOT/build"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKTREE_ID="$(printf '%s' "$(realpath "$REPO_ROOT")" | sha256sum | cut -c1-12)"
OUT_BASE="$BUILD_DIR/$WORKTREE_ID"

GTK_BIN_DIR="$REPO_ROOT/binaries/org.eclipse.swt.gtk.linux.x86_64"
SKIA_SRC_DIR="$REPO_ROOT/bundles/org.eclipse.swt.skia"

PROTO_REPO="https://github.com/swt-initiative31/prototype-skija"
PROTO_BRANCH="master"
PROTO_CHECKOUT="$CHECKOUT_DIR/prototype-skija"

die() { printf 'build.sh: %s\n' "$*" >&2; exit 1; }
info() { printf 'build.sh: %s\n' "$*" >&2; }

need_java() {
	command -v java >/dev/null 2>&1 || die "java not found in PATH"
	command -v javac >/dev/null 2>&1 || die "javac not found in PATH, install a JDK (21 or newer recommended)"
}

require_gtk_linux_x86_64() {
	local what="${1:-this backend}"
	if [ "$(uname -s)-$(uname -m)" != "Linux-x86_64" ]; then
		die "$what needs Linux on x86_64 (prebuilt GTK binaries), found $(uname -s)-$(uname -m)"
	fi
}

# Emit the source folders of an Eclipse bundle build.properties, one per line.
# Accepts either the build.properties file or its bundle directory.
parse_source_folders() {
	local bp="$1"
	[ -d "$bp" ] && bp="$bp/build.properties"
	[ -f "$bp" ] || die "missing $bp"
	local folder
	while IFS= read -r folder; do
		folder="${folder%%$'\r'}"
		[ -n "$folder" ] || continue
		printf '%s\n' "${folder%/}"
	done < <(sed -n '/^source\.\./,/^output/p' "$bp" \
		| tr -d '\\' \
		| sed 's/^source\.\.[[:space:]]*=//;s/^output\..*//' \
		| sed 's/,[[:space:]]*$//' \
		| sed 's/^[[:space:]]*//;s/[[:space:]]*$//' \
		| grep -v '^$')
}

# Compile every .java below the source folders of $1 into $2, using $3 as the
# extra javac classpath ("" for none).
compile_bundle() {
	local bp_dir="$1" out_dir="$2" extra_cp="$3"
	local bp="$bp_dir/build.properties"
	[ -f "$bp" ] || die "missing $bp"
	mkdir -p "$out_dir"
	local args_file="$out_dir/javac.args"
	: > "$args_file"
	local folder
	while IFS= read -r folder; do
		find "$bp_dir/$folder" -name '*.java' -print0
	done < <(parse_source_folders "$bp") \
		| while IFS= read -r -d '' f; do printf '"%s"\n' "$f"; done >> "$args_file"
	if [ ! -s "$args_file" ]; then
		die "no java sources found via $bp"
	fi
	javac -nowarn -encoding UTF-8 ${extra_cp:+-cp "$extra_cp"} -d "$out_dir" @"$args_file" 1>&2
}

# Copy non-java resources of every source folder into the class output dir,
# stripping the source folder prefix (what Tycho does for bin.includes).
copy_resources() {
	local bp_dir="$1" out_dir="$2"
	local folder f rel dest
	while IFS= read -r folder; do
		while IFS= read -r -d '' f; do
			rel="${f#"$bp_dir/$folder"/}"
			dest="$out_dir/$rel"
			mkdir -p "$(dirname "$dest")"
			cp "$f" "$dest"
		done < <(find "$bp_dir/$folder" -type f ! -name '*.java' -print0)
	done < <(parse_source_folders "$bp_dir")
}

# Fingerprint over every file below the source folders plus caller-supplied
# extra strings (jar checksums, git sha). Cheap enough to run per invocation.
compute_fingerprint() {
	local bp_dir="$1"
	shift
	local fp
	fp="$(while IFS= read -r folder; do
			find "$bp_dir/$folder" -type f -printf '%P %s %T@\n'
		done < <(parse_source_folders "$bp_dir") | LC_ALL=C sort | sha256sum | cut -d' ' -f1)"
	for extra in "$@"; do
		fp="$(printf '%s\n%s\n' "$fp" "$extra" | sha256sum | cut -d' ' -f1)"
	done
	printf '%s' "$fp"
}

up_to_date() {
	local out_dir="$1" fp_file="$2" fp="$3"
	[ -f "$fp_file" ] || return 1
	[ -d "$out_dir" ] && [ -n "$(ls -A "$out_dir" 2>/dev/null)" ] || return 1
	[ "$(cat "$fp_file" 2>/dev/null)" = "$fp" ]
}

ensure_skija_jars() {
	local jar_dir url sum file
	while IFS='|' read -r jar_dir url sum; do
		file="$MAVEN_DIR/$jar_dir"
		mkdir -p "$(dirname "$file")"
		if [ -f "$file" ]; then
			echo "$sum  $file" | sha256sum -c --quiet >/dev/null 2>&1 \
				|| die "cached jar $file has wrong checksum, delete it and retry"
		else
			info "downloading $(basename "$file") to cache"
			curl -fsSL -o "$file" "$url" || die "failed to download $url (offline? this backend needs network once)"
			echo "$sum  $file" | sha256sum -c --quiet >/dev/null 2>&1 \
				|| die "downloaded jar $file does not match pinned checksum"
		fi
	done <<-'EOF'
	io/github/humbleui/skija-shared/0.143.17/skija-shared-0.143.17.jar|https://repo1.maven.org/maven2/io/github/humbleui/skija-shared/0.143.17/skija-shared-0.143.17.jar|6213e04a09853ff4a2a2ddc63554981bc5f57aa82cc795f4cd9167aca7edb042
	io/github/humbleui/skija-linux-x64/0.143.17/skija-linux-x64-0.143.17.jar|https://repo1.maven.org/maven2/io/github/humbleui/skija-linux-x64/0.143.17/skija-linux-x64-0.143.17.jar|8cb4ad7d9952016cc90fca1831711380ac202fae6afb0f40519be4f9b456bc67
	io/github/humbleui/types/0.2.0/types-0.2.0.jar|https://repo1.maven.org/maven2/io/github/humbleui/types/0.2.0/types-0.2.0.jar|38d94d00770c4f261ffb50ee68d5da853c416c8fe7c57842f0e28049fc26cca8
	EOF
}

ensure_fork_checkout() {
	if [ -d "$PROTO_CHECKOUT/.git" ]; then
		if [ "${ORACLE_REFRESH:-0}" = "1" ]; then
			info "refreshing prototype-skija checkout from origin/$PROTO_BRANCH"
			git -C "$PROTO_CHECKOUT" fetch --depth 1 origin "$PROTO_BRANCH" >/dev/null 2>&1 \
				|| die "git fetch failed for $PROTO_REPO"
			git -C "$PROTO_CHECKOUT" reset --hard "origin/$PROTO_BRANCH" >/dev/null 2>&1 \
				|| die "git reset failed for $PROTO_CHECKOUT"
			git -C "$PROTO_CHECKOUT" clean -fd >/dev/null 2>&1 || true
		fi
	else
		info "cloning $PROTO_REPO ($PROTO_BRANCH) into cache"
		mkdir -p "$CHECKOUT_DIR"
		git clone --depth 1 -b "$PROTO_BRANCH" "$PROTO_REPO" "$PROTO_CHECKOUT" >/dev/null 2>&1 \
			|| die "failed to clone $PROTO_REPO (offline? this backend needs network once)"
	fi
	git -C "$PROTO_CHECKOUT" rev-parse HEAD >/dev/null 2>&1 \
		|| die "$PROTO_CHECKOUT is not a usable git checkout"
}

check_natives_present() {
	local bin_dir="$1" so
	so="$(ls "$bin_dir"/libswt-pi3-gtk-*.so 2>/dev/null | head -1 || true)"
	[ -n "$so" ] || die "no libswt-pi3-gtk-*.so in $bin_dir; install git-lfs and restore binaries (git lfs pull)"
	if grep -q '^version https://git-lfs' "$so" 2>/dev/null; then
		die "$so is an unresolved git-lfs pointer; run: git lfs install && git lfs pull"
	fi
}

build_native() {
	require_gtk_linux_x86_64 "native"
	check_natives_present "$GTK_BIN_DIR"
	need_java
	local out="$OUT_BASE/native/classes"
	local fp
	fp="$(compute_fingerprint "$GTK_BIN_DIR" "recipe-v2")"
	if ! up_to_date "$out" "$out/../.fingerprint-native" "$fp"; then
		info "compiling native backend (stock SWT GTK bundle)"
		rm -rf "$out"
		compile_bundle "$GTK_BIN_DIR" "$out" ""
		copy_resources "$GTK_BIN_DIR" "$out"
		printf '%s' "$fp" > "$out/../.fingerprint-native"
	else
		info "native backend up to date"
	fi
	printf '%s' "$out"
}

# Resolve an SWT source spec (directory or git ref) to a checkout directory,
# extracting a ref once per commit into the cache.
resolve_swt_source() {
	local spec="$1" sha dir tmp
	if [ -d "$spec" ]; then
		realpath "$spec"
		return
	fi
	sha="$(git -C "$REPO_ROOT" rev-parse --verify --quiet "$spec^{commit}")" \
		|| die "'$spec' is neither a directory nor a git ref of $REPO_ROOT"
	dir="$CHECKOUT_DIR/swt/$sha"
	if [ ! -d "$dir" ]; then
		info "extracting SWT at $spec ($sha) into cache"
		mkdir -p "$CHECKOUT_DIR/swt"
		tmp="$(mktemp -d "$CHECKOUT_DIR/swt/.extract-XXXXXX")"
		# git archive applies the LFS smudge filter, so natives arrive as real binaries
		git -C "$REPO_ROOT" archive "$sha" bundles/org.eclipse.swt binaries/legal_files \
			binaries/org.eclipse.swt.gtk.linux.x86_64 | tar -x -C "$tmp" \
			|| { rm -rf "$tmp"; die "git archive of $sha failed"; }
		mv -T "$tmp" "$dir" 2>/dev/null || rm -rf "$tmp"
	fi
	printf '%s' "$dir"
}

# Build stock SWT from the source named by $2 under backend id $1.
build_native_from() {
	local id="$1" spec="$2"
	require_gtk_linux_x86_64 "$id"
	need_java
	local src bin_dir key out_base out fp
	src="$(resolve_swt_source "$spec")" || exit 1
	bin_dir="$src/binaries/org.eclipse.swt.gtk.linux.x86_64"
	[ -f "$bin_dir/build.properties" ] || die "$src is not an SWT checkout (no $bin_dir/build.properties)"
	check_natives_present "$bin_dir"
	key="$(printf '%s' "$src" | sha256sum | cut -c1-12)"
	out_base="$BUILD_DIR/$id/$key"
	out="$out_base/classes"
	fp="$(compute_fingerprint "$bin_dir" "recipe-v2" "$(cd "$bin_dir" && sha256sum libswt-*.so | sha256sum)")"
	if ! up_to_date "$out" "$out_base/.fingerprint" "$fp"; then
		info "compiling $id backend (stock SWT from $spec)"
		rm -rf "$out"
		compile_bundle "$bin_dir" "$out" ""
		copy_resources "$bin_dir" "$out"
		printf '%s' "$fp" > "$out_base/.fingerprint"
	else
		info "$id backend up to date ($spec)"
	fi
	ln -sfn "$bin_dir" "$out_base/lib"
	printf '%s' "$out"
}

build_skia_canvas() {
	require_gtk_linux_x86_64 "skia-canvas"
	check_natives_present "$GTK_BIN_DIR"
	need_java
	[ -d "$SKIA_SRC_DIR/src" ] || die "$SKIA_SRC_DIR not found; this branch must contain PR 3231 (SWT.SKIA canvas)"
	ensure_skija_jars
	local out_main="$OUT_BASE/skia-canvas/classes-main"
	local out_skia="$OUT_BASE/skia-canvas/classes-skia"
	local jars
	jars="$MAVEN_DIR/io/github/humbleui/skija-shared/0.143.17/skija-shared-0.143.17.jar:$MAVEN_DIR/io/github/humbleui/types/0.2.0/types-0.2.0.jar"
	local jar_sums
	jar_sums="$(cd "$MAVEN_DIR" && sha256sum io/github/humbleui/*/0.143.17/*.jar io/github/humbleui/types/0.2.0/*.jar 2>/dev/null | sha256sum | cut -d' ' -f1)"
	local fp
	fp="$(compute_fingerprint "$GTK_BIN_DIR" "recipe-v2")"
	local fp_skia
	fp_skia="$(compute_fingerprint "$SKIA_SRC_DIR" "recipe-v2" "$jar_sums")"
	if ! up_to_date "$out_main" "$OUT_BASE/skia-canvas/.fingerprint-main" "$fp"; then
		info "compiling skia-canvas backend (host SWT bundle)"
		rm -rf "$out_main"
		compile_bundle "$GTK_BIN_DIR" "$out_main" ""
		copy_resources "$GTK_BIN_DIR" "$out_main"
		printf '%s' "$fp" > "$OUT_BASE/skia-canvas/.fingerprint-main"
	else
		info "skia-canvas host bundle up to date"
	fi
	if ! up_to_date "$out_skia" "$OUT_BASE/skia-canvas/.fingerprint-skia" "$fp_skia"; then
		info "compiling skia-canvas backend (org.eclipse.swt.skia fragment)"
		rm -rf "$out_skia"
		compile_bundle "$SKIA_SRC_DIR" "$out_skia" "$out_main:$jars"
		copy_resources "$SKIA_SRC_DIR" "$out_skia"
		printf '%s' "$fp_skia" > "$OUT_BASE/skia-canvas/.fingerprint-skia"
	else
		info "skia-canvas fragment up to date"
	fi
	printf '%s:%s:%s:%s' "$out_main" "$out_skia" \
		"$jars" \
		"$MAVEN_DIR/io/github/humbleui/skija-linux-x64/0.143.17/skija-linux-x64-0.143.17.jar"
}

build_skija_proto() {
	require_gtk_linux_x86_64 "skija-proto"
	need_java
	ensure_fork_checkout
	local proto_bin="$PROTO_CHECKOUT/binaries/org.eclipse.swt.gtk.linux.x86_64"
	[ -d "$proto_bin/lib" ] || die "fork checkout lacks $proto_bin/lib (skija jars); upstream removed them, pin a commit that still ships them"
	check_natives_present "$proto_bin"
	local out="$OUT_BASE/skija-proto/classes"
	local proto_sha
	proto_sha="$(git -C "$PROTO_CHECKOUT" rev-parse HEAD)"
	info "prototype-skija fork at $proto_sha"
	local jar_sums
	jar_sums="$(cd "$proto_bin/lib" && sha256sum *.jar | sha256sum | cut -d' ' -f1)"
	local fp
	fp="$(compute_fingerprint "$proto_bin" "recipe-v2" "$proto_sha" "$jar_sums")"
	if ! up_to_date "$out" "$out/../.fingerprint-skijaproto" "$fp"; then
		info "compiling skija-proto backend (prototype-skija fork at $proto_sha)"
		rm -rf "$out"
		compile_bundle "$proto_bin" "$out" "$proto_bin/lib/skija-shared-0.116.3.jar:$proto_bin/lib/skija-linux-x64-0.116.3.jar:$proto_bin/lib/types-0.1.1.jar"
		copy_resources "$proto_bin" "$out"
		printf '%s' "$fp" > "$out/../.fingerprint-skijaproto"
	else
		info "skija-proto backend up to date"
	fi
	printf '%s:%s:%s:%s' "$out" \
		"$proto_bin/lib/skija-shared-0.116.3.jar" \
		"$proto_bin/lib/skija-linux-x64-0.116.3.jar" \
		"$proto_bin/lib/types-0.1.1.jar"
}

usage() {
	cat >&2 <<'USAGE'
usage: build.sh <backend-id>   build one backend, print its classpath on stdout
       build.sh --all          build all backends, print "<id> <classpath>" lines

backends: native, skia-canvas, skija-proto, native-baseline, native-candidate
environment:
  ORACLE_BASELINE   git ref or SWT directory for native-baseline (default master)
  ORACLE_CANDIDATE  git ref or SWT directory for native-candidate (required)
  ORACLE_CACHE_DIR  override cache location (default ~/.cache/swt-visual-oracle)
  ORACLE_REFRESH=1  re-fetch the prototype-skija fork before building
USAGE
	exit 2
}

main() {
	local target="${1:-}"
	[ -n "$target" ] || usage
	case "$target" in
		native|skia-canvas|skija-proto|native-baseline|native-candidate)
			local cp
			case "$target" in
				native-baseline) cp="$(build_native_from native-baseline "${ORACLE_BASELINE:-master}")" ;;
				native-candidate)
					[ -n "${ORACLE_CANDIDATE:-}" ] || die "native-candidate needs ORACLE_CANDIDATE (git ref or SWT directory)"
					cp="$(build_native_from native-candidate "$ORACLE_CANDIDATE")" ;;
				native) cp="$(build_native)" ;;
				skia-canvas) cp="$(build_skia_canvas)" ;;
				skija-proto) cp="$(build_skija_proto)" ;;
			esac
			printf '%s\n' "$cp"
			;;
		--all)
			local failed=0 id cp fn
			for id in native skia-canvas skija-proto; do
				case "$id" in
					native) fn=build_native ;;
					skia-canvas) fn=build_skia_canvas ;;
					skija-proto) fn=build_skija_proto ;;
				esac
				local log
				log="$CACHE_ROOT/all-last-error.log"
				if ! cp="$($fn 2>"$log")"; then
					info "--all: backend '$id' is not available here, skipping:"
					sed 's/^/    /' "$log" >&2 || true
					failed=1
				else
					printf '%s %s\n' "$id" "$cp"
				fi
			done
			exit "$failed"
			;;
		-h|--help) usage ;;
		*) usage ;;
	esac
}

mkdir -p "$CACHE_ROOT" "$MAVEN_DIR" "$BUILD_DIR"
main "$@"
