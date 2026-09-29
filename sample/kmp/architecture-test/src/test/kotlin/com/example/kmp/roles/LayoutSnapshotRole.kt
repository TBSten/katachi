package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the flattened form of this definition, kept as text so a change to it is read
 * as a diff.
 *
 * The sibling of [generatedDocumentation]: both are written by a machine out of this very
 * definition and both are committed rather than left in `build/`, because a generated artifact
 * nobody can open is worth nothing. Outside `build/` means inside `files = gitTracked()`, which
 * is why each of them needs a role of its own.
 */
fun DeclarationContainerScope.layoutSnapshot() = "LayoutSnapshot" {
    title = "Layout snapshot"
    summary = "A record of this definition, flattened and written out in full. It exists so a person can review changes to the definition as a diff"
    description = """
        What `:architecture-test:test` writes when it flattens this definition, one entry per
        line. A line is a quadruple of `<role qualifiedName>`, `<path>`, `<kind>` and
        `<required|optional>`, so what `layout { }` ends up allowing is laid out as it is.

        Do not edit it by hand; `LayoutSnapshotSpec` writes it. To update it, just run
        `./gradlew :architecture-test:test -Dkatachi.snapshot.update=true`.

        It exists so a person can review changes to the definition as a diff. This sample uses
        wildcard module keys such as `":feature:*".module { }` and sourceSet sugar more than
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
    """.trimIndent()
    forbiddenContents = """
        - Handwritten notes. The next `-Dkatachi.snapshot.update=true` overwrites everything
        - Other kinds of records. The current `layout { }` allows only the one file
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
