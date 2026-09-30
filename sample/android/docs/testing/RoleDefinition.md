[katachi-sample-android](../README.md) / [Testing](README.md)

# Role definition

roles/<Name>Role.kt, one role of the definition, with where its files live

One role of the definition per file, in `roles/<Name>Role.kt`, and the file name says
which role it is. Only `*Role.kt` may sit in `roles/`, so a shared helper slipping in
as another kind of file becomes an `[UnexpectedFile]`.

`:architecture-test` is a plain `kotlin("jvm")` module that belongs to no layer of the
app. It is not an Android module because katachi is a JVM library and the definition can
take the same shape whatever the project type. Its package cannot be derived from the
module path (applied as is it would become `com/example/sample/architectureTest`), so
the roles of this module write `com/example/sample` directly.

Not called from anywhere on its own: add the call to the group this role belongs to
once it says something.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/sample/roles/*Role.kt` |  |

## Examples

- `roles/ScreenRole.kt` ... The declaration of the Screen role
