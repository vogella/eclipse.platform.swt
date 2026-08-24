# Visual Oracle result JSON schema, version 1

The result document is the stable contract between the harness and every consumer
(agents, reports, CI). It is written by `org.eclipse.swt.visualoracle.result.RunResult`,
validated by `ResultSchemaValidator`, and enforced in both directions by the selftest.

Serialisation details: UTF-8, pretty-printed with two-space indent, object member
order follows the order documented here (the writer is deterministic).
Paths in `image` fields are POSIX-style and relative to the directory containing
the result document.

Validation is strict: unknown properties are errors, wrong types are errors,
conditional fields are enforced. Consumers may rely on every field documented here.

## Top-level object

| Field | Type | Rules |
|---|---|---|
| `schemaVersion` | integer | required, must be `1` for this schema |
| `generator` | string | non-empty, identifies producing tool and task |
| `environment` | object | required, see below |
| `captures` | array | of capture objects; may be empty |
| `comparisons` | array | of comparison objects; may be empty |

## `environment`

The rendering environment this process was pinned to.

| Field | Type | Rules |
|---|---|---|
| `zoomPercent` | integer | >= 1; 100 means unscaled |
| `theme` | string | theme id, empty string = platform default |
| `direction` | string enum | `"LTR"` or `"RTL"` |
| `fontFamily` | string | empty string = platform system font |
| `fontSize` | integer | never 0; positive points, negative pixels |

## Capture objects (`captures[]`)

One capture attempt of one specimen on one backend.

| Field | Type | Rules |
|---|---|---|
| `specimen` | string | non-empty specimen id |
| `backend` | string | non-empty backend id |
| `status` | string enum | `"CAPTURED"`, `"UNSUPPORTED"`, `"FAILED"` |
| `width` | integer | present iff `status` is `"CAPTURED"`, >= 1 |
| `height` | integer | present iff `status` is `"CAPTURED"`, >= 1 |
| `image` | string | present iff `status` is `"CAPTURED"`; PNG path relative to this document |
| `message` | string | only when status is not `"CAPTURED"`; non-empty when present |

Semantics a consumer may rely on:

* `UNSUPPORTED` means the backend cannot render this specimen. This is coverage data,
  not a defect.
* `FAILED` means the attempt crashed or was rejected; `message` says why.
  A failed capture never produces an image reference.

## Comparison objects (`comparisons[]`)

The diff of two captures of the same specimen from different backends.

| Field | Type | Rules |
|---|---|---|
| `specimen` | string | non-empty |
| `referenceBackend` | string | non-empty backend id of the oracle side |
| `candidateBackend` | string | non-empty backend id of the side under test |
| `referenceImage` | string | PNG path relative to this document |
| `candidateImage` | string | PNG path relative to this document |
| `verdict` | string enum | `"EQUAL"`, `"WITHIN_TOLERANCE"`, `"DIFFERENT"` |
| `changedPixels` | integer | >= 0 |
| `changedFraction` | number | 0.0 .. 1.0 |
| `maxChannelDelta` | integer | 0 .. 255 |
| `probableDefectClass` | string enum | `"NONE"`, `"UNKNOWN"`, `"SHIFTED"`, `"MISSING_ELEMENT"`, `"WRONG_COLOR"`, `"WRONG_GLYPH"` |
| `clusters` | array | of cluster objects, possibly empty |

Verdict semantics:

* `EQUAL`: images are bit-identical; `changedPixels` and `maxChannelDelta` are 0.
* `WITHIN_TOLERANCE`: differences exist but none exceeds the requested tolerance;
  `changedPixels` counts only pixels beyond tolerance.
* `DIFFERENT`: at least one pixel exceeds tolerance.

A size mismatch between the two images yields `DIFFERENT` with
`changedFraction` 1.0 over the larger area.

## Cluster objects (`clusters[]`)

Connected regions of change in image pixel space, origin top-left.

| Field | Type | Rules |
|---|---|---|
| `x` | integer | >= 0 |
| `y` | integer | >= 0 |
| `width` | integer | >= 1 |
| `height` | integer | >= 1 |
| `changedPixels` | integer | >= 0, pixels of this cluster |

Until T06 delivers real cluster detection, the harness writes at most one
bounding-box cluster per comparison; consumers must treat cluster count and shape
as provisional until schema version 2.

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

## Versioning

Any breaking change (field removal, type change, enum value removal, semantic change)
bumps `schemaVersion` and adds a section here describing the difference.
Additive changes that old consumers safely ignore may arrive within a version but
must be listed above. The selftest validates against exactly one declared version:
`ResultSchemaValidator.SUPPORTED_SCHEMA_VERSION`.
