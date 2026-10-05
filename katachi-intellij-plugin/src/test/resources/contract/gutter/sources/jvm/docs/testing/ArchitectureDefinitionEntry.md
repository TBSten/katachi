[Ktor sample app](../README.md) / [Testing](README.md)

# Architecture definition entry

The one file whose `architecture { }` gathers every group, so the definition can be read from here

The starting point of the definition. `ProjectArchitecture.kt` holds the `architecture { }`
block and the `val` every role file shares, and it only calls the group functions. The
groups, the roles and the processors are other kinds of file with their own roles, so
this role is the one file and nothing else.

The definition lives in a dedicated module, `:architecture-test`, because it belongs to no
layer of the application. The application is the root project (`:`), so moving the
definition out keeps the application's main source set at one.

The one constraint keeps the entry a table of contents: it declares no group or role
itself (an extension function on `DeclarationContainerScope`), because those are
declared in `groups/` and `roles/`.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt` |  |

## Constraints

- Must not declare a group or a role in the entry file

## Examples

- `ProjectArchitecture.kt` ... The entry point of the definition

## Forbidden contents

- A group or a role declared in this file. Those go in `groups/` and `roles/`
- Application code. Creating `src/main/kotlin` in `:architecture-test` fails as files that
  no role covers
