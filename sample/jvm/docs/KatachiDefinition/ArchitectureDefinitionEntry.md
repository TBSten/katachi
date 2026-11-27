[Ktor sample app](../README.md) / [Architecture definition](README.md)

# Architecture definition entry

The one file whose architecture { } gathers every group, so the definition is read from here

`ProjectArchitecture.kt` holds `val projectArchitecture` and nothing else. It calls the
group functions, so it declares no group or role itself: those are written in
`<Name>Group.kt` and `<Name>Role.kt`, in the directory named after each group.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/ProjectArchitecture.kt` |  |

## Constraints

- [ktFileContent](#kotlin-file-content)

## Kotlin file content

Example: `ProjectArchitecture.kt`

| Package |
|---|
| `com.example` |

| Import | |
|---|---|
| `**` | optional |

| File annotation | |
|---|---|
| `@file:OptIn` | optional |
| `@file:Suppress` | optional |

| Declaration | |
|---|---|
| `val projectArchitecture: Architecture` | required |
