[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Custom processor

Processors a user adds beside the check katachi ships, kept in the processors package of :architecture-test

The processors of this sample: classes implementing `ArchitectureProcessorNoArg` that
read the architecture and return a value or write files. They demonstrate that a user
of katachi can add a processor of their own without touching katachi itself.

Files named `*Processor.kt` in the `processors` package are this role. The metadata key
the processors read is the ProcessorMetadata role, and their tests are ProcessorSpec.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/kmp/processors/*Processor.kt` |  |

## Examples

- `PlatformOwnedFilesProcessor.kt` ... Lists the files of roles tagged with an owner
- `RoleSummaryReportProcessor.kt` ... Writes one short page per role
