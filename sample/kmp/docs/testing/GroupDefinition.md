[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Group definition

groups/<Name>Group.kt, one group of the definition, which lists its roles by calling their functions

One group of the definition per file, in `groups/<Name>Group.kt`, and the file name
says which group it is (the group `"app"` is in `groups/AppGroup.kt`). A group says what
it is made of by calling the role functions in order. Only `*Group.kt` may sit in
`groups/`, so a shared helper slipping in as another kind of file becomes an
`[UnexpectedFile]`.

The layout takes `*` for the name, so adding a group passes without touching the
definition. The group still has to be called from `ProjectArchitecture.kt` to say
anything.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/kmp/groups/*Group.kt` |  |

## Examples

- `groups/UiGroup.kt` ... The declaration of the ui group
