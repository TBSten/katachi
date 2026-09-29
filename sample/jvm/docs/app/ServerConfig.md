[Ktor sample app](../README.md) / [Application](README.md)

# Server configuration

Configuration files loaded at runtime. Resources that are not Kotlin have roles too

The configuration the started process reads. It lives in `src/main/resources` and takes
effect at runtime, not at build time. It is also an example of the fact that not only
Kotlin files have roles.

`application.conf` writes the listening port (`18080`, overridable with the environment
variable `PORT`) and the module applied at startup, `com.example.ApplicationKt.module`. It
is paired by name with the entry point's `Application.module()`, and fixing only one of
them stops the server from starting. `logback.xml` decides where logs go and their format.

`layout { }` lists the two files by name rather than with a wildcard. If a third setting
that takes effect at runtime appears, it should not be allowed to appear silently; it is
something we want to notice.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/resources/application.conf` |  |
| `:` | `src/main/resources/logback.xml` |  |

## Examples

- `application.conf` ... The listening port and the applied module
- `logback.xml` ... Where logs go and their format

## Forbidden contents

- Build settings. Dependencies and plugins are the Gradle script roles
- Values that differ per developer, or secrets. `local.properties` is in `.gitignore` and
  is never handed to the check under the default `files = gitTracked()` in the first
  place
