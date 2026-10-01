[katachi-sample-android](../README.md) / [Testing](README.md)

# Architecture test

The single JUnit test that runs the definition, kept in src/test/kotlin of :architecture-test

The test that checks the whole project through the definition written here: a single
JUnit test that calls `projectArchitecture.assert()`. This is the only test a project
adopting katachi writes. The tests in which a feature module checks its own ViewModel
are a different role (feature test) and live in each feature's `src/test`.

Files named `*Test.kt` directly under the package (outside `groups/` and `roles/`) are
this role. That tells them apart from the architecture definition roles in the same
module: putting a test in `roles/` gives an `[UnexpectedFile]`, and conversely a
definition helper cannot be placed next to the test.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/sample/*Test.kt` |  |

## Examples

- `ProjectArchitectureTest` ... The only test a user writes

## Forbidden contents

Tools used from tests. Stand-in implementations handed to other modules' tests belong to
the fake role of `:testing`, and cannot be exposed from `src/test`.
