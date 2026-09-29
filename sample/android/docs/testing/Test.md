[katachi-sample-android](../README.md) / [Testing](README.md)

# Test code

Tests that check the definition, kept in src/test/kotlin of :architecture-test

The tests of `:architecture-test`. Every module on the app side is checked through the
definition written here. The tests in which a feature module checks its own ViewModel
are a different role (screen test) and live in each feature's `src/test`.

Files named `*Spec.kt` or `*Test.kt` directly under the package (outside `groups/` and
`roles/`) are tests. These two points tell them apart from the architecture definition
role in the same module: putting a test in `roles/` gives an `[UnexpectedFile]`, and
conversely a definition helper cannot be placed next to the tests.

There are four now. The only one a user writes is `ProjectArchitectureTest`, a single
JUnit test that calls `projectArchitecture.assert()`. The rest verify katachi itself:
`ProjectArchitectureSpec` checks the assembled definition, `LayoutSnapshotSpec` compares
the flattened layout with the snapshot, and `ProjectRootSpec` watches the result of the
project root lookup.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/sample/*Spec.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/sample/*Test.kt` |  |

## Examples

- `ProjectArchitectureTest` ... The only test a user writes
- `ProjectArchitectureSpec` ... A test that verifies this definition itself

## Forbidden contents

Tools used from tests. Stand-in implementations handed to other modules' tests belong to
the fake role of `:testing`, and cannot be exposed from `src/test`.
