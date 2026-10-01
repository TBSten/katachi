[Ktor sample app](../README.md)

# Application

Starting the process, and the configuration loaded at runtime

A layer that gathers what answers "how does this process start?". Rather than a layer, it
is a home for the startup code that belongs to no other layer.

It holds the entry point (`Application.kt`) and the server configuration
(`application.conf` and `logback.xml`). The two are a pair: `modules` in `application.conf`
names `com.example.ApplicationKt.module`, and that function calls each plugin
configuration. Fixing only one of them stops the server from starting, so they can be
read in the same place.

What is not placed here: settings that only take effect at build time (the Gradle script
roles) and the contents of each `install(...)` (the Ktor plugin configuration role in the
API layer).

| Role | Summary |
|---|---|
| [Entry point](./Entrypoint.md) | Starting the process and assembling the Ktor Application module |
| [Application configuration](./ApplicationConfig.md) | The configuration file loaded at startup: the port and the module to apply |
| [Logging configuration](./LoggingConfig.md) | The Logback configuration: where logs go and their format |

## Placement in this group

```
src/main/
  kotlin/com/example/Application.kt  Entry point
  resources/
    application.conf                 Application configuration
    logback.xml                      Logging configuration
```
