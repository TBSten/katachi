[Ktor sample app](../README.md) / [Architecture definition](README.md)

# Architecture test

The test that checks the whole project against the definition

The file name is `*Test.kt`. It calls `assert(FileConstraintCheck())` on the definition: with
a bare `assert()`, the `ktFileContent { }` constraints of the roles are reported as
`[UncheckedFileConstraint]` instead of being evaluated.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/*Test.kt` |  |

## Constraints

- [ktFileContent](#kotlin-file-content)

## Kotlin file content

Example: `ProjectArchitectureTest.kt`

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
| (anything) | optional |
