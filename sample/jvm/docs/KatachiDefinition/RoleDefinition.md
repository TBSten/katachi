[Ktor sample app](../README.md) / [Architecture definition](README.md)

# Role definition

One <Name>Role.kt per role: the extension function that declares it, and the private parts only it uses

A role is an extension function on `DeclarationContainerScope`, named after the file without
`Role` (`DslRole.kt` declares `dsl()`). What only that role needs stays `private` in the
same file. The function carries no modifier such as `inline`: katachi captures the
declaration site from the stack, and an inlined frame would point at a line nobody wrote.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/**/*Role.kt` |  |
| `architecture-test/src/test/kotlin/com/example/*/*Role.kt` |  |

## Constraints

- [ktFileContent](#kotlin-file-content)

## Kotlin file content

Example: `ControllerRole.kt`

| Package |
|---|
| (the file's directory) |

| Import | |
|---|---|
| `**` | optional |

| File annotation | |
|---|---|
| `@file:OptIn` | optional |
| `@file:Suppress` | optional |

| Declaration | |
|---|---|
| `fun DeclarationContainerScope.controller()` | required |
| `private` (anything) | optional |
