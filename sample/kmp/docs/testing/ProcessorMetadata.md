[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Processor metadata

Owner.kt, the metadata key and property a custom processor reads, kept in the processor package

The custom metadata of this sample: a `MetadataKey` and the property that writes it
(`owner`), which a custom processor then reads. `owner` is not a word katachi ships, so
it lives with the processor package rather than in the definition.

Only `Owner.kt` is declared, by name. A new key would get its own role, or be added
here once there is a second one to justify a pattern.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/kmp/processor/Owner.kt` |  |

## Examples

- `Owner.kt` ... The metadata key `owner` and the property that writes it
