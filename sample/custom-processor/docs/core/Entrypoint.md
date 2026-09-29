[Custom processor sample](../README.md) / [Application](README.md)

# Entrypoint

Starting the process. The only file that has `main()`

The place where `main()` is written. In this sample it only creates a `NoteStore`, calls
`all()` and prints the result line by line, with no branching and no configuration.

The application is deliberately split into three roles because this sample is about
processors. With a single role, `RoleFileCount` would count one target and `RoleTable` would
list one row, and nothing could be read from the processors' output. The split is not
something the size of the application called for.

The `layout { }` of this role has no wildcard, so it requires exactly one `Main.kt`. Deleting
it raises `[MissingFile]`.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/kotlin/**/Main.kt` |  |

## Examples

- `Main.kt` ... Where the process starts

## Allowed contents

Only starting the process and assembling the layers belongs here.

## Forbidden contents

- The values themselves. The contents of the notes belong to the store role. If they are
  written directly in `main()`, the answer to "where does the data come from?" moves into the
  entrypoint
- The shape of a value. The definition of `Note` belongs to the model role
