[katachi-sample-android](../README.md) / [Testing](README.md)

# Architecture definition

The role definitions written in the katachi DSL. Belongs to no layer

This definition itself, written in the katachi DSL. It lives in `:architecture-test`, a
plain `kotlin("jvm")` module that belongs to no layer of the app. It is not an Android
module because katachi is a JVM library and the definition can take the same shape
whatever the project type.

One declaration per file, and the file name says which kind it is.
`ProjectArchitecture.kt` is the entrypoint and only calls the group functions,
`groups/<Name>Group.kt` holds one group, and `roles/<Name>Role.kt` holds one role. Only
`*Group.kt` and `*Role.kt` may sit in `groups/` and `roles/`, so a shared helper
slipping in as a third kind becomes an `[UnexpectedFile]`. The one exception is
`DocumentSections.kt`, which gathers just the section definitions every group and role
uses with `by`, and is allowed by name, like `ProjectArchitecture.kt`.

The extension functions must not be `inline`. katachi takes the declaration site from
the stack trace, so inlining would point at a line in the caller's file that nobody
wrote. `ProjectArchitectureSpec` guards this by reading that line back.

`:architecture-test`, like `:app`, is a module whose package cannot be derived from the
module path. Applied as is it would become `com/example/sample/architectureTest`, so
this role and the test-code role both write `com/example/sample` directly. There is no
point applying the app's package rule to something that is not part of the app.

It shares a module with the tests, but the two say different things. The definition says
"what shape it has" and the tests say "how it behaves". They are told apart by file
location and name.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/sample/ProjectArchitecture.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/sample/DocumentSections.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/sample/groups/*Group.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/sample/roles/*Role.kt` |  |

## Examples

- `ProjectArchitecture.kt` ... The entrypoint of the definition
- `roles/ScreenRole.kt` ... The declaration of the Screen role
