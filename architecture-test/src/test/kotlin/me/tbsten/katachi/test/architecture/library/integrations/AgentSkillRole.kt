package me.tbsten.katachi.test.architecture.library.integrations

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.agentSkillModule() = "AgentSkill".role {
    title = "katachi skill"
    summary = "利用者が入れる Agent Skill。SKILL.md と、場面ごとの references、導入で写すテンプレート"
    description = """
        `SKILL.md` は入口と共通のルールだけを書き、場面ごとの手順は `references/<場面>/` に分けます。
        `references/setup/` は導入の手順書で、1 セクション 1 ファイル。README の表が読む順番です。
    """.trimIndent()
    example("katachi/SKILL.md", "どの reference を読むかと、共通のルール")
    example("katachi/references/setup/step3/3-2-write-the-definition.md", "導入の手順書の 1 セクション")
    example("katachi/references/add-and-update/add-a-role.md", "導入のあと、新しい種類のファイルに Role を足す手順")
    example("katachi/assets/ja/check-list.html", "導入の進み具合と、利用者への質問を持つチェックリスト")
    example("katachi/assets/en/check-list.html", "英語のチェックリスト。手順 1 で書き込むコードベース レポートも同じページに持つ")
    layout {
        "skills" / "katachi" {
            "SKILL.md".file()
            "references" / "*" / "*.md".file()
            "references" / "setup" / "*" / "*.md".file()
            "assets" / "*" / "*.html".file()
        }
    }
}
