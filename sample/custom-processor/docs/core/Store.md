[Custom processor sample](../README.md) / [Application](README.md)

# Store

Takes responsibility for where values come from. For now, fixed values in memory

The place that takes responsibility for where models are fetched from. `NoteStore` only
returns two fixed notes, but it draws the boundary that the entrypoint and the model do not
change even if the storage becomes a file or a database.

`layout { }` allows the `.kt` files directly in the `store` package. File names are not
restricted, so the `*Store` convention exists only in this text and is not rejected
mechanically.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/kotlin/**/store/*.kt` |  |

## Examples

- `NoteStore` ... The list of notes in memory

## Allowed contents

Fetching and saving, and the details of the source (connection, path, serialization), belong
here. Names are aligned as `*Store` and placed directly in the `store` package.

## Forbidden contents

- Output. `println` is the entrypoint's job. If the store also handled display, there would be
  nothing to swap in a test
- Definitions of values. `Note` belongs to the model role
