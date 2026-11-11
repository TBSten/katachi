[Ktor sample app](../README.md) / [Data](README.md)

# Repository

Handles fetching and saving data, hiding data source details from the domain

The place that takes responsibility for where a value comes from. A Service only calls
`HealthRepository.load()` and does not know whether what lies behind it is a DB, a file or
a fixed value. The aim is to confine what needs fixing when the data source changes to
within this role.

The file name is `*Repository.kt`, one class per file.

## Placement

| Path | When to use |
|---|---|
| `src/main/kotlin/com/example/repository/*Repository.kt` |  |

## Examples

- `HealthRepository` ... The source of the running status

## Allowed contents

Only the round trip to an external data source and repacking the result into models may be
placed here. This sample's `HealthRepository` has no DB and simply returns
`Health(status = "UP", version = "0.1.0")`, but in terms of what katachi looks at, "where
it is placed and what it is named", it has the same shape as a real one.

## Forbidden contents

- Application-specific decisions. What to prioritize and how to combine things is the
  service's role
- Ktor types. An HTTP request never comes down this far
- Returning a type specific to the data source to the outside. Return values are aligned
  to models
