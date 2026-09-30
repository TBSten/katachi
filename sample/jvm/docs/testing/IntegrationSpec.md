[Ktor sample app](../README.md) / [Testing](README.md)

# Integration spec

katachi's own integration tests, run against this real project

The `*Spec` files check katachi itself in a real user build: that the definition is
modelled as written, that a role's declaration site is the line that wrote it, that the
layout snapshot is up to date. They are here because `:katachi` compiles with settings a
user's build does not have. A project that merely uses katachi does not write them.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/*Spec.kt` |  |

## Examples

- `ProjectArchitectureSpec.kt` ... Checks the definition is modelled as written

## Forbidden contents

- The test a project adopting katachi writes. That is `ProjectArchitectureTest`
