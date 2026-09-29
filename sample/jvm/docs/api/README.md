[Ktor sample app](../README.md)

# API

The layer that faces HTTP. It owns everything from receiving a request to returning a response

The layer that an incoming request hits first. It is also a wall that keeps the rest of
the application from having to know about `io.ktor.server.*`.

It gathers two things. A controller is the entry point for one endpoint; the Ktor plugin
configuration holds the settings that apply once to the whole Application, plus the wiring
that decides which Controllers are connected to the routing tree. Both are about "how HTTP
is received" and change for the same reasons, so they share a group.

Note that katachi's `layout { }` only looks at where files live and what they are named.
A convention such as "a Controller does not call a Repository directly" is only written
here as prose; nothing rejects it mechanically.

| Role | Summary |
|---|---|
| [Controller](./Controller.md) | Receives one HTTP request, calls the matching Service and returns the result |
| [Ktor plugin configuration](./KtorPlugin.md) | Applies one cross-cutting setting to the Ktor Application |

## Placement in this group

```
:
  src/main/kotlin/**/
    controller/*/*Controller.kt  Controller
    plugin/*.kt                  Ktor plugin configuration
```

## Forbidden contents

What must not be placed here is the decision of "what to return" and where a value comes
from. The former belongs to the domain, the latter to the data layer. In fact,
`io.ktor.server.*` is imported only in this layer and the entry point, and never in
`service`, `repository` or `model`.

## Test policy

Test the following.

- That the routing is registered: start a `testApplication` and send real requests
- That the status code and the required keys in the JSON body are correct
- That a path that was not registered returns 404

A Service can be swapped through its constructor, so branch coverage belongs to the
domain tests; here we only look at the shape that comes out over HTTP.
