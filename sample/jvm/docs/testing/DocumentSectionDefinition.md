[Ktor sample app](../README.md) / [Testing](README.md)

# Document section definition

The headings this project declares for itself, so roles and groups can write under them

`DocumentSections.kt` declares the headings that appear in the generated pages besides
katachi's own ones ("Allowed contents", "Forbidden contents"), and the property each
role or group writes them with. It is a vocabulary the role files share and no role
owns, so it is a file of its own next to the entry.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/DocumentSections.kt` |  |

## Examples

- `DocumentSections.kt` ... The two headings and their properties

## Forbidden contents

- A group or a role. Those go in `groups/` and `roles/`
