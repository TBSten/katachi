[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Processor spec

Tests of the custom processors, kept in the processor package of :architecture-test

The tests that run a custom processor against a small architecture and pin what it
returns, in `processor/*Spec.kt` beside the processor they test. They are a role of
their own, not part of ProjectArchitectureSpec, because a processor and its spec travel
together: someone copying a processor into their own project takes the spec with it.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/kmp/processor/*Spec.kt` |  |

## Examples

- `PlatformOwnedFilesSpec` ... The spec of PlatformOwnedFilesProcessor
