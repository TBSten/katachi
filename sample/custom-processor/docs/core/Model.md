[Custom processor sample](../README.md) / [Application](README.md)

# Model

The values the application handles. Holds data classes, enums and value objects

The values the application handles. `Note` is a data class with a `title` and a `body`; the
`NoteStore` creates it and `main()` prints it as it is.

A name says what the value is, with no suffix (`Note`, not `NoteModel`). Only the `.kt` files
directly in the `model` package are covered; a directory dug below it does not belong to this
role.

## Placement

| Module | Path | When to use |
|---|---|---|
| `:` | `src/main/kotlin/**/model/*.kt` |  |

## Examples

- `Note` ... A note with a title and a body

## Allowed contents

Data classes, enums, value objects and the computation confined to those values belong here.

## Forbidden contents

- Fetching or saving. I/O belongs to the store role. If a model knew where it is saved, adding
  a single value would mean reading about saving as well
- Dependencies on external libraries. The model in this sample knows only Kotlin's standard
  library
