[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Architecture definition entry

The one file whose `architecture { }` gathers every group, so the definition can be read from here

`ProjectArchitecture.kt` holds `projectArchitecture`, the value every test and processor of this
project reads, and it only calls the group functions. The groups, the roles, the processors and
the section definitions are other kinds of file with their own roles, so this role is the one
file and nothing else.

`layout { }` writes the package as `"com/example"` directly. It does not use `modulePackage`,
because this module's sources are in `com/example`, not in `com/example/architectureTest`, which
is what would be derived from the module name.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/ProjectArchitecture.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... The entry point of the definition

## Forbidden contents

- A group or a role. Those belong to the group definition and role definition roles, one
  declaration per file
- The processors. `processors/` belongs to the "Processor" role
