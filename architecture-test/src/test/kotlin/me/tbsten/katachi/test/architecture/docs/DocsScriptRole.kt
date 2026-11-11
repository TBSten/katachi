package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.docsScript() = "DocsScript".role {
    title = "ドキュメントサイトのビルド結果を扱うスクリプト"
    summary = "サイトのビルド結果を検査・後処理するスクリプト（シェルと Node）"
    example("check-no-dev-tools-in-dist.sh", "本番の出力に dev のツール（フィードバックやメモ）が入っていないかを見る")
    example("add-dev-version-option.mjs", "公開サイトの root の版の切り替えに開発版の選択肢を足す")
    layout {
        "docs" / "scripts" / "*.sh".file()
        "docs" / "scripts" / "*.mjs".file()
    }
}
