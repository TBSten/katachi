package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the recorded layout snapshot, which `LayoutSnapshotSpec` writes and compares.
 *
 * The snapshot lives inside the sample, so the allow list has to claim it like anything else
 * that is generated and not under `build/`. This is the same arrangement `GeneratedDocumentation`
 * has, which is why the two sit next to each other.
 */
fun DeclarationContainerScope.layoutSnapshot() = "LayoutSnapshot" {
    title = "Layout snapshot"
    summary = "Text recording the flattened result of `layout { }`, for katachi's own self-verification"
    description = """
        Text that flattens every `layout { }` of this definition and lists "role, path, kind and
        whether it is required" one entry per line. `LayoutSnapshotSpec` rebuilds it each time and
        compares it against the recorded content.

        It exists to show that what is checked has not changed when the way a role is written changes.
        As long as this diff stays empty, rewriting to sugar (`":".module { }` or `mainSourceSet`) is
        safe.

        Never write this by hand; `LayoutSnapshotSpec` writes it. To update it after an intended
        change, run `./gradlew :architecture-test:test -Dkatachi.snapshot.update=true`. The same
        command is written in the comment at the top of the file.

        A project adopting katachi does not need it. It is a device for katachi itself to see that it
        has not broken the sample, and its purpose differs from that of `ProjectArchitectureTest`.
    """.trimIndent()
    forbiddenContents = """
        - Expected values written by hand. When a diff appears, the fix is either on the definition
          side or a regeneration of the snapshot. Editing the text directly to make things agree
          defeats the sentinel
        - Other kinds of records. The current `layout { }` allows only the single file
          `snapshots/layout.txt`, so adding one starts with rewriting the role
    """.trimIndent()
    example("snapshots/layout.txt", "The full text of the flattened layout")
    layout {
        "snapshots" {
            // Written out by name rather than as `*.txt`, so the entry carries no wildcard
            // and is therefore `required`. A snapshot that was deleted is reported as
            // `[MissingFile]` here instead of passing as an empty allow list.
            "layout.txt".file()
        }
    }
}
