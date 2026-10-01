package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of `Module.md`, one per published module.
 *
 * `katachi-kotlin-library` passes it to Dokka's `includes`, which is the only place a package
 * can carry a summary: katachi has no `.internal` package suppressed from the reference that
 * needs one, but every other public package does, since there is nowhere in Kotlin to write a
 * package's own KDoc. Neither file holds a wildcard, so a module published without one is
 * reported rather than quietly generating a reference with empty package summaries.
 */
fun DeclarationContainerScope.apiDocsModule() = "ApiDocsModule" {
    title = "API リファレンスのモジュール説明"
    summary = "Dokka の includes に渡す Module.md。モジュール自身と、公開パッケージそれぞれの概要を持つ"
    example("katachi/Module.md", "# Module katachi の段落と、公開パッケージごとの # Package 見出し")
    layout {
        "katachi" {
            description = "katachi 自身のモジュールと、その公開パッケージそれぞれの説明"
            "Module.md".file()
        }
        "katachi-konsist" {
            description = "katachi-konsist モジュールと、その公開パッケージそれぞれの説明"
            "Module.md".file()
        }
    }
}
