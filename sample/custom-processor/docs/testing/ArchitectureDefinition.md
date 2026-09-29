[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Architecture definition

Role definitions written in the katachi DSL, and the tests that assert them

This is the code that describes the shape of this project. It belongs to no layer of the
application, so it lives in a dedicated module, `:architecture-test`.

It is one declaration per file. `ProjectArchitecture.kt` is the entry point,
`groups/<Name>Group.kt` holds a group and `roles/<Name>Role.kt` holds a role. The section
definitions that every group and role uses through `by` are collected in `DocumentSections.kt`,
which, like `ProjectArchitecture.kt`, is allowed by name. `ProjectArchitectureTest`, which
checks the project against the definition, `CustomProcessorSpec`, which calls the three
processors through the API, and `LayoutSnapshotSpec`, katachi's own sentinel, live in the same
module and are covered by this role.

`layout { }` writes the package as `"com/example"` directly. It does not use `modulePackage`,
because this module's sources are in `com/example`, not in `com/example/architectureTest`, which
is what would be derived from the module name.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/ProjectArchitecture.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/DocumentSections.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/ProjectArchitectureTest.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/*Spec.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/groups/*.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/roles/*.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... The entry point of the definition
- `roles/StoreRole.kt` ... The declaration of a single role
- `ProjectArchitectureTest.kt` ... The test that asserts the definition

## Forbidden contents

- The processors themselves. `processors/` belongs to the "Processor" role, and the split is
  deliberate. A definition writes a shape and a processor reads that shape to produce
  something, so they face opposite directions. Keeping them apart also gives them separate
  rows in the output of `RoleFileCount`
- Application code. If you create `src/main/kotlin` in `:architecture-test`, its files fail as
  covered by no role
