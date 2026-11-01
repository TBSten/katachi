package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.projectDocument() = "ProjectDocument".role {
    title = "プロジェクト文書"
    summary = "リポジトリを開いた人が最初に読むもの"
    example("README.md", "katachi が何で、どう入れるか")
    example("CONTRIBUTING.md", "katachi 自体を直す人向けの案内")
    example("LICENSE", "ライセンス")
    layout {
        // None of them holds a wildcard, so all of them are required: deleting one is reported
        // as `[MissingFile]` instead of quietly passing. Writing the Japanese README out
        // rather than `"README*.md"` is what keeps that true of it too (that pattern would also
        // match `README.md` itself, now that a `*` may match nothing, and make both optional),
        // and the same goes for CONTRIBUTING.
        "README.md".file()
        "README.ja.md".file()
        "CONTRIBUTING.md".file()
        "CONTRIBUTING.ja.md".file()
        "LICENSE".file()
    }
}
