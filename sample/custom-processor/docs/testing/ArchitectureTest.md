[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Architecture test

The tests that check the project against the definition and exercise the processors

`ProjectArchitectureTest` checks the project against the definition. `CustomProcessorSpec`
calls the three processors through the API, and `LayoutSnapshotSpec`, katachi's own sentinel,
lives in the same module. The last two are matched by the `*Spec` name.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/ProjectArchitectureTest.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/*Spec.kt` |  |

## Examples

- `ProjectArchitectureTest.kt` ... The test that asserts the definition
- `CustomProcessorSpec.kt` ... The processors called the way a user calls them

## Forbidden contents

Processors. `processors/` belongs to the "Processor" role.
