package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the pages `katachiDocs` writes out of this very definition.
 *
 * A generated artifact that lives outside `build/` needs a role like anything else: the default
 * `files = gitTracked()` offers it to the check, and a directory no role claims is a violation.
 */
fun DeclarationContainerScope.generatedDocumentation() = "GeneratedDocumentation" {
    title = "Generated documentation"
    summary = "Markdown written out of this definition and committed to the repository"
    description = """
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
    """.trimIndent()
    forbiddenContents = """
        - Handwritten documentation. The `*.md` files under `docs/` are rebuilt on every generation,
          and pages this definition does not produce are deleted. Prose a person writes belongs in the
          root `README.md`
        - Resources other than `.md`. The current `layout { }` allows only `*.md`, so adding an image
          starts with rewriting the role
    """.trimIndent()
    example("docs/README.md", "The index of every page")
    example("docs/testing/Processor.md", "The page of a single role")
    example("docs/core/README.md", "The page of a single group")
    layout {
        "docs" {
            // The index, written on every run, so it is the one entry without a wildcard --
            // and therefore the one entry that is `required`. A `docs/` that was never
            // generated is reported here rather than passing as an empty allow list.
            "README.md".file()
            // `**` matches zero levels or more, so this one line covers the index, every
            // group's `README.md` and every role's page.
            "**" / "*.md".file()
        }
    }
}
