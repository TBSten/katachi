package com.example.sample.roles

import com.example.sample.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the flattened form of this definition, kept as text so a change to it is read
 * as a diff.
 *
 * The second of the machine-written roles, after [generatedDocumentation]. Both are produced
 * out of this very definition and both are committed rather than left in `build/`, because a
 * generated artifact nobody can open is worth nothing. Outside `build/` means inside
 * `files = gitTracked()`, which is why each of them needs a role of its own.
 */
fun DeclarationContainerScope.layoutSnapshot() = "LayoutSnapshot" {
    title = "Layout snapshot"
    summary = "A record of every line of this definition once flattened, so people can review changes to the definition as a diff"
    description = """
        What `:architecture-test:test` writes out from the flattened definition, one entry per
        line. A line is the four-tuple `<role qualifiedName>` `<path>` `<kind>`
        `<required|optional>`, so what `layout { }` finally allows is laid out as is.

        Not edited by hand. `LayoutSnapshotSpec` writes it. To update it, just run `./gradlew
        :architecture-test:test -Dkatachi.snapshot.update=true`.

        What it is for is letting people review changes to the definition as a diff. When roles
        spread over nine modules are rewritten with shorthand such as `featureSources()`, reading
        the definition code does not tell you whether it just became easier to read or the tree
        being checked itself changed. If the flattened result is kept as committed text, the
        number of lines in `git diff` is the answer: if no line moves, the rewrite is
        equivalent, and if one does, that line shows what changed and how.

        It exists for verifying katachi itself and is not something to write when adopting
        katachi. It is a record for katachi's developers to confirm that the meaning did not
        change when the sample's definition is edited.

        It is placed outside `build/` although it is generated, because showing a diff is its
        job. A record that is not committed appears in nobody's review. In return it falls under
        the default `files = gitTracked()` check, which is why this role is needed. Removing the
        role makes `:architecture-test:test` fail with `[UnexpectedDirectory] snapshots`.

        `documented` is not written (the default `true`). Nowhere else explains what this
        `snapshots/` is, and leaving it out of the list would leave a `.txt` of unknown origin
        for someone who opens the repository. It is treated like `GeneratedDocumentation`, the
        opposite of `Documentation` in the `tool` group being `documented = false`.
    """.trimIndent()
    forbiddenContents = """
        - Handwritten notes. The next `-Dkatachi.snapshot.update=true` overwrites the whole file
        - Other kinds of records. The current `layout { }` allows only the single file
          `snapshots/layout.txt`, so adding one starts with rewriting the role
    """.trimIndent()
    example("snapshots/layout.txt", "Every line of the flattened layout")
    layout {
        "snapshots" {
            // Written out by name rather than as `*.txt`, so the entry carries no wildcard
            // and is therefore `required`. A snapshot that was deleted is reported as
            // `[MissingFile]` here instead of passing as an empty allow list.
            "layout.txt".file()
        }
    }
}
