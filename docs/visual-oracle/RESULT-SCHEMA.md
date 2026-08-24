# Visual Oracle result JSON schema, version 1

The result document is the stable contract between the harness and every consumer
(agents, reports, CI).
It is written by `org.eclipse.swt.visualoracle.result.RunResult`, read back by
`RunResult.fromJson` / `ResultJson`, validated by `ResultSchemaValidator`, and
exercised in both directions by the selftest.

Serialisation details: UTF-8, pretty-printed with two-space indent, object member
order follows the order documented here (the writer is deterministic).
Paths in `image` fields are POSIX-style and relative to the directory containing
the result document.

Validation is strict: unknown properties are errors, wrong types are errors,
conditional fields are enforced.
Every validator error names the offending field's location (for example
`captures[2].width`) and what is wrong with it.

Field status vocabulary used below:

* **required**: must be present; absence is a schema violation.
* **conditional**: presence depends on another field's value; present when the
  condition holds it is required, otherwise it is forbidden.
* **optional**: may be absent; absence carries no hidden meaning beyond the one
  documented.

## Top-level object

| Field | Type | Status | Rules |
|---|---|---|---|
| `schemaVersion` | integer | required | must be `1` for this schema |
| `generator` | string | required | non-empty, identifies producing tool and task |
| `environment` | object | required | see below |
| `captures` | array | required | of capture objects; may be empty |
| `comparisons` | array | required | of comparison objects; may be empty |

An empty run (no specimen attempted) is a valid document:
`captures` and `comparisons` are present but empty arrays.

## `environment`

The rendering environment this process was pinned to.
All fields are required; an environment is never partially recorded.

| Field | Type | Status | Rules |
|---|---|---|---|
| `zoomPercent` | integer | required | >= 1; 100 means unscaled |
| `theme` | string | required | theme id; empty string = platform default |
| `direction` | string enum | required | `"LTR"` or `"RTL"` |
| `fontFamily` | string | required | empty string = platform system font unchanged |
| `fontSize` | integer | required | never 0; positive points, negative pixels |

Empty-string semantics are data, not missing values:
a consumer must treat `"theme": ""` exactly like the producing process treated
the platform default.

## Capture objects (`captures[]`)

One capture attempt of one specimen on one backend.

| Field | Type | Status | Rules |
|---|---|---|---|
| `specimen` | string | required | non-empty specimen id |
| `backend` | string | required | non-empty backend id |
| `status` | string enum | required | `"CAPTURED"`, `"UNSUPPORTED"`, `"FAILED"` |
| `width` | integer | conditional | required iff `status` is `"CAPTURED"`, >= 1, forbidden otherwise |
| `height` | integer | conditional | required iff `status` is `"CAPTURED"`, >= 1, forbidden otherwise |
| `image` | string | conditional | required iff `status` is `"CAPTURED"`; PNG path relative to this document; forbidden otherwise |
| `message` | string | optional | only meaningful when `status` is not `"CAPTURED"`; forbidden for `"CAPTURED"`; non-empty when present |

Semantics a consumer may rely on:

* `UNSUPPORTED` means the backend cannot render this specimen.
  This is coverage data, not a defect, and must never be counted as one.
* `FAILED` means the attempt crashed or was rejected; `message` says why when
  present.
  A failed capture never produces an image reference.
* A capture entry never changes its meaning across documents from the same
  `schemaVersion`: re-running the same specimen under the same environment
  yields the same entry shape.

## Comparison objects (`comparisons[]`)

The diff of two captures of the same specimen from different backends.
All fields are required.

| Field | Type | Status | Rules |
|---|---|---|---|
| `specimen` | string | required | non-empty |
| `referenceBackend` | string | required | non-empty backend id of the oracle side |
| `candidateBackend` | string | required | non-empty backend id of the side under test |
| `referenceImage` | string | required | PNG path relative to this document |
| `candidateImage` | string | required | PNG path relative to this document |
| `verdict` | string enum | required | `"EQUAL"`, `"WITHIN_TOLERANCE"`, `"DIFFERENT"` |
| `changedPixels` | integer | required | >= 0 |
| `changedFraction` | number | required | 0.0 .. 1.0 |
| `maxChannelDelta` | integer | required | 0 .. 255 |
| `probableDefectClass` | string enum | required | `"NONE"`, `"UNKNOWN"`, `"SHIFTED"`, `"MISSING_ELEMENT"`, `"WRONG_COLOR"`, `"WRONG_GLYPH"` |
| `clusters` | array | required | of cluster objects, possibly empty |

Verdict semantics (as implemented by the T06 diff engine):

* `EQUAL`: images are bit-identical; `changedPixels`, `changedFraction` and
  `maxChannelDelta` are 0 and `clusters` is empty.
* `WITHIN_TOLERANCE`: differences exist but stay within tolerance: every
  changed pixel is inside a cluster whose mean peak channel delta indicates
  anti-aliasing-like disagreement, and the changed fraction does not exceed
  the tolerance's `maxChangedFraction`.
  `changedPixels` counts pixels beyond the per-channel tolerance.
* `DIFFERENT`: differences exceed the tolerance, either because more than
  `maxChangedFraction` of all pixels changed or because at least one change
  cluster is structural (coherent group of strongly-changed pixels).

A size mismatch between the two images yields `DIFFERENT` with
`changedFraction` 1.0 over the larger area, `changedPixels` equal to the
larger area, and one cluster spanning the larger bounds.

Defect class semantics (as implemented by the T07 classification layer;
`NONE` for EQUAL and WITHIN_TOLERANCE, otherwise at most one claim):

