package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.apiDocsModule() = "ApiDocsModule".role {
    title = "API リファレンスのモジュール説明"
    summary = "Dokka の includes に渡す Module.md。モジュール自身と、公開パッケージそれぞれの概要を持つ"
    description = """
        `Module.md` は、Dokka の `includes` に渡すパッケージ概要の唯一の置き場です。Kotlin にはパッケージ自身の KDoc を書く場所がありません。
        `Module.md` を持つ 4 モジュール（katachi・katachi-konsist・katachi-konture・katachi-script）を 1 本ずつ宣言しているので、どれかの `Module.md` が消えると `MissingFile` になります。`Module.md` を足したモジュールは、ここにも足します。
    """.trimIndent()
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
        "katachi-script" {
            description = "katachi-script モジュールと、その公開パッケージの説明"
            "Module.md".file()
        }
    }
}
