package me.tbsten.katachi.test.architecture.skill

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.katachiSkill() = "KatachiSkill".role {
    title = "katachi skill の本文"
    summary = "SKILL.md と、場面ごとに読む references の Markdown"
    description = """
        `SKILL.md` は入口と共通のルールだけを書き、場面ごとの手順は `references/<場面>/` に分けます。
        `references/setup/` は導入の手順書で、1 セクション 1 ファイル。`katachi agent guide` は、これを README の表の順につないで `katachi-script` に同梱したものを出します。
    """.trimIndent()
    example("katachi/SKILL.md", "どの reference を読むかと、共通のルール")
    example("katachi/references/setup/step3/3-2-write-the-definition.md", "導入の手順書の 1 セクション")
    example("katachi/references/add-and-update/add-a-role.md", "導入のあと、新しい種類のファイルに Role を足す手順")
    layout {
        "skills" / "katachi" {
            "SKILL.md".file()
            "references" / "*" / "*.md".file()
            "references" / "setup" / "*" / "*.md".file()
        }
    }
}
