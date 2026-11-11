package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/** `lint`: the Role check of the guide's step 3. It never fails; it only reports (KATACHI-266). */
class LintCommandSpec : FreeSpec({
    "定義のディレクトリが無ければ scaffold を案内して止まる" {
        val run = runCapturing(createTempDirectory("katachi-project-"), listOf("lint"))
        run.status shouldBe 1
        run.err shouldContain "定義のディレクトリがありません"
        run.err shouldContain "architecture-test/src/test/kotlin"
    }

    "気になる Role が無ければ走査した件数だけを出す（コメントと文字列の中は数えない）" {
        val project = projectWithDefinition(
            "CleanRole.kt" to """
                val clean = "Clean".role {
                    layout {
                        "src" / "main" / "Clean".ktFile()
                    }
                }
                // "Ghost".role { layout { "README.md".file(); "build.gradle.kts".file() } }
                val text = ""${'"'}
                    "Ghost2".role { layout { "README.md".file() } }
                ""${'"'}
            """.trimIndent(),
        )
        val run = runCapturing(project, listOf("lint"))
        run.status shouldBe 0
        run.out shouldContain "Role の点検: 種類の違うファイルを抱えていそうな Role は見つかりませんでした（1 件を走査）。"
    }

    "種類の違うファイルを抱える Role と、手書きで宣言した Gradle のファイルを指摘し、失敗にはしない" {
        val project = projectWithDefinition(
            "MixedRole.kt" to """
                val mixed = "Mixed".role {
                    layout {
                        "README.md".file()
                        "build.gradle.kts".file()
                    }
                }
            """.trimIndent(),
        )
        val run = runCapturing(project, listOf("lint"))
        run.status shouldBe 0
        run.out shouldContain "種類の違うファイルを1つの Role に抱えていそうなものが 1 件あります（1 件を走査）。"
        run.out shouldContain "MixedRole.kt:1 Role \"Mixed\""
        run.out shouldContain "種類の違うファイルが同居しています: README（README.md） / build.gradle(.kts)（build.gradle.kts）"
        run.out shouldContain "Gradle のファイルを手書きの Role で宣言しています（Role: Mixed）。"
    }

    "制約を付けた Role の Gradle のファイルは、移す先の hook を名指しする" {
        val project = projectWithDefinition(
            "BuildRole.kt" to """
                val build = "Build".role {
                    layout {
                        "build.gradle.kts".file()
                    }
                    fileConstraint("uses the catalog") { }
                }
            """.trimIndent(),
        )
        val run = runCapturing(project, listOf("lint"))
        run.status shouldBe 0
        run.out shouldContain "Gradle のファイルを手書きの Role で宣言し、その Role に制約を付けています:"
        run.out shouldContain "Role \"Build\"（build.gradle.kts → gradle { buildScript { } }）"
        run.out shouldNotContain "Gradle のファイルを手書きの Role で宣言しています（Role:"
    }

    "buildSrc の Gradle のファイルは gradle() の対象外なので数えない" {
        val project = projectWithDefinition(
            "BuildSrcRole.kt" to """
                val buildSrc = "BuildSrcScript".role {
                    layout {
                        "buildSrc" / "build.gradle.kts".file()
                    }
                }
            """.trimIndent(),
        )
        val run = runCapturing(project, listOf("lint"))
        run.out shouldNotContain "Gradle のファイルを手書きの Role"
    }

    "本体とテストのファイルを同じ Role で宣言していれば指摘する" {
        val project = projectWithDefinition(
            "RepositoryRole.kt" to """
                val repository = "Repository".role {
                    layout {
                        "src" / "main" / "Repository".ktFile()
                        "src" / "test" / "RepositoryTest".ktFile()
                    }
                }
            """.trimIndent(),
        )
        val run = runCapturing(project, listOf("lint"))
        run.out shouldContain "本体（main）とテスト（test）のファイルを同じ Role で宣言しています"
    }

    "ツールごとの設定ディレクトリを anyFile() でまとめていれば指摘する" {
        val project = projectWithDefinition(
            "ToolConfigRole.kt" to """
                val config = "ToolConfig".role {
                    layout {
                        ".github" { anyFile() }
                        ".idea" { anyFile() }
                    }
                }
            """.trimIndent(),
        )
        val run = runCapturing(project, listOf("lint"))
        run.out shouldContain "ツールごとの設定ディレクトリを anyFile() / ignore() でまとめています: .github, .idea"
    }

    "Role が 8 個以上並ぶ平たい group を指摘し、nestedGroups = no なら黙る" {
        val roles = (1..8).joinToString("\n") { "    \"R$it\".role { }" }
        val project = projectWithDefinition("FeatureGroup.kt" to "val feature = \"feature\".group {\n$roles\n}")
        val run = runCapturing(project, listOf("lint"))
        run.out shouldContain "平たい group（直下に Role が 8 個以上あり、group を含まない）が 1 件あります"
        run.out shouldContain "FeatureGroup.kt:1 group \"feature\"（Role 8 個）"

        val quiet = runCapturing(project, listOf("lint"), env = preferences(project, "\"nestedGroups\": \"no\", \"oneRolePerFile\": \"no\""))
        quiet.out shouldNotContain "平たい group"
    }

    "小文字で始まる Role・group の名前を UpperCamelCase の案と一緒に指摘する（KATACHI-362）" {
        val project = projectWithDefinition(
            "MetaGroup.kt" to """
                val meta = "meta".group {
                    "License".role { layout { "LICENSE".file() } }
                    "gitIgnore".role { layout { ".gitignore".file() } }
                }
            """.trimIndent(),
        )
        val run = runCapturing(project, listOf("lint"), env = preferences(project, "\"oneRolePerFile\": \"no\""))
        run.status shouldBe 0
        run.out shouldContain "小文字で始まる Role・group の名前が 2 件あります。"
        run.out shouldContain "MetaGroup.kt:1 group \"meta\"（→ \"Meta\"）"
        run.out shouldContain "MetaGroup.kt:3 Role \"gitIgnore\"（→ \"GitIgnore\"）"
        run.out shouldNotContain "Role \"License\"（→"

        project.definitionFile("MetaGroup.kt").writeText(
            """
                val meta = "Meta".group {
                    "License".role { layout { "LICENSE".file() } }
                    "GitIgnore".role { layout { ".gitignore".file() } }
                }
            """.trimIndent(),
        )
        val fixed = runCapturing(project, listOf("lint"), env = preferences(project, "\"oneRolePerFile\": \"no\""))
        fixed.out shouldNotContain "小文字で始まる"
    }

    "1 ファイルに Role が 2 つあれば分けていないと指摘し、oneRolePerFile = no なら黙る" {
        val project = projectWithDefinition("Both.kt" to "val a = \"A\".role { }\nval b = \"B\".role { }")
        val run = runCapturing(project, listOf("lint"))
        run.out shouldContain "分けていないファイル（1 ファイルに Role か group が 2 つ以上）が 1 件あります"
        run.out shouldContain "Both.kt: Role \"A\", Role \"B\""

        val quiet = runCapturing(project, listOf("lint"), env = preferences(project, "\"oneRolePerFile\": \"no\""))
        quiet.out shouldContain "見つかりませんでした（2 件を走査）"
    }

    "補助ファイルだけの Role は documented = false の候補にし、書いてあれば外す" {
        val project = projectWithDefinition(
            "ReadmeRole.kt" to """
                val readme = "Readme".role {
                    layout {
                        "README.md".file()
                    }
                }
            """.trimIndent(),
        )
        val run = runCapturing(project, listOf("lint"))
        run.out shouldContain "documented = false の候補が 1 件あります"
        run.out shouldContain "Role \"Readme\"（README）"

        project.definitionFile("ReadmeRole.kt").writeText(
            """
                val readme = "Readme".role {
                    documented = false
                    layout {
                        "README.md".file()
                    }
                }
            """.trimIndent(),
        )
        runCapturing(project, listOf("lint")).out shouldContain "見つかりませんでした（1 件を走査）"
    }

    "1 Role 1 ファイルに分けた定義でも、group の documented = false と group 直下の Role 数を見る" {
        val roleFiles = (1..8).map { i ->
            "meta/Doc${i}Role.kt" to """
                fun DeclarationContainerScope.doc$i() = "Doc$i".role {
                  layout {
                    "README$i.md".file()
                  }
                }
            """.trimIndent()
        }
        val project = projectWithDefinition(
            "meta/MetaGroup.kt" to """
                fun DeclarationContainerScope.meta() =
                  "Meta".group {
                    documented = false
                    ${(1..8).joinToString("\n                    ") { "doc$it()" }}
                  }
            """.trimIndent(),
            *roleFiles.toTypedArray(),
        )
        val run = runCapturing(project, listOf("lint"))
        run.status shouldBe 0
        run.out shouldNotContain "documented = false の候補"
        run.out shouldContain "MetaGroup.kt:2 group \"Meta\"（Role 8 個）"
    }

    "init が記録した moduleDir と言語で走査する" {
        val project = createTempDirectory("katachi-project-")
        project.resolve("static-analysis/katachi/src/test/kotlin").createDirectories()
            .resolve("MixedRole.kt").writeText("val m = \"Mixed\".role { layout { \"README.md\".file(); \"LICENSE\".file() } }")
        val workdir = workdirWith(
            """{"meta": {"projectRoot": "$project", "moduleDir": "static-analysis/katachi"}, "preferences": {}, "steps": []}""",
            lang = "en",
        )
        val run = runCapturing(project, listOf("lint"), env = mapOf("KATACHI_WORKDIR" to workdir.toString()))
        run.status shouldBe 0
        run.out shouldContain "Roles that seem to hold files of different kinds: 1 (1 scanned)."
        run.out shouldContain "static-analysis/katachi/src/test/kotlin"
    }
})

/** A project whose `architecture-test/src/test/kotlin` holds [files] (name to source). */
private fun projectWithDefinition(vararg files: Pair<String, String>): Path {
    val project = createTempDirectory("katachi-project-")
    val dir = project.resolve("architecture-test/src/test/kotlin").createDirectories()
    for ((name, source) in files) dir.resolve(name).also { it.parent.createDirectories() }.writeText(source + "\n")
    return project
}

private fun Path.definitionFile(name: String): Path = resolve("architecture-test/src/test/kotlin").resolve(name)

/** `KATACHI_WORKDIR` pointing at a working directory whose check list records [project] and [preferences]. */
private fun preferences(project: Path, preferences: String): Map<String, String> {
    val workdir = workdirWith("""{"meta": {"projectRoot": "$project"}, "preferences": {$preferences}, "steps": []}""")
    return mapOf("KATACHI_WORKDIR" to workdir.toString())
}
