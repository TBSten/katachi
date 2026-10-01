[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Group definition

One group of the katachi definition, declared in `groups/<Name>Group.kt`

One group per file, named after the group with a `Group` suffix. The function inside says what
the group is made of by calling the role functions in order.

None of those functions may be `inline`: katachi captures the declaration site from the stack,
and an inlined frame reports the caller's file with a line number past its end.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/groups/*Group.kt` |  |

## Examples

- `groups/CoreGroup.kt` ... A group made of three roles

## Forbidden contents

Role declarations. A role belongs to the role definition role, in `roles/`.
