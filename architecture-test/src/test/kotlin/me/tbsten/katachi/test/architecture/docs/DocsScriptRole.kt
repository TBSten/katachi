package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.docsScript() = "DocsScript".role {
    title = "ドキュメントサイトの検査スクリプト"
    summary = "サイトのビルド結果を検査するシェルスクリプト"
    example("check-no-dev-tools-in-dist.sh", "本番の出力に dev の道具（フィードバックやメモ）が入っていないかを見る")
    layout {
        "docs" / "scripts" / "*.sh".file()
    }
}
