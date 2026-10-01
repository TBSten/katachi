[katachi-sample-kmp](../README.md) / [Testing support](README.md)

# Layout snapshot

A record of this definition, flattened and written out in full. It exists so a person can review changes to the definition as a diff

What `:architecture-test:test` writes when it flattens this definition, one entry per
line. A line is a quadruple of `<role qualifiedName>`, `<path>`, `<kind>` and
`<required|optional>`, so what `layout { }` ends up allowing is laid out as it is.

Do not edit it by hand; `LayoutSnapshotSpec` writes it. To update it, just run
`./gradlew :architecture-test:test -Dkatachi.snapshot.update=true`.

It exists so a person can review changes to the definition as a diff. This sample uses
wildcard directories such as `"feature" / "*"` and sourceSet sugar more than
anything, and looking at the definition code alone does not tell you whether a rewrite
only made it easier to read or changed the checked tree itself. If the flattened result is
committed as text, the number of lines in `git diff` is the answer: if no line moves, the
rewrite is equivalent, and if one moves, that line shows what changed and how.

It is here for katachi's own verification, not something you write when adopting
katachi. It is a record with which katachi's developers confirm that the meaning has not
changed when the sample's definition is touched.

It sits outside `build/` even though it is generated, because its job is to show up in
diffs. Since `.gitignore` drops `build/`, a record written there would appear in nobody's
review. In exchange for being outside, it falls under the default `files = gitTracked()`
check, which is why this role is needed. Deleting the role makes
`:architecture-test:test` fail with `[UnexpectedDirectory] snapshots`.

`documented` is not written (the default `true`). Nothing else explains what this
`snapshots/` is, and leaving it out of the list would leave one `.txt` whose origin a
reader of the repository cannot tell. The page of this role itself
(`docs/testing/LayoutSnapshot.md`) is generated too.

## Placement

| Path | When to use |
|---|---|
| `snapshots/layout.txt` |  |

## Examples

- `snapshots/layout.txt` ... Every line of the flattened layout

## Forbidden contents

- Handwritten notes. The next `-Dkatachi.snapshot.update=true` overwrites everything
- Other kinds of records. The current `layout { }` allows only the one file
  `snapshots/layout.txt`, so adding one starts with rewriting the role
