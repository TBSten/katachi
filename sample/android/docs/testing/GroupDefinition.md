[katachi-sample-android](../README.md) / [Testing](README.md)

# Group definition

groups/<Name>Group.kt, one group of the definition, which lists its roles by calling their functions

One group of the definition per file, in `groups/<Name>Group.kt`, and the file name
says which group it is. A group says what it is made of by calling the role functions
in order. Only `*Group.kt` may sit in `groups/`, so a shared helper slipping in as
another kind of file becomes an `[UnexpectedFile]`.

`:architecture-test` is a plain `kotlin("jvm")` module that belongs to no layer of the
app. It is not an Android module because katachi is a JVM library and the definition can
take the same shape whatever the project type. Its package cannot be derived from the
module path (applied as is it would become `com/example/sample/architectureTest`), so
the roles of this module write `com/example/sample` directly.

Not called from anywhere on its own: add the call to `ProjectArchitecture.kt` once the
group says something.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/sample/groups/*Group.kt` |  |

## Examples

- `groups/UiGroup.kt` ... The declaration of the ui group
