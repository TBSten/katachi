[Ktor sample app](../README.md) / [Domain](README.md)

# Service

Owns one application-specific behaviour, realized by combining Repositories

The place that says what this app does. It is called from a Controller, combines the
Repositories it needs, and returns a model. `HealthService.currentHealth()` for now just
returns the result of `HealthRepository.load()`, but this is where decisions go when they
multiply.

The file name is `*Service.kt`, one class per file. Among the application's roles, only
this one writes a constraint with `konsist { }`, "Must be public", so accidentally adding
`internal` fails the test. You notice before it can no longer be referenced from a
Controller in another package.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/kotlin/**/service/*Service.kt` |  |

## Constraints

- Must be public

## Examples

- `HealthService` ... Getting the server running status

## Allowed contents

Only application-specific procedures, decisions and assembly may be placed here.
Processing that spans several Repositories, or that cross-checks fetched values, comes
here.

## Forbidden contents

- Ktor types such as `Route`, `call` and `respond`. Knowledge of HTTP stops at the API
  layer
- Data source details (connection targets, queries, file paths). The repository hides
  those
- The definition of a value itself. A data class is the model's role
