package me.tbsten.katachi.test.architecture.skill

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.katachiSkillAsset() = "KatachiSkillAsset".role {
    title = "katachi skill のテンプレート"
    summary = "導入の手順で作業用ディレクトリに写す、チェックリストとレポートの HTML"
    description = """
        データは HTML の中の `<script type="application/json">` に持ち、残りは表示するだけの殻です。
        AI エージェントは手順 0-2 で `assets/<言語>/` から写し、JSON だけを書き換えます（手順 0-3）。
        `katachi agent test` などは、写したチェックリストの `meta`・`preferences` と `<html lang>` を読みます。
    """.trimIndent()
    example("katachi/assets/ja/check-list.html", "導入の進み具合と、利用者への質問を持つチェックリスト")
    example("katachi/assets/en/project-code-base-report.html", "手順 1 で書き込むコードベースのレポート")
    layout {
        "skills" / "katachi" / "assets" / "*" / "*.html".file()
    }
}
