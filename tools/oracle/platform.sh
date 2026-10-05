#!/usr/bin/env bash
# Platform detection shared by the visual oracle scripts; source it, do not run it.
#
# Sets, for the machine the scripts run on:
#   ORACLE_WS         SWT windowing system: gtk | cocoa | win32
#   ORACLE_ARCH       x86_64 | aarch64
#   ORACLE_FRAGMENT   binary fragment suffix, for example gtk.linux.x86_64
#   ORACLE_CP_SEP     classpath separator: ':' or ';' on Windows
# and defines:
#   oracle_binaries_dir <repo-root>        the fragment directory below binaries/
#   oracle_native_glob                     glob naming the main native library in that directory
#   oracle_native_libs_glob                glob matching every native library there
#   oracle_native_path <path>              the path in the form javac and java take (Windows form on Git Bash)
#   oracle_timeout_cmd                     prints the name of a GNU timeout(1), fails if there is none
#   oracle_link_dir <target> <link>        makes <link> show <target> (a copy on Windows, where ln -s copies)
#   oracle_run_headless <cmd...>           runs a command with the display setup the platform needs
#   oracle_require_linux_gtk <what>        dies unless on Linux/GTK (Skia and Skija backends)
#
# The Java side mirrors this in harness/src/.../impl/PlatformSupport.java; keep
# fragment names and the headless wrapper in step with it.

oracle_platform_die() { printf '%s: %s\n' "$(basename "$0")" "$*" >&2; exit 1; }

case "$(uname -s)" in
	Linux)  ORACLE_WS=gtk;   ORACLE_OS=linux ;;
	Darwin) ORACLE_WS=cocoa; ORACLE_OS=macosx ;;
	MINGW*|MSYS*|CYGWIN*) ORACLE_WS=win32; ORACLE_OS=win32 ;;
	*) oracle_platform_die "unsupported operating system $(uname -s)" ;;
esac

case "$(uname -m)" in
	x86_64|amd64) ORACLE_ARCH=x86_64 ;;
	aarch64|arm64) ORACLE_ARCH=aarch64 ;;
	*) ORACLE_ARCH="$(uname -m)" ;;
esac

ORACLE_FRAGMENT="$ORACLE_WS.$ORACLE_OS.$ORACLE_ARCH"

ORACLE_CP_SEP=':'
[ "$ORACLE_WS" = win32 ] && ORACLE_CP_SEP=';'

# javac and java are native Windows programs under Git Bash: they take C:/... paths and ';' in -cp, and
# the contents of an @argfile are never converted. cygpath -m (forward slashes) keeps argfile quoting simple.
if [ "$ORACLE_WS" = win32 ]; then
	command -v cygpath >/dev/null 2>&1 || oracle_platform_die "cygpath not found, run the oracle scripts from Git Bash or MSYS2"
	oracle_native_path() { cygpath -m "$1"; }
	# lets the Java side run build.sh through the same bash
	ORACLE_BASH="$(oracle_native_path "$BASH")"
	export ORACLE_BASH
else
	oracle_native_path() { printf '%s' "$1"; }
fi

# JVM options every oracle JVM gets on this platform. Children share the one interactive desktop on
# Windows, where overlapping windows and focus changes would reach the captures, so they run one at a time.
ORACLE_PLATFORM_JAVA_OPTS=()
[ "$ORACLE_WS" = win32 ] && ORACLE_PLATFORM_JAVA_OPTS=(-Doracle.children.parallelism=1)

# On Windows an unrelated timeout.exe (the DOS pause command) can precede the GNU one on PATH.
oracle_timeout_cmd() {
	local candidate
	for candidate in timeout /usr/bin/timeout; do
		if command -v "$candidate" >/dev/null 2>&1 && "$candidate" --version 2>/dev/null | grep -q 'GNU coreutils'; then
			printf '%s' "$candidate"
			return 0
		fi
	done
	return 1
}

oracle_link_dir() {
	local target="$1" link="$2"
	if [ "$ORACLE_WS" = win32 ]; then
		# ln -s makes a copy on Git Bash; keep an identical one instead of replacing natives a JVM may have loaded
		diff -rq "$target" "$link" >/dev/null 2>&1 && return 0
		rm -rf "$link"
		cp -r "$target" "$link"
	else
		ln -sfn "$target" "$link"
	fi
}

# macOS has no GNU sha256sum (its BSD one lacks -c); shasum is compatible.
if ! sha256sum --version 2>&1 | grep -q "GNU coreutils"; then
	sha256sum() { shasum -a 256 "$@"; }
fi

oracle_binaries_dir() { printf '%s/binaries/org.eclipse.swt.%s' "$1" "$ORACLE_FRAGMENT"; }

oracle_native_glob() {
	case "$ORACLE_WS" in
		gtk) printf 'libswt-pi3-gtk-*.so' ;;
		cocoa) printf 'libswt-pi-cocoa-*.jnilib' ;;
		win32) printf 'swt-win32-*.dll' ;;
	esac
}

# Glob matching every native library of the fragment, for fingerprints.
oracle_native_libs_glob() {
	case "$ORACLE_WS" in
		gtk) printf 'libswt-*.so' ;;
		cocoa) printf 'libswt-*.jnilib' ;;
		win32) printf '*.dll' ;;
	esac
}

oracle_require_linux_gtk() {
	local what="${1:-this backend}"
	if [ "$(uname -s)-$(uname -m)" != "Linux-x86_64" ]; then
		oracle_platform_die "$what needs Linux on x86_64 (prebuilt GTK binaries), found $(uname -s)-$(uname -m)"
	fi
}

# Runs "$@" under the headless display the platform needs. On Linux that is
# Xvfb with the Wayland variables unset and GDK pinned to X11; macOS and
# Windows draw on their own session, so nothing wraps the command there.
# ORACLE_XVFB_SCREEN overrides the Xvfb geometry (default 1600x1200x24).
oracle_run_headless() {
	case "$ORACLE_WS" in
		gtk)
			command -v xvfb-run >/dev/null 2>&1 \
				|| oracle_platform_die "xvfb-run not found, install xvfb (graphical verbs need a headless X server)"
			exec env -u WAYLAND_DISPLAY -u XDG_SESSION_TYPE GDK_BACKEND=x11 LIBGL_ALWAYS_SOFTWARE=1 \
				xvfb-run -a -s "-screen 0 ${ORACLE_XVFB_SCREEN:-1600x1200x24}" "$@"
			;;
		*)
			# TODO(cocoa): decide whether children need an isolated session or a fixed display.
			# Windows: children use the interactive desktop; CI runners have one.
			exec "$@"
			;;
	esac
}
