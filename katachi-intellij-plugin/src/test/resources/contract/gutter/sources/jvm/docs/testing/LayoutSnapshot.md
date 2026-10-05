[Ktor sample app](../README.md) / [Testing](README.md)

# Layout snapshot

A record of every line of this definition, flattened. It exists so people can review changes to the definition as a diff

What `:architecture-test:test` writes out after flattening this definition, one entry per
line. A line is a four-tuple of `<role qualifiedName>`, `<path>`, `<kind>` and
`<required|optional>`, so what `layout { }` ends up allowing is laid out as is.

Do not edit it by hand; `LayoutSnapshotSpec` writes it. To update it, just run
`./gradlew :architecture-test:test -Dkatachi.snapshot.update=true`.

It exists so that people can review changes to the definition as a diff. When a role is
rewritten with `mainSourceSet` or `testSourceSet`, looking at the definition code does not
tell you whether it merely got easier to read or whether the tree being checked has itself
changed. If the flattened result is kept as committed text, the lines in `git diff` are
the answer. If no line moves, the rewrite is equivalent; if lines move, they show what
changed and how.

It is here for katachi's own verification, not something you write when adopting katachi.
It is a record that lets katachi's developers confirm that the meaning did not change when
the sample's definition was touched.

It is generated, yet kept outside `build/` because its job is to be shown as a diff. A
record that is not committed appears in nobody's review. In exchange, it falls under the
default `files = gitTracked()` check, so this role is needed. Removing the role makes
`:architecture-test:test` fail with `[UnexpectedDirectory] snapshots`.

`documented` is not written (the default `true`). There is nowhere else that explains what
this `snapshots/` is, and taking it off the list would leave a `.txt` of unknown origin
for anyone who opens the repository. This role's own page
(`docs/testing/LayoutSnapshot.md`) is generated as well.

## Placement

| Path | When to use |
|---|---|
| `snapshots/layout.txt` |  |

## Examples

- `snapshots/layout.txt` ... Every line of the flattened layout

## Forbidden contents

- Hand-written notes. They are overwritten wholesale by the next
  `-Dkatachi.snapshot.update=true`
- Other kinds of records. The current `layout { }` accepts only the single file
  `snapshots/layout.txt`, so adding one starts with rewriting the role
