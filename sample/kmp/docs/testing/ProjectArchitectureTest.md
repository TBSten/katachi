[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Architecture test

The single JUnit test that runs the definition, kept in src/test/kotlin of :architecture-test

The test that checks the whole project through the definition: a single JUnit test that
calls `projectArchitecture.assert(FileConstraintCheck())`. This is the only test a project
adopting katachi writes. `FileConstraintCheck()` is what makes the ViewModel role's
`konture { }` constraint run; without it the constraint is reported as not evaluated.
The definition it runs is described by the DefinitionEntry, GroupDefinition and
RoleDefinition roles, and kept apart from this file because the definition's job is to
describe the project.

Files named `*Test.kt` directly under the package (outside `groups/`, `roles/` and
`processors/`) are this role. That tells them apart from the roles of the definition in
the same module.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/kmp/*Test.kt` |  |

## Examples

- `ProjectArchitectureTest` ... The only test a user writes

## Forbidden contents

- Tests of katachi itself (`*Spec.kt`). They are the ProjectArchitectureSpec role
