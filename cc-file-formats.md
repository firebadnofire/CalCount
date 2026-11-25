# CalCount file formats and QR transfer

**CalCount Nutritional Labels (CCNL)** stores one food or liquid label in a
`.ccnl` file. **CalCount Recipe (CCR)** stores a recipe and the labels for
its ingredients in a `.ccr` file. Both formats have version 1. Exported
files are self-contained and can be transferred offline.

## CalCount Nutritional Labels

A CCNL file is uncompressed UTF-8 JSON. The root object requires
`format: "ccnl"`, `schema_version: 1`, and `food`.

| Field inside `food` | Meaning |
| --- | --- |
| `id` | Stable UUID, in lowercase hyphenated form |
| `name`, `servingDescription` | Required nonblank text |
| `kind` | `FOOD` or `LIQUID` |
| `servingWeightGrams` | Positive number required for `FOOD` |
| `servingVolumeMilliliters` | Positive number required for `LIQUID` |
| `nutritionPerServing` | Object containing values per declared serving |
| `servingsPerContainer` | Optional positive number |
| `createdAtEpochMillis` | Optional nonnegative integer in Unix milliseconds |

The required nutrients are `calories` (kilocalories), `fatGrams`,
`carbsGrams`, and `proteinGrams`. Optional nutrient fields cover fat
subtypes, sugars, fiber, cholesterol, electrolytes, vitamins, and minerals.
The exact keys and units are in the
[CCNL JSON Schema](docs/schemas/ccnl.schema.json). All nutrient values are
finite, nonnegative numbers. An absent field or JSON `null` means unknown.
A numeric zero means a reported zero. The unused weight or volume field may
be omitted or null. A liquid label can also record its measured serving
weight for recipe ingredients.

The optional root `catalog` object includes `catalogId`, positive integer
`revision`, `publisher`, `sourceName`, and optional `sourceUrl`,
`license`, `regionCode`, `notes`, and `verifiedAtEpochMillis`. These are
provenance fields. URLs are imported as text.

CalCount reads legacy bare food objects and the former `type: "food"`
envelope with absent `version` or numeric `version: 1`. A legacy label
without `kind` is interpreted as `FOOD`. Valid legacy UUIDs are normalized
to lowercase. Other legacy IDs receive a new UUID. A present `format`
identifies a versioned payload and requires a supported version. A file
extension or MIME type cannot establish validity.

See the [working oats label](docs/examples/oats.ccnl). It has 150 kcal per
40 g serving and explicitly reports zero sodium. It leaves potassium unknown.

## CalCount Recipe

CCR v1 is a Zstandard-compressed USTAR byte stream. The TAR root must contain
`manifest.json` and exactly one regular `labels/<uuid>.ccnl` file per
referenced label UUID. An empty `labels/` directory entry is optional.
TAR links, path prefixes, extension headers, and unrelated entries are
rejected. Files are read in memory and are never extracted to disk.

The manifest is UTF-8 JSON with `format: "ccr"`, `schema_version: 1`,
the recipe's stable UUID `id`, its `name`, and a positive numeric
`servings` yield. It may include `description` and `notes`. The
ordered `ingredients` array contains `label_id`, positive numeric
`quantity`, `unit`, and optional `notes`. Repeated references are allowed
and each contributes separately. The `labels` array contains an `id` and
a lowercase hexadecimal `sha256` of the exact bytes for each bundled
label. Each listed UUID must match its label and be used by an ingredient.
See the [CCR manifest schema](docs/schemas/ccr-manifest.schema.json)
and [working oats manifest](docs/examples/manifest.json).

### Quantity and nutrition

Supported units are `servings`, `g`, `oz`, `mL`, `L`, and `fl_oz`.
An ounce is 28.349523125 g and a US fluid ounce is 29.5735295625 mL.
Divide a measured amount by the label's serving measurement after
converting to grams or milliliters. A weight unit requires a measured
serving weight, including for liquid ingredients. Older CalCount builds
that only accept liquids by volume or servings may reject CCR files that
use liquid ingredients by weight. A volume unit requires a liquid label's
volume. Convert a `servings` quantity directly to a serving factor. Multiply each nutrient
by that factor, sum contributions, then divide by the recipe yield to get
per-serving values. Nested recipes are outside v1.

Nutrient completeness applies independently to each nutrient. If all
ingredient rows report the nutrient, the sum is complete. If only some
report it, the sum is an incomplete subtotal. If none report it, it is
unknown. A zero counts as reported, and the same completeness applies to
the whole recipe and its scaled servings. If one ingredient provides 30 mg
sodium and another leaves sodium unknown, a two-serving recipe has an
incomplete 30 mg subtotal and an incomplete 15 mg per serving. CCR stores
the labels needed to derive completeness.

### Identity and import review

CalCount validates the complete bundle before staging it. Matching UUIDs
with identical content are reused, ignoring timestamps for identity
comparison. Conflicting labels are reviewed in first-use order, followed
by the recipe. **Use Local** retains the local definition and links
incoming references to it. **Use Imported** replaces the definition under
its UUID after explicit confirmation. **Keep Both** assigns the imported
definition a fresh UUID and remaps references. Review shows affected
recipes and nutrition changes. Choices that invalidate a recipe are
rejected. Final confirmation saves the recipe and label changes together.

Imported recipes reference the local food library. Editing a food
recalculates dependent recipes when displayed or logged. Referenced foods
cannot be deleted until removed from those recipes. Existing consumption
records retain their saved nutrition and completeness.

### Limits

