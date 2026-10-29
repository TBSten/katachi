[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Group definition

<group>/<Name>Group.kt, one group of the definition, which lists its roles by calling their functions

One group of the definition per file, in `<group>/<Name>Group.kt`, and the file name
says which group it is (the group `"app"` is in `app/AppGroup.kt`). The directory is the
group's name as written in `"app".group { }`, lower case and joined, and a group inside a
group sits in a directory inside it. A group says what it is made of by calling the role
functions in order, and the roles it calls sit beside it in that directory. A file that
is not a `*Group.kt` or `*Role.kt` in there, such as a shared helper, becomes an
`[UnexpectedFile]`.

The layout takes `*` for the name and `**` for the directories above it, so adding a group passes without touching the
definition. The group still has to be called from `ProjectArchitecture.kt` to say
anything.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/kmp/**/*Group.kt` |  |

## Examples

- `ui/UiGroup.kt` ... The declaration of the ui group
