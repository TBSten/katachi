package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the pages `katachiDocs` writes out of this very definition.
 *
 * A generated artifact kept outside `build/` is an ordinary file as far as the check is
 * concerned: `files` is left at the default `gitTracked()`, so the pages are offered to it and a
 * directory no role claims is a violation. Declaring the role is what buys them a place in the
 * repository, where a reader finds them without running anything.
 */
fun DeclarationContainerScope.generatedDocumentation() = "GeneratedDocumentation" {
    title = "Generated documentation"
    summary = "Markdown written from this definition and committed to the repository"
    description = """
        The Markdown that `./gradlew :architecture-test:katachiDocs` writes out of this very
        definition. The output goes to `sample/kmp/docs` through
        `katachi { processors { docs { outputDir } } }`.

        Do not write it by hand; `katachiDocs` writes it. Text added here is erased by the next
        generation. The place to fix is always the definition: the `title`, `summary`,
        `description` and `example` of a role or group become the page as they are.

        It sits outside `build/` even though it is generated, so that someone who opens the
        repository can read it as it is. `.gitignore` drops `build/`, so pages written to
        `build/` would never be seen by anyone. In exchange for being outside, it falls under
        the `files = gitTracked()` check, which is why this role is needed. Deleting the role
        makes `:architecture-test:test` fail with `[UnexpectedDirectory] docs`.

        CI checks whether it is stale with `--arg mode=check`. `mode=check` writes nothing,
        compares against what is on disk and throws on a mismatch, so changing the definition and
        pushing without regenerating turns `checkSampleKmp` at the repository root red. To fix it
        locally, just run `katachiDocs` again (without `mode` it writes).

        It is `documented = true`. If a generated role did not show in the list, nothing would
        say what this `docs/` is. The page of this role itself
        (`docs/testing/GeneratedDocumentation.md`) is generated too.

        `**` in `layout { }` matches zero or more levels, so the index `docs/README.md`,
        `docs/<group>/README.md` and `docs/<group>/<role>.md` are all covered by one line. That
        line does not change even if groups are nested. Only the index is written separately
        without a wildcard, and it is `required`, so a state where nothing was ever generated is
        found there.
    """.trimIndent()
    forbiddenContents = """
        - Handwritten documents. The `*.md` under `docs/` are recreated on every generation,
          and pages this definition does not produce are deleted
        - Resources other than `.md`. The current `layout { }` allows only `*.md`, so adding an
          image starts with rewriting the role
    """.trimIndent()
    example("docs/README.md", "The index of all pages and the list per group")
    example("docs/ui/Component.md", "The page of one role")
    example("docs/ui/README.md", "The page of one group")
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
