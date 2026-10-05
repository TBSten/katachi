[Ktor sample app](../README.md) / [Application](README.md)

# Entry point

Starting the process and assembling the Ktor Application module

The single point where the process starts. `Application.kt` holds just two things:
`main()` and `Application.module()`. `main()` calls `EngineMain.main(args)`, and the engine
reads `src/main/resources/application.conf` and starts listening.

`Application.module()` is a function that merely lines up `configureSerialization()` and
`configureRouting()`. Reading it tells you which cross-cutting settings this app has, and
in what order. Adding a plugin configuration means touching one line in this list.

This role's `layout { }` has no wildcard and requires exactly one `Application.kt`.
Deleting it produces `[MissingFile]`, so the check cannot pass with no entry point
anywhere.

## Placement

| Path | When to use |
|---|---|
| `src/main/kotlin/com/example/Application.kt` |  |

## Examples

- `Application.kt` ... Where the process starts

## Forbidden contents

- The contents of `install(...)`. The settings themselves belong to the Ktor plugin
  configuration role; only the calls belong here
- Endpoint registration. `routing { }` is held by `plugin/Routing.kt`
- The listening port and the list of modules to apply. Those are on the
  `application.conf` (server configuration) side
