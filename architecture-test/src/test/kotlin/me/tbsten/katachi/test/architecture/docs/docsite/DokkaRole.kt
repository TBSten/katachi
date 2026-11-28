package me.tbsten.katachi.test.architecture.docs.docsite

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.dokka() = "Dokka".role {
    title = "API リファレンス"
    summary = "ドキュメントサイトがそのまま配る public/ と、Dokka が書き出す API リファレンス（git には入らない）"
    layout {
        // Served as they are, and `docs/public/<lang>/api-docs/` is where Dokka writes its output, so what is inside is not listed.
        "docs" / "public".ignore()
    }
}
