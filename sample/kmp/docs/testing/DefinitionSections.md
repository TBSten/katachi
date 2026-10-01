[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Definition sections

DocumentSections.kt, the section headings every group and role writes through

`DocumentSections.kt` gathers just the section definitions (`allowedContents`,
`forbiddenContents` and the like) that every group and role uses with `by`. It is the
one shared helper the definition allows, named exactly for the same reason
`ProjectArchitecture.kt` is: anything else that slips in next to the definition becomes
an `[UnexpectedFile]`.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/kmp/DocumentSections.kt` |  |

## Examples

- `DocumentSections.kt` ... The section headings shared by every group and role
