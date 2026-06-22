# Design Proposal: Fix O(N) `TreeItem.getItemCount()` / `getItem(int)` on GTK (issue #882)

Status: proposal / for discussion (no code committed)
Scope: `bundles/org.eclipse.swt` GTK port only (`Tree.java`, `TreeItem.java`)
Related: eclipse-platform/eclipse.platform.swt#649 (quadratic lazy reveal on Linux),
eclipse-platform/eclipse.platform.ui#810 (JFace item-limit mitigation, already shipped)

## 1. Problem

On the GTK port, the child-count and indexed-child accessors delegate directly to
linear-time `GtkTreeStore` operations:

| SWT API | GTK call | Location | Complexity |
| --- | --- | --- | --- |
| `TreeItem.getItemCount()` | `gtk_tree_model_iter_n_children` | `TreeItem.java:755` | O(N) |
| `Tree.getItemCount()` (roots) | `gtk_tree_model_iter_n_children` | (same call, parent `0`) | O(N) |
| `TreeItem.getItem(int)` | `gtk_tree_model_iter_nth_child` | `TreeItem.java:782` | O(index) |
| `TreeItem.getItems()` | `iter_children` + `iter_next` loop | `Tree.getItems(handle)` ~`Tree.java:606` | O(N) per call |

`GtkTreeStore` walks the sibling linked list for each of these; it keeps no cached
count. The SWT team already documented the cost — see the comment at `Tree.java:992`:
*"Even a single call to `gtk_tree_model_iter_n_children` already reduces performance 3x."*

JFace's `AbstractTreeViewer`/`TreeViewer` call these accessors **once per level (and
sometimes once per child)** while expanding/revealing. Each call is O(N); doing it
O(N) times yields **O(N²)** navigation. Measured symptom in #649: ~400 s to reveal a
100 000-item lazy tree on Linux, versus linear behaviour on Windows/macOS (whose
native widgets expose O(1) counts).

## 2. Goal & non-goals

Goal: make `getItemCount()` and repeated `getItems()`/`getItem(int)` calls O(1)
amortised **during read-heavy phases** (the JFace reveal/expand loop), with no change
to observable semantics and no regression to bulk-insert performance.

