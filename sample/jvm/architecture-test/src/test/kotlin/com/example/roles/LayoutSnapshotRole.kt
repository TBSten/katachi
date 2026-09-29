package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the flattened form of this definition, kept as text so a change to it is read
 * as a diff.
 *
 * The sibling of [generatedDocumentation]: both are written by a machine out of this very
 * definition and both are committed rather than left in `build/`, because a generated artifact
 * nobody can open is worth nothing. Outside `build/` means inside `files = gitTracked()`, which
 * is why each of them has a role.
 */
fun DeclarationContainerScope.layoutSnapshot() = "LayoutSnapshot" {
    title = "Layout snapshot"
    summary = "A record of every line of this definition, flattened. It exists so people can review changes to the definition as a diff"
    description = """
        What `:architecture-test:test` writes out after flattening this definition, one entry per
        line. A line is a four-tuple of `<role qualifiedName>`, `<path>`, `<kind>` and
        `<required|optional>`, so what `layout { }` ends up allowing is laid out as is.

        Do not edit it by hand; `LayoutSnapshotSpec` writes it. To update it, just run
        `./gradlew :architecture-test:test -Dkatachi.snapshot.update=true`.

        It exists so that people can review changes to the definition as a diff. When a role is
        rewritten with `.module { }` or `mainSourceSet`, looking at the definition code does not
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
    """.trimIndent()
    forbiddenContents = """
        - Hand-written notes. They are overwritten wholesale by the next
          `-Dkatachi.snapshot.update=true`
        - Other kinds of records. The current `layout { }` accepts only the single file
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
