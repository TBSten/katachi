[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Definition entrypoint

ProjectArchitecture.kt, which only calls the group functions and belongs to no layer

The entrypoint of the definition, written in the katachi DSL. `ProjectArchitecture.kt`
holds `projectArchitecture` and only calls the group functions, so it stays short no
matter how many roles the app grows. Where each group and role lives is the business of
the GroupDefinition and RoleDefinition roles.

It sits in a dedicated `:architecture-test` module because this is KMP. katachi is a JVM
library, and the other modules of this build are KMP with only Android and iOS, so there
is no `commonTest` anywhere to hold the definition. Preparing one `kotlin("jvm")` module
is also the shape katachi recommends. Its package cannot be derived from the module path
(applied as is it would become `com/example/kmp/architectureTest`), so the roles of this
module write `com/example/kmp` directly.

The extension functions of the definition must not be `inline`. katachi takes the
declaration site from the stack trace, so inlining would point at a line in the
caller's file that nobody wrote. `ProjectArchitectureSpec` guards this by reading that
line back.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/kmp/ProjectArchitecture.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... The entry point of the definition
