[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Definition sections

DocumentSections.kt, the section headings every group and role writes through

`DocumentSections.kt` gathers just the section definitions (`allowedContents`,
`forbiddenContents` and the like) that every group and role uses with `by`. It is the
one file beside `ProjectArchitecture.kt` that every group and role reads, and it is named
exactly for the same reason: anything else that slips in next to the definition becomes
an `[UnexpectedFile]`. The other vocabulary a few of them share, the `owner` key, belongs
to the processor that reads it and sits in `processors/Owner.kt` (ProcessorMetadata).

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/kmp/DocumentSections.kt` |  |

## Examples

- `DocumentSections.kt` ... The section headings shared by every group and role
