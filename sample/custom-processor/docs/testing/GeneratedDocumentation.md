[Custom processor sample](../README.md) / [Definition and processors](README.md)

# Generated documentation

Markdown written out of this definition and committed to the repository

Markdown that `./gradlew :architecture-test:katachiDocs` writes out of this very definition.
The output directory is pointed at `sample/custom-processor/docs` by
`katachi { processors { docs { outputDir } } }`.

`docs` is a processor katachi registers from the start, so it comes from a different place
than the three this sample wrote. It can be called through a `katachi<Key>` task of the same
shape because, as an `ArchitectureProcessor`, a processor of your own and one of katachi's are
the same thing.

Never write this by hand; `katachiDocs` writes it. Prose added here disappears on the next
generation. The place to fix is always the definition: the `title`, `summary`, `description`
and `example` of a role or a group become the page as they are.

It sits outside `build/` even though it is generated, so that whoever opens the repository can
read it as it is. In exchange it falls under the default `files = gitTracked()` check, which is
why this role is needed. Removing the role makes `:architecture-test:test` fail with
`[UnexpectedDirectory] docs`.

CI checks that it is not stale with `--arg mode=check`. `mode=check` writes nothing, compares
against what is on disk and fails with an exception on any mismatch, so pushing after changing
the definition without regenerating turns the repository root's `checkSampleCustomProcessor`
red. To fix it locally, just run `katachiDocs` again (without `mode` it writes).

The `**` in `layout { }` matches zero or more levels, so one line covers the index
`docs/README.md`, `docs/<group>/README.md` and `docs/<group>/<role>.md`. Only the index is
written separately without a wildcard, and that entry is `required`. A state where nothing has
ever been generated is found there.

## Placement

| Path | When to use |
|---|---|
| `docs/README.md` |  |
| `docs/**/*.md` |  |

## Examples

- `docs/README.md` ... The index of every page
- `docs/testing/Processor.md` ... The page of a single role
- `docs/core/README.md` ... The page of a single group

## Forbidden contents

- Handwritten documentation. The `*.md` files under `docs/` are rebuilt on every generation,
  and pages this definition does not produce are deleted. Prose a person writes belongs in the
  root `README.md`
- Resources other than `.md`. The current `layout { }` allows only `*.md`, so adding an image
  starts with rewriting the role
