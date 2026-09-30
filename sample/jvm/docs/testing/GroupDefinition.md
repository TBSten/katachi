[Ktor sample app](../README.md) / [Testing](README.md)

# Group definition

One `groups/<Name>Group.kt` per group, saying what the group is made of by calling role functions

A group is declared as an extension function on `DeclarationContainerScope`, the scope
that `architecture { }` and `"...".group { }` share. Because a group only calls the
role functions, a role can be moved into another group without touching the role's own
file.

The file name is `*Group.kt`, so a file in `groups/` that is not a group is reported.
Nothing here may be `inline`: katachi captures the declaration site from the stack, and
an inlined frame would point at a line nobody wrote (`ProjectArchitectureSpec` is what
holds that line).

## Placement

| Module | Path | When to use |
|---|---|---|
| `:architecture-test` | `src/test/kotlin/com/example/groups/*Group.kt` |  |

## Examples

- `groups/DomainGroup.kt` ... A group that lists the roles of the domain layer

## Forbidden contents

- A role. Those go in `roles/`
