[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Architecture entry

The entry point of the katachi definition, and the section definitions shared by its groups and roles

`ProjectArchitecture.kt` holds `projectArchitecture`, the value every test and processor of this
project reads, and `DocumentSections.kt` holds the section definitions that every group and role
uses through `by`. Both are allowed by name, so adding a third file here is a decision, not an
accident.

`layout { }` writes the package as `"com/example"` directly. It does not use `modulePackage`,
because this module's sources are in `com/example`, not in `com/example/architectureTest`, which
is what would be derived from the module name.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/ProjectArchitecture.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/DocumentSections.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... The entry point of the definition
- `DocumentSections.kt` ... The sections every group and role shares

## Forbidden contents

- A group or a role. Those belong to the group definition and role definition roles, one
  declaration per file
- The processors. `processors/` belongs to the "Processor" role
