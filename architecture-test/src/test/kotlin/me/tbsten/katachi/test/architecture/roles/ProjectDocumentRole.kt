package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the documents a reader opens first.
 *
 * Not under `docs/` like the other two roles of this group, and deliberately so: a group is a
 * set of roles that belong together, not a directory. README and LICENSE are the first
 * documents a reader opens, which puts them here rather than with the tooling in `tool` — that
 * group is machine-facing (`.github`, `.claude`, `.gitignore`) and is kept out of the
 * generated documentation, which is the wrong place for the page everyone reads first.
 */
fun DeclarationContainerScope.projectDocument() = "ProjectDocument" {
    title = "プロジェクト文書"
    summary = "リポジトリを開いた人が最初に読むもの"
    example("README.md", "katachi が何で、どう入れるか")
    example("LICENSE", "ライセンス")
    layout {
        // None of them holds a wildcard, so all three are required: deleting one is reported
        // as `[MissingFile]` instead of quietly passing. Writing the Japanese README out
        // rather than `"README*.md"` is what keeps that true of it too.
        "README.md".file()
        "README.ja.md".file()
        "LICENSE".file()
    }
}
