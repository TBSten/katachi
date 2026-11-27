[Ktor sample app](../README.md) / [Architecture definition](README.md)

# Group definition

One <Name>Group.kt per group, in the directory named after the group, listing the roles it is made of

A group is an extension function on `DeclarationContainerScope` that calls the role
functions, so a role moves into another group by moving its file. The function is named
after the file (`DomainGroup.kt` declares `domainGroup()`) and is the only declaration in it.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/**/*Group.kt` |  |
| `architecture-test/src/test/kotlin/com/example/*/*Group.kt` |  |

## Constraints

- [ktFileContent](#kotlin-file-content)

## Kotlin file content

Example: `ApiGroup.kt`

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
| `fun DeclarationContainerScope.apiGroup()` | required |
| `(val or var) *` | optional |
| `private` (anything) | optional |
