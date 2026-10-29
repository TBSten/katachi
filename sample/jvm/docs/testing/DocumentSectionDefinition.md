[Ktor sample app](../README.md) / [Testing](README.md)

# Document section definition

The headings this project declares for itself, so roles and groups can write under them

`DocumentSections.kt` declares two headings this project adds to the generated pages
besides katachi's own ones, "Allowed contents" and "Forbidden contents", and the property
each role or group writes them with. They are a vocabulary the role files share and no
role owns, so they are a file of their own next to the entry. A heading only one group
writes stays in that group's file instead: "Test policy" is declared in
`api/ApiGroup.kt`.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/DocumentSections.kt` |  |

## Examples

- `DocumentSections.kt` ... The two headings and their properties

## Forbidden contents

- A group or a role. Those go in `<Name>Group.kt` and `<Name>Role.kt`