| Limit | Value |
| --- | ---: |
| CCNL and CCR manifest JSON, each | 64 KiB |
| JSON nesting | 24 levels |
| Compressed CCR | 2 MiB |
| Expanded USTAR | 8 MiB |
| Zstandard maximum window | 8 MiB |
| Distinct labels | 256 |
| Ingredient rows | 512 |

CalCount rejects malformed UTF-8, duplicate JSON keys, duplicate TAR paths,
unsupported versions, archive truncation, unmatched labels, and digest
mismatches. Member sizes, supported entry types, and the TAR terminator
are checked. The JSON Schemas describe the document shapes. Runtime
validation also checks byte limits, reference integrity, digest matches,
finite arithmetic, and compatible measurement units.

## QR carousel v1

The sender serializes the same file bytes used for file export and divides
them into chunks of at most 384 bytes. There are at most 128 frames
(48 KiB). Each packet is ASCII with colon separators. The receiver limits
packet text to 656 characters.

```text
CCQR1:<session-uuid>:<ccnl|ccr>:<index>:<count>:<sha256>:<crc32>:<data>
```

- `session-uuid` is a fresh lowercase hyphenated UUID for each broadcast.
- `index` is zero-based and `count` is between 1 and 128, both in decimal.
- `sha256` is the lowercase 64-character SHA-256 digest of the complete file bytes.
- `crc32` is the eight-character lowercase CRC-32 (IEEE), with leading zeros.
  Its input is the colon-joined ASCII packet fields in order with the CRC field
  removed, including the final `data` field.
- `data` is unpadded Base64url. Concatenate decoded chunks in index order.
  Chunk lengths may differ. Receivers apply the 384-byte per-chunk maximum.
- QR rendering uses error correction M and a four-module quiet zone. A
  384-byte chunk expands to 512 Base64url characters before the header.

The default broadcast is 1,000 ms per frame. Manually selectable 750 ms and
500 ms modes can be less reliable. The preview shows the duration of one
cycle as `frame_count × frame_duration_ms`. A 128-frame default broadcast
takes 128 seconds per cycle. Reception can take longer. The carousel loops
until the sender presses **End**.

The receiver collects a single session from any starting frame, accepts
out-of-order packets, and ignores duplicates and unrelated sessions. It
keeps unique frames across slideshow cycles. A reset clears the collection
so the receiver can switch senders. Once all frames are present, it verifies
the full payload digest, checks the declared payload type against the
decoded content, and opens the same import review as a file. Timing has no
effect on packet interpretation. Existing CalCount single-QR JSON labels
remain supported. CRC and SHA-256 detect corruption. Neither authenticates
the sender. The protocol has no acknowledgments or networking.

## Third-party interoperability

1. Generate a valid CCNL object for each distinct food and save the exact
   UTF-8 bytes. Keep UUIDs stable across edits.
2. For CCR, hash each label as written. Generate the manifest, add the
   indexed labels to a USTAR archive at their UUID paths, and compress it
   using standard Zstandard. Use regular file entries and no TAR extensions.
3. For QR, generate one session UUID, split those same file bytes, and
   calculate the payload SHA-256 and each packet CRC-32. Encode and broadcast
   the resulting packet strings.
4. On receipt, validate sizes before allocation or decompression. Verify
   packet and label integrity and apply identity resolution before writing
   any records.

For the example, store `oats.ccnl` as
`labels/10000000-0000-4000-8000-000000000001.ccnl` without changing its byte
content, include the example `manifest.json` at the root, then compress
the USTAR. It yields two servings at 150 kcal each. A byte-level edit to
the label requires recomputing the manifest digest.

Android uses a generic binary MIME type when creating documents through
SAF so providers preserve the custom extensions. Share intents use
`application/json` for CCNL and `application/octet-stream` for CCR. Custom
CalCount MIME types are accepted for incoming shares, and recognition
depends on the bytes.

## Local logs, persistence, and Android runtime

Saved consumption entries use `source_type: "food"` or
`source_type: "recipe"` with the source UUID and display name. Legacy logs
without this field load as food logs. A recipe log stores its nutrition
snapshot and the per-nutrient completeness values `COMPLETE`,
`INCOMPLETE`, and `UNKNOWN`. Today, History, and Day Detail use these
saved values. Changes or deletion of a source do not change historical
records. Older snapshots without completeness retain their existing display.

CalCount reads the legacy SharedPreferences JSON when migrating. New
writes use an app-private current JSON document and a previous valid
copy. It verifies the staged bytes before atomically publishing the file.
Startup ignores uncommitted staging files and uses the previous copy if the
current state is unreadable. If neither is readable, saving is disabled to
protect the stored data.

CalCount supports Android API 26 and newer. CCR uses Commons Compress
1.28.0 and zstd-jni 1.5.7-12. The Android AAR declares API 16 as its
minimum and includes ARM64, ARMv7, x86, and x86-64 binaries. That version
works with CalCount’s compile SDK 36. Gson 2.15.0 checks strict JSON
syntax before the app reads the field values. The APK includes the
[third-party licenses and notices](app/src/main/assets/third-party/).

Android instrumentation exercises real CCR compression and decompression
on ARM64 emulators running API 26 and API 35 with 16 KiB pages. Debug
and release APKs must also pass 16 KiB packaging and native ELF alignment
checks. The bundled Android QR decoder is tested with rendered carousel
frames. Physical camera readability depends on both devices and lighting. See [Android page-size compatibility guidance](https://developer.android.com/guide/practices/page-sizes).
