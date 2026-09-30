[katachi-sample-android](../README.md) / [Entrypoint layer](README.md)

# Application entrypoint

The single Application class Android creates at process start, kept in :app

The `Application` Android creates once when the process starts. `:app` has one
`MainApplication`, named exactly. If it is gone, the check fails with `[MissingFile]`.

`MainApplication` only extends `Application`. It is left empty as the place to add "once
at launch" work such as initializing a DI container.

The package is written directly as `com/example/sample` for the same reason as
`MainActivity`: `:app` is the application itself.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:app` | `src/main/kotlin/com/example/sample/MainApplication.kt` |  |

## Examples

- `MainApplication` ... The Application implementation

## Forbidden contents

Screen contents. Anything drawn on screen belongs to `:ui` or `:feature:*`.
