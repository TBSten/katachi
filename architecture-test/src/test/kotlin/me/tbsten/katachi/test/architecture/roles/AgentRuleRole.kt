package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of what Claude Code reads before it touches this repository. */
fun DeclarationContainerScope.agentRule() = "AgentRule" {
    title = "エージェント設定"
    summary = "Claude Code が読むルールとスキル"
    example("rules/kotlin.md", "docs/internal/kotlin/ の規約を読ませる入口")
    example("skills/verify-changes/SKILL.md", "変更後に何を回すかの手順")
    example("skills/translate-ja-en/scripts/list-targets.py", "スキルが呼ぶ補助スクリプト")
    example("skills/translate-ja-en/target-file-pattern.csv", "スキルが読むデータ（翻訳の対象の組）")
    layout {
        ".claude" {
            "rules" / "*.md".file()
            "skills" / "*" / "SKILL.md".file()
            // What a skill keeps beside its SKILL.md: data its steps read, such as the one list of
            // file pairs translate-ja-en works from.
            "skills" / "*" / "*".file()
            // A skill may bring the scripts its steps run. They sit next to the SKILL.md that
            // tells an agent when to run them, rather than somewhere the agent has to be told about.
            "skills" / "*" / "scripts" / "*".file()
        }
    }
}
