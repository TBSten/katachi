[Ktor sample app](../README.md) / [Testing](README.md)

# Architecture definition

The role definitions written in katachi's DSL. It belongs to no layer

The code that describes the shape of this project. Since it belongs to no layer of the
application, it lives in a dedicated module, `:architecture-test`. The application is the
root project (`:`), so moving the definition out keeps the application's main source set
at one.

It is one declaration per file. `ProjectArchitecture.kt` is the entry point,
`groups/<Name>Group.kt` holds a group, `roles/<Name>Role.kt` holds a role, and
`processors/` holds the processors this sample wrote itself. `ProjectArchitectureTest`,
which checks that the project matches the definition, and the `*Spec` files, which are
katachi's own integration tests, are in the same module, so this role covers them too.

`layout { }` looks at everything under `com/example` with `**`. Splitting into `groups/`
and `roles/` is a convention for readability, not something `layout { }` enforces (a `.kt`
in `roles/` that declares nothing still passes).

Instead, only the reverse convention is checked, with `konsist(scope = DirectOnly)`:
no group or role declaration (an extension function on `DeclarationContainerScope`) is
placed **directly** under `com/example`; those go in `groups/` and `roles/`. Because it is
`scope = DirectOnly`, this constraint does not descend into the files inside `groups/` and
`roles/`.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/*.kt` |  |
| `:architecture-test` | `src/test/kotlin/com/example/**/*.kt` |  |

## Constraints

- Must not declare groups or roles directly here

## Examples

- `ProjectArchitecture.kt` ... The entry point of the definition
- `roles/ControllerRole.kt` ... The declaration of a single role
- `ProjectArchitectureTest.kt` ... The test that asserts the definition

## Forbidden contents

- Application code. Creating `src/main/kotlin` in `:architecture-test` fails as files that
  no role covers
- Anything that already has a layer it belongs to. This is a place to write only "shape"
