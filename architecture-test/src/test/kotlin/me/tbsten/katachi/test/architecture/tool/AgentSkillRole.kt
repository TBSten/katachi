package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.agentSkill() = "AgentSkill".role {
    title = "開発用のエージェントスキル"
    summary = "このリポジトリを開発する Claude Code が使う `.claude/skills/`"
    description = """
        利用者が入れる `skills/katachi/`（`Skill` group）とは別物です。こちらはこのリポジトリの開発の手順だけを持ちます。
    """.trimIndent()
    example("skills/verify-changes/SKILL.md", "変更後に何を回すかの手順")
    example("skills/translate-ja-en/scripts/list-targets.py", "スキルが呼ぶ補助スクリプト")
    example("skills/translate-ja-en/target-file-pattern.csv", "スキルが読むデータ（翻訳の対象の組）")
    example("skills/prerelease/decisions/docs.md", "前の回で人が下した判断（次の回で同じことを聞かないため）")
    layout {
        ".claude" {
            "skills" / "*" / "SKILL.md".file()
            // What a skill keeps beside its SKILL.md: data its steps read, such as the one list of
            // file pairs translate-ja-en works from.
            "skills" / "*" / "*".file()
            // A skill may bring the scripts its steps run. They sit next to the SKILL.md that
            // tells an agent when to run them, rather than somewhere the agent has to be told about.
            "skills" / "*" / "scripts" / "*".file()
            // The details of each step, split out so that SKILL.md stays a short outline.
            "skills" / "*" / "references" / "*.md".file()
            // A step whose details differ per component keeps one file for each beside them, such as
            // prerelease's references/exploratory/<component>.md (the charter each exploratory session follows).
            "skills" / "*" / "references" / "*" / "*.md".file()
            // The HTML a skill fills in for its report, such as prerelease's index.html and TODO.html.
            "skills" / "*" / "templates" / "*.html".file()
            // What a person decided on earlier runs of a skill, such as prerelease's decisions/, so that the
            // next run reads them and does not ask the same thing again.
            "skills" / "*" / "decisions" / "*.md".file()
        }
    }
}