* `SHIFTED`: a small translation (up to +-2 px) reproduces nearly every
  changed pixel with comparable content mass on both sides; a baseline or
  origin error rather than a drawing error.
* `MISSING_ELEMENT`: an element is present on one side only. Every cluster's
  interior colour means differ far apart while the edge maps share almost
  nothing; the constant is direction-neutral, so it does not distinguish a
  removal from an addition.
* `WRONG_COLOR`: geometry intact, colours moved: the edge maps of both
  images agree, optionally confirmed by signed-gradient correlation.
* `WRONG_GLYPH`: differences concentrate where both renderings carry dense
  small-scale structure (where text lives) and change it coherently rather
  than as scattered halo.
* `UNKNOWN`: deliberate abstention whenever the evidence does not separate
  the classes cleanly (mixed defects, scattered anti-aliasing beyond the
  envelope, size mismatches). A confidently wrong class sends someone to the
  wrong part of the code; an abstention does not.

## Cluster objects (`clusters[]`)

Connected regions of change in image pixel space, origin top-left, as found
by the T06 engine.
Grouping bridges gaps of up to two pixels (8-connected after dilation) so the
halo around one conceptual change forms one cluster; bounds and pixel counts
always describe the actually-changed pixels.

| Field | Type | Status | Rules |
|---|---|---|---|
| `x` | integer | required | >= 0 |
| `y` | integer | required | >= 0 |
| `width` | integer | required | >= 1 |
| `height` | integer | required | >= 1 |
| `changedPixels` | integer | required | >= 0, pixels of this cluster |

A cluster with `changedPixels` 0 is valid (a bounds box without counted
pixels); consumers must accept it and must not divide by it.
Clusters are ordered by significance: most changed pixels first, then
tighter bounding box, then position.
At most 64 clusters are reported; if a comparison produces more (heavy
anti-aliasing speckle), the least significant are dropped and their pixels
remain counted in `changedPixels`.

## Reading a document

`RunResult.fromJson(text)` turns a document back into the result model, the
exact inverse of writing: write then read produces a model equal to the
original, including empty runs, failed and unsupported captures, and empty or
zero-pixel cluster lists.

Reading never guesses.
In order:

1. Strict JSON parse (duplicate keys, trailing content, malformed escapes are
   errors).
2. Version gate: `schemaVersion` must be present and integral, and must equal
   the version this build reads.
   An unknown version fails before any other field is interpreted, so a
   future document cannot be misread field by field; the message names both
   the found and the supported version.
3. Full strict validation against that version's schema.
4. Mapping into the model; every enum value and conditional field was already
   proven valid.

Any failure throws `JsonException` whose message says precisely what is wrong.

## Merging documents

Comparison across processes happens by merging result documents
(`impl/ResultMerger`, decision D6), so merge output is part of this contract:

* Every input must itself be a valid document of the supported schema version;
  invalid or mixed-version input is refused, never silently passed through.
* All inputs must carry the identical `environment`; merging across
  environments is refused because the merged label would be a lie.
* The same inputs in any arrival order produce byte-identical output:
  `captures` are ordered canonically by (`specimen`, `backend`),
  `comparisons` by (`specimen`, `referenceBackend`, `candidateBackend`),
  with the serialised entry itself breaking remaining ties.
* Image paths are rebased to stay relative to the directory of the merged
  document; consumers resolve them that way.

Reports and CLIs may rely on this ordering; it is what keeps their output
stable between runs.

## Example

```json
{
  "schemaVersion": 1,
  "generator": "oracle-harness/0.1.0",
  "environment": {
    "zoomPercent": 100,
    "theme": "",
    "direction": "LTR",
    "fontFamily": "Sans",
    "fontSize": 10
  },
  "captures": [
    {
      "specimen": "button.push.default",
      "backend": "native",
      "status": "CAPTURED",
      "width": 140,
      "height": 40,
      "image": "images/button.push.default-native.png"
    },
    {
      "specimen": "button.push.default",
      "backend": "skia-canvas",
      "status": "UNSUPPORTED",
      "message": "adapter arrives with T11"
    }
  ],
  "comparisons": [
    {
      "specimen": "button.push.default",
      "referenceBackend": "native",
      "candidateBackend": "native",
      "referenceImage": "images/button.push.default-native-reference.png",
      "candidateImage": "images/button.push.default-native-candidate.png",
      "verdict": "EQUAL",
      "changedPixels": 0,
      "changedFraction": 0.0,
      "maxChannelDelta": 0,
      "probableDefectClass": "NONE",
      "clusters": []
    }
  ]
}
```

## Versioning and consumer reliance

Any breaking change (field removal, type change, enum value removal, semantic
change) bumps `schemaVersion` and adds a section here describing the
difference.
Field names, enum values and the strict no-unknown-properties rule are frozen
per schema version.

Within version 1 a consumer may rely on:

* every field documented above being present exactly as marked
  (required, conditional, optional);
* enum value sets never shrinking;
* verdict, status and defect-class semantics staying as documented here;
* the write-read round trip and the merge determinism guarantees stated above.

Additive changes that old consumers safely ignore may arrive within a version
but must be listed above.
A strictly validating consumer rejects unknown properties even so; such a
consumer keeps working by updating its validator, while a lenient reader that
ignores extras needs no change at all.
This asymmetry is deliberate: validators exist to catch producer-consumer
skew, readers exist to keep parsing.
The selftest validates against exactly one declared version:
`ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION`.
