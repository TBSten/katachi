[Ktor sample app](../README.md) / [Domain](README.md)

# Model

The values the domain handles, used as-is for API input and output too

The values the application handles. `Health` is a `@Serializable` data class with `status`
and `version`; the Repository creates it, the Service passes it along, and the Controller
uses it as-is as the response body of `GET /health`.

Not separating domain models from API payloads is a form this sample chose on purpose.
They are one thing for now, so that if the two start to drift apart, a new DTO role can be
added to the API layer to split them.

The name is simply what the value represents, with no suffix (`Health`, not
`HealthModel`). Only `.kt` files directly in the `model` package are covered; a directory
dug beneath it does not fall under this role.

## Placement

| Path | When to use |
|---|---|
| `src/main/kotlin/com/example/model/*.kt` |  |

## Examples

- `Health` ... Running status and version

## Allowed contents

Only data classes, enums, value objects and computations closed over those values may be placed here.

## Forbidden contents

- Fetching or saving. I/O is the repository's role
- Dependencies on Ktor. A model may know about `kotlinx.serialization` and no further
