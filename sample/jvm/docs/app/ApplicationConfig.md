[Ktor sample app](../README.md) / [Application](README.md)

# Application configuration

The configuration file loaded at startup: the port and the module to apply

`application.conf` lives in `src/main/resources` and takes effect at runtime, not at
build time. It writes the listening port (`18080`, overridable with the environment
variable `PORT`) and the module applied at startup, `com.example.ApplicationKt.module`.
It is paired by name with the entry point's `Application.module()`, and fixing only one
of them stops the server from starting.

It is also an example of the fact that not only Kotlin files have roles.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/resources/application.conf` |  |

## Examples

- `application.conf` ... The listening port and the applied module

## Forbidden contents

- Build settings. Dependencies and plugins are the Gradle script roles
- Values that differ per developer, or secrets. `local.properties` is in `.gitignore` and
  is never handed to the check under the default `files = gitTracked()` in the first
  place
