package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the pages `katachiDocs` writes out of this very definition.
 *
 * A generated artifact that lives outside `build/` needs a role like anything else: the default
 * `files = gitTracked()` offers it to the check, and a directory no role claims is a violation.
 * Declaring it is what buys the pages a place in the repository, where a reader can find them.
 */
fun DeclarationContainerScope.generatedDocumentation() = "GeneratedDocumentation" {
    title = "Generated documentation"
    summary = "Markdown written out of this definition and committed to the repository"
    description = """
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
    """.trimIndent()
    forbiddenContents = """
        - Hand-written documentation. Every `*.md` under `docs/` is recreated at each generation,
          and pages this definition does not produce are deleted. Prose written by people belongs
          on the `tool` group side, next to `.gitignore`
        - Resources other than `.md`. The current `layout { }` accepts only `*.md`, so adding an
          image starts with rewriting the role
    """.trimIndent()
    example("docs/README.md", "The index of all pages, and the list for each group")
    example("docs/api/Controller.md", "The page of a single role")
    example("docs/api/README.md", "The page of a single group")
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
