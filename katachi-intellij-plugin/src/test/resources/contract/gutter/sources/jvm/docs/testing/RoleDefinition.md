[Ktor sample app](../README.md) / [Testing](README.md)

# Role definition

One `roles/<Name>Role.kt` per role, holding its description, its layout and its constraints

A role is declared as an extension function on `DeclarationContainerScope`, one per
file, so the description, the `layout { }` and the constraints of a role are read in one
place. The file name is `*Role.kt`; a file in `roles/` that is not a role is reported.

A role that only a role needs (a private helper such as `ServiceRole`'s constraint) stays
in the file that uses it, so the declaration site a violation names is the role that
owns the rule.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/roles/*Role.kt` |  |

## Examples

- `roles/ControllerRole.kt` ... The declaration of a single role

## Forbidden contents

- A group. Those go in `groups/`
