[Ktor sample app](../README.md) / [API](README.md)

# Controller

Receives one HTTP request, calls the matching Service and returns the result

The boundary between HTTP and the inside of the application. It owns registering paths and
methods, pulling values out of the request, and turning what a Service returned into a
response.

One controller per file, and the file name is `<Resource>Controller.kt`. Each resource
gets one package such as `controller/health/`, and the controller goes inside it, named
after the directory: `controller/health/HealthController.kt`. The file name directly
names a group of endpoints, and the directory decides it, so the name alone tells you
whether a new path belongs in an existing file or in a new one. `layout { }` only looks
at where files live and what they are named; `ktFileContent { }` adds what a file holds:
the package of its resource, a class with `register(route: Route)`, and imports that
leave out `com.example.repository`. The rest of "Forbidden contents" is not rejected
mechanically.

It can be generated from a template. The package level of the resource is named
`resource` and the file name is derived from it, so `--arg template=Controller --arg
resource=user` produces `controller/user/UserController.kt`. `resource` goes straight
into the package, so the template accepts only letters and digits (a value such as
`--arg resource=user-profile` is rejected).

## Placement

| Path | When to use |
|---|---|
| `src/main/kotlin/com/example/controller/*/*Controller.kt` |  |

## Constraints

- [ktFileContent](#kotlin-file-content)

## Kotlin file content

Example: `HealthController.kt`

| Package |
|---|
| `com.example.controller.<resource>` |

| Import | |
|---|---|
| `io.ktor.server.**` | optional |
| `com.example.service.**` | optional |
| `com.example.model.**` | optional |

| Declaration | |
|---|---|
| `class HealthController` | required |

### Members of `class HealthController`

| Member | |
|---|---|
| `private val *: *Service` | optional |
| `fun register(Route)` | required |

## Examples

- `HealthController` ... The health check entry point

## Allowed contents

Only registration on a Ktor `Route` and the conversion needed to hand values over may be
placed here. `HealthController` writes `route.get("/health") { ... }` inside
`register(route: Route)`, and the `HealthService` it calls is received as a constructor
argument with a default value, so a test can swap it.

## Forbidden contents

- Branching or computation. The moment it decides "which one to return", it is the
  service's job
- Calls to `com.example.repository`. A Controller does not fetch data directly
- Application-wide configuration such as `install(...)`, and `routing { }` itself. Which
  Controllers are connected to the routing tree is decided by `plugin/Routing.kt`
