[Ktor sample app](../README.md) / [Testing](README.md)

# Test code

Tests placed in src/test/kotlin, keeping the same package structure as the main code

The code that checks the application's behaviour. It sits in the same package as its
target, so that what is under `src/test/kotlin` mirrors `src/main/kotlin`.

`HealthRouteTest` is written with kotest's `FreeSpec`, starts Ktor's `testApplication`
and sends a real request to `GET /health`. A test name is a single sentence that says
exactly what is being checked. It is a safeguard against a state where the layout check
passes but the contents are dead.

What does not belong here:

- Tests of the architecture definition itself. Those live in `:architecture-test` and are
  covered by the architecture definition role. This role only looks at the test source
  set of the root project (`:`)

The file name is `*Test.kt`, so a helper or a fixture with another name is reported
rather than let in silently. `**` is the package hierarchy, which mirrors the main
source set, and a directory left under the test source set without a single test is
reported.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/test/kotlin/**/*Test.kt` |  |

## Examples

- `HealthRouteTest` ... The test for GET /health
