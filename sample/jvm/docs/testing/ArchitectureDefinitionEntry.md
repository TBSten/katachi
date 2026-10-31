[Ktor sample app](../README.md) / [Testing](README.md)

# Architecture definition entry

The one file whose `architecture { }` gathers every group, so the definition can be read from here

The starting point of the definition. `ProjectArchitecture.kt` holds the `architecture { }`
block, as `val projectArchitecture`, and it only calls the group functions. The groups, the
roles, the processors and the headings every role file shares (`DocumentSections.kt`) are
other kinds of file with their own roles, so this role is the one file and nothing else.

The definition lives in a dedicated module, `:architecture-test`, because it belongs to no
layer of the application. The application is the root project (`:`), so moving the
definition out keeps the application's main source set at one.

The one constraint, written with `ktFileContent { }`, keeps the entry a table of
contents: the file holds `val projectArchitecture` and nothing else, so it declares no
group or role itself (an extension function on `DeclarationContainerScope`), because
those are declared in `<Name>Group.kt` and `<Name>Role.kt`, in the directory named after
each group.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt` |  |

## Constraints

- [Must not declare a group or a role in the entry file (ktFileContent)](#kotlin-file-content)

## Kotlin file content

Example: `ProjectArchitecture.kt`

| Package |
|---|
| `com.example` |

| Import | |
|---|---|
| `com.example.**` | optional |
| `me.tbsten.katachi.**` | optional |

| Declaration | |
|---|---|
| `val projectArchitecture: Architecture` | required |

## Examples

- `ProjectArchitecture.kt` ... The entry point of the definition

## Forbidden contents

- A group or a role declared in this file. Those go in `<Name>Group.kt` and `<Name>Role.kt`
- Application code. Creating `src/main/kotlin` in `:architecture-test` fails as files that
  no role covers
