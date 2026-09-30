[katachi-sample-android](../README.md) / [Entrypoint layer](README.md)

# Proguard rules

proguard-rules.pro of :app, the rules the code shrinker reads

The `proguard-rules.pro` at the root of `:app`. Its shape is decided by the Android
build system, not by this project. There is only one per app, so it is named exactly
and a second one is a violation.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:app` | `proguard-rules.pro` |  |

## Examples

- `proguard-rules.pro` ... The rules the shrinker reads
