[katachi-sample-android](../README.md) / [Testing](README.md)

# Architecture spec

Tests that verify the definition and katachi itself, kept in src/test/kotlin of :architecture-test

The tests that guard the definition and katachi's own behavior. A project adopting
katachi does not write these; they are here because this sample is also katachi's
integration test. `ProjectArchitectureSpec` checks the assembled definition,
`LayoutSnapshotSpec` compares the flattened layout with the snapshot, and
`ProjectRootSpec` watches the result of the project root lookup.

Files named `*Spec.kt` directly under the package (outside `groups/` and `roles/`) are
this role. Putting one in `roles/` gives an `[UnexpectedFile]`.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/sample/*Spec.kt` |  |

## Examples

- `ProjectArchitectureSpec` ... A test that verifies this definition itself

## Forbidden contents

The one test a user writes (`*Test.kt`). It is the ProjectArchitectureTest role
