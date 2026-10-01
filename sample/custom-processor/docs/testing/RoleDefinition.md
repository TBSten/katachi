[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Role definition

One role of the katachi definition, declared in `roles/<Name>Role.kt`

One role per file, named after the role with a `Role` suffix. Each holds the role's title,
summary, description and examples, and the `layout { }` that says which files belong to it.

## Placement

| Path | When to use |
|---|---|
| `architecture-test/src/test/kotlin/com/example/roles/*Role.kt` |  |

## Examples

- `roles/StoreRole.kt` ... The declaration of a single role

## Forbidden contents

Group declarations. A group belongs to the group definition role, in `groups/`.