Non-goals:
- Changing JFace. The shipped item-limit (#810) already caps per-parent rendering and
  is the pragmatic IDE-level mitigation; this proposal addresses the SWT root cause so
  trees are fast even without a cap.
- Touching the Win32/Cocoa ports (already O(1) natively).
- Making a single `getItem(N-1)` call after a mutation O(1) — `nth_child` after a
  structural change must still consult GTK at least once per generation.

## 3. Why not incremental per-parent counters

The obvious idea — keep an `int childCount` on each `TreeItem`/`Tree` and `++/--` it on
insert/remove — is fragile here:

- `createItem` has the `parentIter` (`Tree.java:989`) but **`destroyItem` does not**
  (`Tree.java:1283`); recovering the parent there costs an O(depth) `gtk_tree_model_get_path`.
- `remove(parentIter,…)` (`Tree.java:2909`), the VIRTUAL branch of
  `setItemCount` (`Tree.java:3454`), `removeAll` → `gtk_tree_store_clear`
  (`Tree.java:2960`), `releaseChildren`, and the **model rebuild on column add/remove**
  (`Tree.java:734`, old store discarded) all mutate structure through different paths.
- DnD reorder, sort, and `clearAll` add more edges.

Every missed edge silently corrupts the count → wrong viewer state. Too many hooks,
too much blast radius on the most-used widget, and **not locally testable** (no GTK
display + natives in CI-less dev here).

## 4. Recommended design: generation-counter + lazy cache

Keep the cache **read-derived and self-invalidating** instead of incrementally
maintained.

### 4.1 Core

On `Tree`:

```java
/** Bumped on every structural change to the model. Invalidates all cached child
 *  counts/arrays in O(1) without touching each item. */
int structureModCount;
```

On `Tree` (roots) and `TreeItem` (subtrees):

```java
private int cachedChildCount = -1;     // -1 = unknown
private int cachedChildCountStamp = -1; // structureModCount when cached
```

`getItemCount()` becomes:

```java
int n = parent.structureModCount;
if (cachedChildCountStamp == n) return cachedChildCount;
cachedChildCount = GTK.gtk_tree_model_iter_n_children(parent.modelHandle, handle);
cachedChildCountStamp = n;
return cachedChildCount;
```

(`Tree.getItemCount()` is the same with `handle == 0` and reads its own field.)

### 4.2 Invalidation = one increment per mutation

Replace the scattered per-parent bookkeeping with a single `structureModCount++` at
each structural mutation site. Candidate sites (all already centralised):

- `createItem` (`Tree.java:989`)
- `destroyItem(TreeItem)` (`Tree.java:1283`)
- `remove(parentIter,start,end)` (`Tree.java:2909`)
- `removeAll()` (`Tree.java:2953`)
- `setItemCount(parentIter,count)` (`Tree.java:3444`) — covers VIRTUAL insert too
- `releaseChildren` (`Tree.java:2874`)
- model rebuild in `setColumnCount`/column add-remove (`Tree.java:~734`)
- DnD drop reorder, sort reorder, `clearAll` if they change structure

A drop/missed site does **not** corrupt data — it only fails to invalidate, so a stale
count could be returned. To make even that safe, see §4.4.

### 4.3 Optional: cache the children array too (fixes the #649 hot path)

The dominant cost in #649 is JFace calling `getItems()` repeatedly, each O(N). Add a
generation-stamped array cache mirroring the count cache:

```java
private TreeItem[] cachedItems;
private int cachedItemsStamp = -1;
```

`getItems()` returns a **defensive copy** of the cached array when the stamp matches
(API contract: callers may mutate the returned array — see `TreeItem.getItems()`
Javadoc, `TreeItem.java:789`). This turns O(N) reveals that re-fetch siblings from
O(N²) into O(N). `getItem(int)` can serve from the same cache when present, falling
back to `nth_child` otherwise. Memory cost: one `TreeItem[]` per *expanded* parent for
one generation; dropped on the next mutation.

> Recommendation: land §4.1–4.2 first (small, addresses #882 literally), measure, then
> add §4.3 if the reveal profile still shows `getItems()` dominating.

### 4.4 Defensive correctness option

To guarantee correctness even if an invalidation site is missed, bump
`structureModCount` from the **already-existing** `modelChanged = true` assignments
(they sit at every mutation: `Tree.java:1023`, `1288`, `3479`, …). Funnelling both
through one `private void structureChanged()` helper means "I changed the model" and "I
invalidated caches" can never drift apart. This is the safest variant and the one I'd
ship.

## 5. Correctness / edge cases to verify on Linux CI

- VIRTUAL trees: `setItemCount` insert/clear, `setData`-driven materialisation, and the
  `*virtual*` placeholder path (`TreeItem.java:813`).
- Column add/remove rebuilds the `GtkTreeStore` (`Tree.java:~734`) — caches on surviving
  `TreeItem`s must be invalidated (covered by §4.4 since `modelChanged` is set there).
- `getItem(int)` range check still throws `ERROR_INVALID_RANGE` for out-of-range
  (`TreeItem.java:782`) — cache must not mask the error.
- DnD reorder within the same tree and column sort reorder (counts unchanged, but array
  order changes → array cache must invalidate).
- `EmptinessChanged` events (`Tree.java:1029`, `1293`) must still fire.

## 6. Testing

- Unit: extend `tests/org.eclipse.swt.tests` Tree tests — assert `getItemCount()`
  correctness across create/insert-at-index/remove/removeAll/setItemCount, VIRTUAL, and
  after column add/remove.
- Performance (manual / opt-in): build a 100 000-child node, time a full reveal; expect
  the O(N²)→O(N) collapse from #649. Cannot run in this dev environment (no GTK
  display/natives) — relies on the Linux PR workflow.

## 7. Risk & effort

- Effort: §4.1–4.2 ≈ small (≈30 lines + one helper). §4.3 ≈ medium.
- Risk: low for §4.4 variant (self-invalidating, no incremental drift); the only failure
  mode is a *stale-but-not-corrupt* count, eliminated by funnelling through
  `modelChanged`.
- Blast radius: core widget, but reads fall back to live GTK queries whenever the stamp
  doesn't match, so behaviour is identical to today in the worst case.

## 8. Recommendation

Implement §4.1 + §4.2 wired through the §4.4 `structureChanged()`/`modelChanged` funnel
to fix #882 literally and safely. Defer §4.3 (array cache) to a follow-up gated on a
reveal profile, since that's what actually closes the #649 user-visible freeze and
carries more memory/semantics surface.
