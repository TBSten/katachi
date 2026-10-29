[katachi-sample-android](../README.md) / [Testing](README.md)

# Definition sections

DocumentSections.kt, the section headings every group and role writes through

`DocumentSections.kt` gathers just the section definitions (`allowedContents`,
`forbiddenContents` and the like) that every group and role uses with `by`. It is the
one file beside `ProjectArchitecture.kt` that every group and role reads, and it is named
exactly for the same reason: anything else that slips in next to the definition becomes
an `[UnexpectedFile]`. A helper that only the roles of one group read sits in that
group's file instead (`featureSources` and `FeatureModule` in `feature/FeatureGroup.kt`,
`DataDomain` in `data/DataGroup.kt`).

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/sample/DocumentSections.kt` |  |

## Examples

- `DocumentSections.kt` ... The section headings shared by every group and role
