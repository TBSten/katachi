[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Integration spec

The tests that call the processors through the API, and katachi's own sentinel run against this real project

`CustomProcessorSpec` calls the three processors the way a user calls them, with
`projectArchitecture.process(...)`, and asserts what they return. `LayoutSnapshotSpec` is
katachi's own sentinel: it checks that the layout snapshot is up to date. Both are matched
by the `*Spec` name.

A project that merely uses katachi does not write the second one. The first is the part of
this sample a reader copies when testing a processor of their own.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/*Spec.kt` |  |

## Examples

- `CustomProcessorSpec.kt` ... The processors called the way a user calls them

## Forbidden contents

- The test a project adopting katachi writes. That is `ProjectArchitectureTest`
- The processors themselves. `processors/` belongs to the "Processor" role
