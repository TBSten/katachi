package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.apiDocsModule() = "ApiDocsModule".role {
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
        "katachi-konture" {
            description = "katachi-konture モジュールと、その公開パッケージそれぞれの説明"
            "Module.md".file()
        }
    }
}
