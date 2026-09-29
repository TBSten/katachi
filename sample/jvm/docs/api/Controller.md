[Ktor sample app](../README.md) / [API](README.md)

# Controller

Receives one HTTP request, calls the matching Service and returns the result

The boundary between HTTP and the inside of the application. It owns registering paths and
methods, pulling values out of the request, and turning what a Service returned into a
response.

One controller per file, and the file name is `*Controller.kt`. Each resource gets one
package such as `controller/health/`, and the controller goes inside it. The file name
directly names a group of endpoints, so the name alone tells you whether a new path
belongs in an existing file or in a new one. Note that `layout { }` only looks at where
files live and what they are named; it does not mechanically reject "Forbidden contents".

It can be generated from a template. The package level of the resource is named
`resource`, so `--arg template=Controller --arg resource=user --arg name=User` produces
`controller/user/UserController.kt`. `resource` goes straight into the package, so the
template accepts only letters and digits (a value such as `--arg resource=user-profile`
is rejected).

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/kotlin/**/controller/*/*Controller.kt` |  |

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
