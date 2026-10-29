[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Role definition

<group>/<Name>Role.kt, one role of the definition, with where its files live

One role of the definition per file, in `<group>/<Name>Role.kt`, and the file name says
which role it is (the role `"UiCore"` is in `ui/UiCoreRole.kt`, because `uiGroup()`
calls `uiCore()`). The directory is the one of the group that calls the role. A role
that no group calls sits next to `ProjectArchitecture.kt`. A file that is not a
`*Group.kt` or `*Role.kt` in a group directory, such as a shared helper, becomes an
`[UnexpectedFile]`.

Not called from anywhere on its own: add the call to the group this role belongs to
once it says something.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/kmp/**/*Role.kt` |  |

## Examples

- `feature/ScreenRole.kt` ... The declaration of the Screen role
