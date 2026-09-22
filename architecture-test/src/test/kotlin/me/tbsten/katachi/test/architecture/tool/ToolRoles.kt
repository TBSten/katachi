package me.tbsten.katachi.test.architecture.tool

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * The roles of the tooling around the project that is not the build itself.
 *
 * Like the build group, these are checked but kept out of the generated documentation.
 *
 * ## `.idea/` deliberately has no role
 *
 * `.idea/dictionaries/project.xml` is tracked by git, so `files = gitTracked()` offers it —
 * and yet declaring it would *create* a violation rather than remove one. `Scan.visitDirectory`
 * tests `FOREIGN_DIRECTORY_NAMES` (`.git`, `.gradle`, `.idea`) before it consults the layout
 * at all and returns, so the walk never reaches anything below `.idea/`. A declared file the
 * walk never visits is reported as `[MissingFile]`.
 *
 * Not declaring it is therefore the right answer here, and the gap is katachi's, not this
 * definition's: a project with tracked `.idea/` files has no way to declare them today.
 * Filed as a TODO against katachi rather than papered over.
 */
fun ArchitectureScope.toolRoles() {
    "tool".group {
        documented = false
        title = "ツール設定"

        "Ci" {
            title = "CI"
            summary = "GitHub Actions のワークフローと、そこから呼ばれるスクリプト"
            example("ci.yml", "check と3つのサンプルビルドを回す")
            example("check-kotlin-versions.sh", "サンプルとルートの Kotlin バージョンの一致を見る")
            layout {
                ".github" {
                    "workflows" / "*.yml".file()
                    "scripts" / "*.sh".file()
                }
            }
        }

        "AgentRule" {
            title = "エージェント設定"
            summary = "Claude Code が読むルールとスキル"
            example("rules/kotlin.md", "docs/internal/kotlin/ の規約を読ませる入口")
            example("skills/verify-changes/SKILL.md", "変更後に何を回すかの手順")
            layout {
                ".claude" {
                    "rules" / "*.md".file()
                    "skills" / "*" / "SKILL.md".file()
                }
            }
        }

        "Git" {
            title = "Git 設定"
            summary = "バージョン管理の設定ファイル"
            example(".gitignore", "生成物と手元の設定を管理対象から外す")
            layout {
                ".gitignore".file()
            }
        }

        "ProjectDocument" {
            title = "プロジェクト文書"
            summary = "リポジトリを開いた人が最初に読むもの"
            example("README.md", "katachi が何で、どう入れるか")
            example("LICENSE", "ライセンス")
            layout {
                // Neither holds a wildcard, so both are required: deleting one is reported as
                // `[MissingFile]` instead of quietly passing.
                "README.md".file()
                "LICENSE".file()
            }
        }
    }
}
