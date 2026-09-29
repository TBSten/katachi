[Ktor sample app](../README.md) / [API](README.md)

# Ktor plugin configuration

Applies one cross-cutting setting to the Ktor Application

A place for settings that take effect once for the whole Application, not for a specific
endpoint. Each file holds one extension function on `Application`, `configureXxx()`, and
the entry point's `Application.module()` calls them in turn.

File names have no suffix. There is no cue like `*Controller`, so it is the `plugin`
package itself that says "this is a plugin configuration". The file name matches the name
of the Ktor feature being installed.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/kotlin/**/plugin/*.kt` |  |

## Examples

- `Routing` ... Wiring of the routing tree
- `Serialization` ... JSON input/output configuration

## Allowed contents

Only a Ktor plugin's `install(...)` and its configuration block may be placed here.
`Serialization.kt` installs JSON (with `prettyPrint` and `ignoreUnknownKeys` enabled) into
`ContentNegotiation`, and `Routing.kt` opens `routing { }` and calls each Controller's
`register`. The aim is to see in one file which Controllers are connected.

## Forbidden contents

- The body of an endpoint handler. What goes inside `get`/`post` is the controller's role
- Domain decisions or data fetching. Do not call a Service or Repository from here
- `main()` and `Application.module()`. Starting and assembling belong to the entry point
  role
