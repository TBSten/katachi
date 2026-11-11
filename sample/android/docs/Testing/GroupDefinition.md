[katachi-sample-android](../README.md) / [Testing](README.md)

# Group definition

<group>/<Name>Group.kt, one group of the definition, which lists its roles by calling their functions

One group of the definition per file, in `<group>/<Name>Group.kt`, and the file name
says which group it is. The directory is the group's name as written in
`"Ui".group { }` with its first letter in lower case (`ui/UiGroup.kt`), and a group
inside a group sits in a directory inside it. A group says what it is made of by
calling the role functions in order, and the roles it calls sit beside it in that
directory. A file that is not a `*Group.kt` or `*Role.kt` in there, such as a shared
helper, becomes an `[UnexpectedFile]`.

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
| `architecture-test/src/test/kotlin/com/example/sample/**/*Group.kt` |  |
| `architecture-test/src/test/kotlin/com/example/sample/*/*Group.kt` |  |

## Examples

- `ui/UiGroup.kt` ... The declaration of the Ui group
