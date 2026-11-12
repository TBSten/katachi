[Ktor sample app](../README.md) / [Testing](README.md)

# Group definition

One `<Name>Group.kt` per group, in the directory named after the group, saying what the group is made of by calling role functions

A group is declared as an extension function on `DeclarationContainerScope`, the scope
that `architecture { }` and `"...".group { }` share. Because a group only calls the
role functions, a role moves into another group by moving its file, with its body
unchanged.

The file name is `*Group.kt`, and the file sits in the directory named after the group
(`domain/DomainGroup.kt` for `"Domain".group { }`), so the roles the group calls are its
neighbours. A `*Group.kt` placed anywhere else is reported.
Nothing here may be `inline`: katachi captures the declaration site from the stack, and
an inlined frame would point at a line nobody wrote. `ProjectArchitectureSpec` checks
that each captured line holds its declaration.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/**/*Group.kt` |  |

## Examples

- `domain/DomainGroup.kt` ... A group that lists the roles of the domain layer

## Forbidden contents

- A role. Those go in `<Name>Role.kt`, in the directory of the group that calls it
