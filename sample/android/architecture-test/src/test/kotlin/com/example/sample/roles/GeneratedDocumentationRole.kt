package com.example.sample.roles

import com.example.sample.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the pages `katachiDocs` writes out of this very definition.
 *
 * The counterpart of [documentation]: that one is prose a person writes and this one is not
 * written by hand at all. They are separate roles rather than one "documents" bucket precisely
 * because that difference is the only thing worth knowing about either of them.
 */
fun DeclarationContainerScope.generatedDocumentation() = "GeneratedDocumentation" {
    title = "Generated documentation"
    summary = "Markdown written out from this definition and committed to the repository"
    description = """
        The Markdown that `./gradlew :architecture-test:katachiDocs` writes out from this
        definition itself. The output goes to `sample/android/docs`, set in `katachi {
        processors { docs { outputDir } } }`.

        Never written by hand. `katachiDocs` writes it, and any text added here disappears at
        the next generation. The place to fix is always the definition: the `title`, `summary`,
        `description` and `example` of a role or group become the page as they are.

        The opposite side of the `Documentation` role of the `tool` group (the root
        `README.md`). That one is prose people write, and takes on what does not come out of the
        definition (preparing the environment, why AGP cannot be raised, how to run the check).
        This one is never typed by a person. Both are "documentation", but where you may and may
        not edit differs so much that they are not merged into one role.

        It is placed outside `build/` although it is generated, so that someone who opens the
        repository can read it directly. In return it falls under the default `files =
        gitTracked()` check, which is why this role is needed. Removing the role makes
        `:architecture-test:test` fail with `[UnexpectedDirectory] docs`.

        CI checks whether it has gone stale with `--arg mode=check`. `mode=check` writes nothing
        and compares against what is on disk, failing with an exception on any difference, so
        pushing a definition change without regenerating turns `checkSampleAndroid` at the
        repository root red. To fix it locally, just run `katachiDocs` again (without `mode` it
        writes).

        `documented = true`. If a generated file were missing from the list, nowhere would say
        what this `docs/` is. The page of this role itself
        (`docs/testing/GeneratedDocumentation.md`) is generated too. This pairs with
        `Documentation` being `documented = false`, and is also why the two are not in the same
        group.

        `**` in `layout { }` matches zero or more levels, so one line covers the index
        `docs/README.md`, `docs/<group>/README.md` and `docs/<group>/<role>.md`. The line does
        not change if groups are nested. Only the index is written separately without a
        wildcard, and that one is `required`, so a state where nothing has been generated yet is
        found there.
    """.trimIndent()
    forbiddenContents = """
        - Hand-written documentation. The `*.md` files under `docs/` are rebuilt at every
          generation, and pages this definition does not produce are deleted
        - Resources other than `.md`. The current `layout { }` allows only `*.md`, so adding
          images starts with rewriting the role
    """.trimIndent()
    example("docs/README.md", "The index of all pages, and the list per group")
    example("docs/feature/Screen.md", "The page of one role")
    example("docs/feature/README.md", "The page of one group")
    layout {
        "docs" {
            // The index, written on every run, so it is the one entry without a wildcard --
            // and therefore the one entry that is `required`. A `docs/` that was never
            // generated is reported here rather than passing as an empty allow list.
            "README.md".file()
            // `**` matches zero levels or more, so this one line covers the index, every
            // group's `README.md` and every role's page -- and keeps covering them if a
            // group is ever nested inside another.
            "**" / "*.md".file()
        }
    }
}
