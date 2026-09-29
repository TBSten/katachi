[Ktor sample app](../README.md) / [Testing](README.md)

# Generated documentation

Markdown written out of this definition and committed to the repository

The Markdown that `./gradlew :architecture-test:katachiDocs` writes out of this very
definition. The output destination is pointed at `sample/jvm/docs` by
`katachi { processors { docs { outputDir } } }`.

Do not write it by hand; `katachiDocs` writes it. Any text added here is erased by the next
generation. The place to fix is always the definition, and a role's or group's `title`,
`summary`, `description` and `example` become the pages as they are.

It is generated, yet kept outside `build/` so that anyone who opens the repository can read
it as is. In exchange, it falls under the default `files = gitTracked()` check, which is
why this role is needed. Removing the role makes `:architecture-test:test` fail with
`[UnexpectedDirectory] docs`.

CI looks for staleness with `--arg mode=check`. `mode=check` writes nothing and compares
against what is on disk, failing with an exception on a mismatch, so pushing a changed
definition without regenerating turns `checkSampleJvm` at the repository root red. To fix
it locally, just run `katachiDocs` again (without `mode`, it writes).

It is set to `documented = true`. Even for generated output, if it were missing from the
list, nothing anywhere would say what this `docs/` is. This role's own page
(`docs/testing/GeneratedDocumentation.md`) is generated as well.

`**` in `layout { }` matches zero or more levels, so a single line covers the index
`docs/README.md`, `docs/<group>/README.md` and `docs/<group>/<role>.md`. The line does not
change even if groups are nested. The index alone is written separately without a
wildcard, and that entry is `required`: a state where nothing has ever been generated is
found there.

## Placement

| Module | Path | When to use |
|---|---|---|
|  | `docs/README.md` |  |
|  | `docs/**/*.md` |  |

## Examples

- `docs/README.md` ... The index of all pages, and the list for each group
- `docs/api/Controller.md` ... The page of a single role
- `docs/api/README.md` ... The page of a single group

## Forbidden contents

- Hand-written documentation. Every `*.md` under `docs/` is recreated at each generation,
  and pages this definition does not produce are deleted. Prose written by people belongs
  on the `tool` group side, next to `.gitignore`
- Resources other than `.md`. The current `layout { }` accepts only `*.md`, so adding an
  image starts with rewriting the role
