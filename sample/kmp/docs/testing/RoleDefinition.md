[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Role definition

roles/<Name>Role.kt, one role of the definition, with where its files live

One role of the definition per file, in `roles/<Name>Role.kt`, and the file name says
which role it is (the role `"UiCore"` is in `roles/UiCoreRole.kt`). Only `*Role.kt` may
sit in `roles/`, so a shared helper slipping in as another kind of file becomes an
`[UnexpectedFile]`.

Not called from anywhere on its own: add the call to the group this role belongs to
once it says something.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/kmp/roles/*Role.kt` |  |

## Examples

- `roles/ScreenRole.kt` ... The declaration of the Screen role
