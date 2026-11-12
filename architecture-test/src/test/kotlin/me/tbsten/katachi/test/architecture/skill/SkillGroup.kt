package me.tbsten.katachi.test.architecture.skill

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.skillGroup() = "Skill".group {
    title = "katachi skill"
    description = """
        利用者が `gh skill install TBSten/katachi katachi` で入れる Agent Skill。導入の手順書・定義の追加と更新・違反の直し方を持ちます。
        gh skill も npx skills も `skills/<名前>/SKILL.md` を探すので、リポジトリ直下の `skills/` に置いています。
        リポジトリの開発に使う `.claude/skills/` とは別物です。
    """.trimIndent()

    katachiSkill()
    katachiSkillAsset()
}
