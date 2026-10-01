[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Architecture test

The one test that checks the whole project against the definition

`ProjectArchitectureTest` calls `assert()` on the definition. Every violation of the
repository arrives in a single failure message, so there is nothing to gain from splitting
it up. It is the only test a project adopting katachi writes.

The file name is `*Test.kt`. The `*Spec` files next to it have a role of their own.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/*Test.kt` |  |

## Examples

- `ProjectArchitectureTest.kt` ... The test that asserts the definition

## Forbidden contents

- Tests of the application. Those go in the root project's test source set
- The processors' tests. Those are `*Spec` files, which belong to the integration spec role
