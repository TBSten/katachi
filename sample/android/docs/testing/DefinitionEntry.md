[katachi-sample-android](../README.md) / [Testing](README.md)

# Definition entrypoint

ProjectArchitecture.kt, which only calls the group functions and belongs to no layer

The entrypoint of the definition, written in the katachi DSL. `ProjectArchitecture.kt`
holds `projectArchitecture` and only calls the group functions, so it stays short no
matter how many roles the app grows. Where each group and role lives is the business of
the GroupDefinition and RoleDefinition roles.

`:architecture-test` is a plain `kotlin("jvm")` module that belongs to no layer of the
app. It is not an Android module because katachi is a JVM library and the definition can
take the same shape whatever the project type. Its package cannot be derived from the
module path (applied as is it would become `com/example/sample/architectureTest`), so
the roles of this module write `com/example/sample` directly.

The extension functions of the definition must not be `inline`. katachi takes the
declaration site from the stack trace, so inlining would point at a line in the
caller's file that nobody wrote. `ProjectArchitectureSpec` guards this by reading that
line back.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/sample/ProjectArchitecture.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... The entrypoint of the definition
