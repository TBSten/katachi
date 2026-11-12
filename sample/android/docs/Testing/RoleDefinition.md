[katachi-sample-android](../README.md) / [Testing](README.md)

# Role definition

<group>/<Name>Role.kt, one role of the definition, with where its files live

One role of the definition per file, in `<group>/<Name>Role.kt`, and the file name says
which role it is. The directory is the one of the group that calls the role
(`feature/ScreenRole.kt`, because `featureGroup()` calls `screen()`). A role that no
group calls sits next to `ProjectArchitecture.kt`. A file that is not a `*Group.kt` or
`*Role.kt` in a group directory, such as a shared helper, becomes an `[UnexpectedFile]`.

`:architecture-test` is a plain `kotlin("jvm")` module that belongs to no layer of the
app. It is not an Android module because katachi is a JVM library and the definition can
take the same shape whatever the project type. Its package cannot be derived from the
module path (applied as is it would become `com/example/sample/architectureTest`), so
the roles of this module write `com/example/sample` directly.

Not called from anywhere on its own: add the call to the group this role belongs to
once it says something.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/sample/**/*Role.kt` |  |
| `architecture-test/src/test/kotlin/com/example/sample/*/*Role.kt` |  |

## Examples

- `feature/ScreenRole.kt` ... The declaration of the Screen role
