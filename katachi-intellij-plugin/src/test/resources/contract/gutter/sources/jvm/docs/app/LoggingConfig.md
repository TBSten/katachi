[Ktor sample app](../README.md) / [Application](README.md)

# Logging configuration

The Logback configuration: where logs go and their format

`logback.xml` lives in `src/main/resources` and is read by Logback when the process
starts. It decides where logs go and their format. It is not Kotlin and not the server's
own configuration, so it is a role of its own next to `application.conf`.

## Placement

| Path | When to use |
|---|---|
| `src/main/resources/logback.xml` |  |

## Examples

- `logback.xml` ... Where logs go and their format

## Forbidden contents

- Server settings such as the port. Those are in `application.conf`
