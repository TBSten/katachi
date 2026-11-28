package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/** `lint`: the Role check of the guide's step 3, read from the definition as Gradle evaluated it (KATACHI-485). It never fails on what it finds. */
class LintCommandSpec : FreeSpec({
    "定義のディレクトリが無ければ scaffold を案内して止まる（Gradle は起動しない）" {
        val run = runCapturing(createTempDirectory("katachi-project-"), listOf("lint"))
        run.status shouldBe 1
        run.err shouldContain "定義のディレクトリがありません"
        run.err shouldContain "architecture-test/src/test/kotlin"
    }

    "知らないオプションと余分な引数は、Gradle を起動する前に使い方の誤り（2）で止まる" {
        val project = gradleProject("CleanRole.kt" to "// Clean")
        for (argument in listOf("--bogus", "extra")) {
            val run = runCapturing(project, listOf("lint", argument))
            run.status shouldBe 2
            run.err shouldContain "[x] lint: 知らないオプションです: $argument"
            run.out shouldNotContain "Role の点検"
        }
        val en = runCapturing(project, listOf("lint", "--bogus"), env = mapOf("KATACHI_LANG" to "en"))
        en.status shouldBe 2
        en.err shouldContain "[x] lint: unknown option: --bogus"
        // A usage error wins over a missing definition (1).
        runCapturing(createTempDirectory("katachi-project-"), listOf("lint", "--bogus")).status shouldBe 2
    }

    "--help は引数として読まずに使い方を出す" {
        val run = runCapturing(createTempDirectory("katachi-project-"), listOf("lint", "--help"))
        run.status shouldBe 0
        run.out shouldContain "katachi lint"
        run.out shouldContain "小文字で始まる Role・group の名前"
        run.out shouldContain "./gradlew で定義を評価する"
    }

    "プロジェクトの gradlew で、検査用モジュールの katachiInternalDefinitionJson を静かに回す" {
        val project = gradleProject("CleanRole.kt" to "// Clean")
        val gradle = FakeGradle(json = definitionJson(roles = listOf(roleJson("Clean", "CleanRole.kt", layout = listOf(layoutFile("src/main/Clean.kt"))))))
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.status shouldBe 0
        val call = gradle.calls.single()
        call.root shouldBe project
        call.arguments shouldBe listOf("-q", "--console=plain", ":architecture-test:katachiInternalDefinitionJson")
        run.err shouldContain "定義を Gradle で評価しています"
    }

    "気になる Role が無ければ点検した件数だけを出す" {
        val project = gradleProject("CleanRole.kt" to "// Clean")
        val gradle = FakeGradle(json = definitionJson(roles = listOf(roleJson("Clean", "CleanRole.kt", layout = listOf(layoutFile("src/main/Clean.kt"))))))
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.status shouldBe 0
        run.out shouldContain "Role の点検: 種類の違うファイルを抱えていそうな Role は見つかりませんでした（1 件を走査）。"
    }

    "ソースにどう書いてあっても、評価した定義にある Role だけを数える（日本語のテスト名は Role ではない）" {
        val project = gradleProject(
            "ArchitectureSpec.kt" to """
                class ArchitectureSpec : FreeSpec({
                    "検査用のモジュールが読める" { }
                    "Ghost".role { layout { "README.md".file(); "build.gradle.kts".file() } }
                })
            """.trimIndent(),
            "CleanRole.kt" to "// Clean",
        )
        val gradle = FakeGradle(json = definitionJson(roles = listOf(roleJson("Clean", "CleanRole.kt", layout = listOf(layoutFile("src/main/Clean.kt"))))))
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.out shouldContain "（1 件を走査）"
        run.out shouldNotContain "Ghost"
    }

    "Role の中に直書きした ktFileContent・fileConstraint・konsist・konture を、長さによらず呼び出しごとに指摘する（KATACHI-486）" {
        val project = gradleProject(
            "InlineRole.kt" to """
                val inline = "Inline".role {
                    ktFileContent { }
                    fileConstraint("no http") {
                        a()
                        b()
                        c()
                        d()
                        e()
                    }
                    konsist {
                        "x".konsist { }
                    }
                    konture { }
                }
            """.trimIndent(),
        )
        val gradle = FakeGradle(json = definitionJson(roles = listOf(roleJson("Inline", "InlineRole.kt", constraints = 4))))
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.status shouldBe 0
        run.out shouldContain "Role の中に制約（ktFileContent・fileConstraint・konsist・konture）を直書きしているものが 1 件あります。"
        run.out shouldContain
            "InlineRole.kt:1 Role \"Inline\"（ktFileContent（2 行目）, fileConstraint（3 行目）, konsist（10 行目）, konture（13 行目））"
        run.out shouldContain "private fun RoleScope.<role>KtFileContent()"
    }

    "切り出した制約は直書きに数えない（Role の下に置いても、別のファイルでも）（KATACHI-486）" {
        val project = gradleProject(
            "BuildRole.kt" to """
                val build = "Build".role {
                    buildKtFileContent()
                    noHttp()
                    layout {
                        "build.gradle.kts".file()
                    }
                }

                private fun RoleScope.buildKtFileContent() = ktFileContent { }

                private fun RoleScope.noHttp() = fileConstraint("no http") { }
            """.trimIndent(),
        )
        val gradle = FakeGradle(
            json = definitionJson(roles = listOf(roleJson("Build", "BuildRole.kt", layout = listOf(layoutFile("build.gradle.kts")), constraints = 2))),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle, env = preferences(project, "\"oneRolePerFile\": \"no\""))
        run.out shouldNotContain "直書き"
    }

    "layout の中と、ほかの Role の中の制約は、その Role の直書きに数えない（KATACHI-486）" {
        val project = gradleProject(
            "Roles.kt" to """
                val outer = "Outer".role {
                    layout { }
                    layout { "x".file() }
                }
                val other = "Other".role {
                    ktFileContent { }
                }
            """.trimIndent(),
        )
        val gradle = FakeGradle(
            json = definitionJson(
                roles = listOf(
                    roleJson("Outer", "Roles.kt", line = 1, layout = listOf(layoutFile("x"))),
                    roleJson("Other", "Roles.kt", line = 5, constraints = 1),
                ),
            ),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle, env = preferences(project, "\"oneRolePerFile\": \"no\""))
        run.out shouldContain "直書きしているものが 1 件あります。"
        run.out shouldContain "Roles.kt:5 Role \"Other\"（ktFileContent（6 行目））"
        run.out shouldNotContain "Role \"Outer\"（ktFileContent"
    }

    "直書きの制約の指摘は英語でも出て、失敗にはしない（KATACHI-486）" {
        val project = gradleProject("InlineRole.kt" to "val inline = \"Inline\".role {\n    ktFileContent { }\n}")
        val gradle = FakeGradle(json = definitionJson(roles = listOf(roleJson("Inline", "InlineRole.kt", constraints = 1))))
        val run = runCapturing(project, listOf("lint"), gradle = gradle, env = mapOf("KATACHI_LANG" to "en"))
        run.status shouldBe 0
        run.out shouldContain "Roles with a constraint written inline (ktFileContent, fileConstraint, konsist, konture): 1."
        run.out shouldContain "InlineRole.kt:1 Role \"Inline\" (ktFileContent (line 2))"
    }

    "ソースが読めない・Role の呼び出しが見つからないときは、直書きの指摘を出さずに止まらない（KATACHI-486）" {
        val project = gradleProject("Broken.kt" to "val x = \"Broken\".role { ktFileContent {")
        val gradle = FakeGradle(json = definitionJson(roles = listOf(roleJson("Broken", "Broken.kt", line = 40))))
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.status shouldBe 0
    }

    "定義のディレクトリに無いファイルで宣言された Role と、gradle() が宣言した Role は点検しない" {
        val project = gradleProject("CleanRole.kt" to "// Clean")
        val gradle = FakeGradle(
            json = definitionJson(
                groups = listOf(groupJson("Gradle", "CleanRole.kt", declaredByGradle = true)),
                roles = listOf(
                    roleJson("Clean", "CleanRole.kt", layout = listOf(layoutFile("src/main/Clean.kt"))),
                    roleJson("BuildScript", "CleanRole.kt", groupPath = listOf("Gradle"), layout = listOf(layoutFile("build.gradle.kts")), constraints = 1, declaredByGradle = true),
                    roleJson("FromAnotherBuild", "SharedRole.kt", layout = listOf(layoutFile("README.md"), layoutFile("build.gradle.kts"))),
                    roleJson("NoSite", null, layout = listOf(layoutFile("README.md"), layoutFile("build.gradle.kts"))),
                ),
            ),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.out shouldContain "（1 件を走査）"
        run.out shouldNotContain "Gradle のファイルを手書き"
        run.out shouldNotContain "分けていないファイル"
    }

    "declaredByGradle を書かない古い版の JSON でも、gradle() の Role と group を点検しない" {
        val project = gradleProject("CleanRole.kt" to "// Clean", "GradleGroup.kt" to "// gradle()")
        val gradle = FakeGradle(
            json = definitionJson(
                groups = listOf(
                    groupJson("Gradle", "GradleGroup.kt", line = 6, declaredByGradle = null),
                    groupJson("GradleWrapper", "GradleGroup.kt", line = 6, path = listOf("Gradle", "GradleWrapper"), declaredByGradle = null),
                ),
                roles = listOf(
                    roleJson("Clean", "CleanRole.kt", layout = listOf(layoutFile("src/main/Clean.kt"))),
                    roleJson("BuildScript", "GradleGroup.kt", line = 6, groupPath = listOf("Gradle"), layout = listOf(layoutFile("build.gradle.kts")), declaredByGradle = null),
                    roleJson("LauncherScript", "GradleGroup.kt", line = 6, groupPath = listOf("Gradle", "GradleWrapper"), layout = listOf(layoutFile("gradlew")), declaredByGradle = null),
                ),
            ),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.out shouldContain "（1 件を走査）"
    }

    "種類の違うファイルを抱える Role と、手書きで宣言した Gradle のファイルを指摘し、失敗にはしない" {
        val project = gradleProject("MixedRole.kt" to "// Mixed")
        val gradle = FakeGradle(
            json = definitionJson(roles = listOf(roleJson("Mixed", "MixedRole.kt", layout = listOf(layoutFile("README.md"), layoutFile("build.gradle.kts"))))),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.status shouldBe 0
        run.out shouldContain "種類の違うファイルを1つの Role に抱えていそうなものが 1 件あります（1 件を走査）。"
        run.out shouldContain "MixedRole.kt:1 Role \"Mixed\""
        run.out shouldContain "種類の違うファイルが同居しています: README（README.md） / build.gradle(.kts)（build.gradle.kts）"
        run.out shouldContain "Gradle のファイルを手書きの Role で宣言しています（Role: Mixed）。"
        run.out shouldNotContain "テキストの素朴な走査"
    }

    "制約を付けた Role の Gradle のファイルは、移す先の hook を名指しする" {
        val project = gradleProject("BuildRole.kt" to "// Build")
        val gradle = FakeGradle(json = definitionJson(roles = listOf(roleJson("Build", "BuildRole.kt", layout = listOf(layoutFile("build.gradle.kts")), constraints = 1))))
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.status shouldBe 0
        run.out shouldContain "Gradle のファイルを手書きの Role で宣言し、その Role に制約を付けています:"
        run.out shouldContain "Role \"Build\"（build.gradle.kts → gradle { buildScript { } }）"
        run.out shouldNotContain "Gradle のファイルを手書きの Role で宣言しています（Role:"
    }

    "buildSrc の Gradle のファイルは gradle() の対象外なので数えない" {
        val project = gradleProject("BuildSrcRole.kt" to "// BuildSrc")
        val gradle = FakeGradle(json = definitionJson(roles = listOf(roleJson("BuildSrcScript", "BuildSrcRole.kt", layout = listOf(layoutFile("buildSrc/build.gradle.kts"))))))
        runCapturing(project, listOf("lint"), gradle = gradle).out shouldNotContain "Gradle のファイルを手書きの Role"
    }

    "本体とテストのファイルを同じ Role で宣言していれば指摘する" {
        val project = gradleProject("RepositoryRole.kt" to "// Repository")
        val gradle = FakeGradle(
            json = definitionJson(
                roles = listOf(roleJson("Repository", "RepositoryRole.kt", layout = listOf(layoutFile("src/main/Repository.kt"), layoutFile("src/test/RepositoryTest.kt")))),
            ),
        )
        runCapturing(project, listOf("lint"), gradle = gradle).out shouldContain "本体（main）とテスト（test）のファイルを同じ Role で宣言しています"
    }

    "ツールごとの設定ディレクトリを anyFile() でまとめていれば指摘する" {
        val project = gradleProject("ToolConfigRole.kt" to "// ToolConfig")
        val gradle = FakeGradle(
            json = definitionJson(roles = listOf(roleJson("ToolConfig", "ToolConfigRole.kt", layout = listOf(layoutAnyFile(".github"), layoutAnyFile(".idea"))))),
        )
        runCapturing(project, listOf("lint"), gradle = gradle).out shouldContain
            "ツールごとの設定ディレクトリを anyFile() / ignore() でまとめています: .github, .idea"
    }

    "module { } が足す行（synthetic）は、ファイルの種類にも置き場所にも数えない" {
        val project = gradleProject("ModuleRole.kt" to "// Module")
        val gradle = FakeGradle(
            json = definitionJson(
                roles = listOf(
                    roleJson(
                        "ModuleRole",
                        "ModuleRole.kt",
                        layout = listOf(layoutFile("src/main/Module.kt"), JsonEntry("src/test", kind = "AnyFile", synthetic = true), JsonEntry("README.md", synthetic = true)),
                    ),
                ),
            ),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.out shouldContain "（1 件を走査）"
        run.out shouldNotContain "本体（main）とテスト（test）"
    }

    "Role が 8 個以上並ぶ平たい group を指摘し、nestedGroups = no なら黙る" {
        val roles = (1..8).map { roleJson("R$it", "FeatureGroup.kt", line = it + 1, groupPath = listOf("Feature"), layout = listOf(layoutFile("src/R$it.kt"))) }
        val project = gradleProject("FeatureGroup.kt" to "// Feature")
        val gradle = FakeGradle(json = definitionJson(groups = listOf(groupJson("Feature", "FeatureGroup.kt")), roles = roles))
        val run = runCapturing(project, listOf("lint"), gradle = gradle, env = preferences(project, "\"oneRolePerFile\": \"no\""))
        run.out shouldContain "平たい group（直下に Role が 8 個以上あり、group を含まない）が 1 件あります"
        run.out shouldContain "FeatureGroup.kt:1 group \"Feature\"（Role 8 個）"

        val quiet = runCapturing(project, listOf("lint"), gradle = gradle, env = preferences(project, "\"nestedGroups\": \"no\", \"oneRolePerFile\": \"no\""))
        quiet.out shouldNotContain "平たい group"
    }

    "group を含む group は、Role が 8 個以上あっても平たいとは言わない" {
        val roles = (1..8).map { roleJson("R$it", "FeatureGroup.kt", line = it + 1, groupPath = listOf("Feature"), layout = listOf(layoutFile("src/R$it.kt"))) }
        val project = gradleProject("FeatureGroup.kt" to "// Feature")
        val gradle = FakeGradle(
            json = definitionJson(
                groups = listOf(groupJson("Feature", "FeatureGroup.kt"), groupJson("Sub", "FeatureGroup.kt", line = 20, path = listOf("Feature", "Sub"))),
                roles = roles,
            ),
        )
        runCapturing(project, listOf("lint"), gradle = gradle, env = preferences(project, "\"oneRolePerFile\": \"no\"")).out shouldNotContain "平たい group"
    }

    "小文字で始まる Role・group の名前を UpperCamelCase の案と一緒に指摘する（KATACHI-362）" {
        val project = gradleProject("MetaGroup.kt" to "// Meta")
        val gradle = FakeGradle(
            json = definitionJson(
                groups = listOf(groupJson("meta", "MetaGroup.kt")),
                roles = listOf(
                    roleJson("License", "MetaGroup.kt", line = 2, groupPath = listOf("meta"), layout = listOf(layoutFile("LICENSE"))),
                    roleJson("gitIgnore", "MetaGroup.kt", line = 3, groupPath = listOf("meta"), layout = listOf(layoutFile(".gitignore"))),
                ),
            ),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle, env = preferences(project, "\"oneRolePerFile\": \"no\""))
        run.status shouldBe 0
        run.out shouldContain "小文字で始まる Role・group の名前が 2 件あります。"
        run.out shouldContain "MetaGroup.kt:1 group \"meta\"（→ \"Meta\"）"
        run.out shouldContain "MetaGroup.kt:3 Role \"gitIgnore\"（→ \"GitIgnore\"）"
        run.out shouldNotContain "Role \"License\"（→"
    }

    "1 ファイルに Role が 2 つあれば分けていないと指摘し、oneRolePerFile = no なら黙る" {
        val project = gradleProject("Both.kt" to "// Both")
        val gradle = FakeGradle(
            json = definitionJson(
                roles = listOf(
                    roleJson("B", "Both.kt", line = 2, layout = listOf(layoutFile("src/B.kt"))),
                    roleJson("A", "Both.kt", line = 1, layout = listOf(layoutFile("src/A.kt"))),
                ),
            ),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.out shouldContain "分けていないファイル（1 ファイルに Role か group が 2 つ以上）が 1 件あります"
        run.out shouldContain "Both.kt: Role \"A\", Role \"B\""

        val quiet = runCapturing(project, listOf("lint"), gradle = gradle, env = preferences(project, "\"oneRolePerFile\": \"no\""))
        quiet.out shouldContain "見つかりませんでした（2 件を走査）"
    }

    "補助ファイルだけの Role と ignore() だけの Role は documented = false の候補にし、書いてあれば外す" {
        val project = gradleProject("ReadmeRole.kt" to "// Readme", "BuildOutputRole.kt" to "// BuildOutput")
        fun gradle(readmeDocumented: Boolean?) = FakeGradle(
            json = definitionJson(
                roles = listOf(
                    roleJson("Readme", "ReadmeRole.kt", documented = readmeDocumented, layout = listOf(layoutFile("README.md"))),
                    roleJson("BuildOutput", "BuildOutputRole.kt", documented = false, layout = listOf(layoutIgnore("build"))),
                    roleJson("Sources", "BuildOutputRole.kt", line = 2, layout = listOf(layoutIgnore("generated"))),
                ),
            ),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle(null), env = preferences(project, "\"oneRolePerFile\": \"no\""))
        run.out shouldContain "documented = false の候補が 2 件あります"
        run.out shouldContain "Role \"Readme\"（README）"
        run.out shouldContain "Role \"Sources\"（ignore()）"
        run.out shouldNotContain "Role \"BuildOutput\"（"

        val written = runCapturing(project, listOf("lint"), gradle = gradle(false), env = preferences(project, "\"oneRolePerFile\": \"no\""))
        written.out shouldNotContain "Role \"Readme\"（README）"
    }

    "1 Role 1 ファイルに分けた定義でも、group の documented = false と group 直下の Role 数を見る" {
        val project = gradleProject("meta/MetaGroup.kt" to "// Meta", *(1..8).map { "meta/Doc${it}Role.kt" to "// Doc$it" }.toTypedArray())
        val gradle = FakeGradle(
            json = definitionJson(
                groups = listOf(groupJson("Meta", "MetaGroup.kt", documented = false)),
                roles = (1..8).map { roleJson("Doc$it", "Doc${it}Role.kt", groupPath = listOf("Meta"), layout = listOf(layoutFile("README$it.md"))) },
            ),
        )
        val run = runCapturing(project, listOf("lint"), gradle = gradle)
        run.status shouldBe 0
        run.out shouldNotContain "documented = false の候補"
        run.out shouldContain "MetaGroup.kt:1 group \"Meta\"（Role 8 個）"
    }

    "init が記録した moduleDir と言語で、その検査用モジュールのタスクを回す" {
        val project = createTempDirectory("katachi-project-")
        Files.createFile(project.resolve("gradlew"))
        project.resolve("static-analysis/katachi/src/test/kotlin").createDirectories().resolve("MixedRole.kt").writeText("// Mixed")
        val workdir = workdirWith(
            """{"meta": {"projectRoot": "$project", "moduleDir": "static-analysis/katachi"}, "preferences": {}, "steps": []}""",
            lang = "en",
        )
        val gradle = FakeGradle(
            moduleDir = "static-analysis/katachi",
            json = definitionJson(roles = listOf(roleJson("Mixed", "MixedRole.kt", layout = listOf(layoutFile("README.md"), layoutFile("LICENSE"))))),
        )
        val run = runCapturing(project, listOf("lint"), env = mapOf("KATACHI_WORKDIR" to workdir.toString()), gradle = gradle)
        run.status shouldBe 0
        gradle.calls.single().arguments[2] shouldBe ":static-analysis:katachi:katachiInternalDefinitionJson"
        run.out shouldContain "Roles that seem to hold files of different kinds: 1 (1 scanned)."
        run.out shouldContain "static-analysis/katachi/src/test/kotlin"
    }

    "--module-dir の定義を読む。チェックリストが無くても、meta.moduleDir と違っても（KATACHI-420）" {
        val project = createTempDirectory("katachi-project-")
        Files.createFile(project.resolve("gradlew"))
        project.resolve("static-analysis/katachi/src/test/kotlin").createDirectories().resolve("MixedRole.kt").writeText("// Mixed")
        val recordingOther = workdirWith("""{"meta": {"projectRoot": "$project", "moduleDir": "static-analysis/old"}, "steps": []}""")

        for (env in listOf(emptyMap(), mapOf("KATACHI_WORKDIR" to recordingOther.toString()))) {
            val gradle = FakeGradle(
                moduleDir = "static-analysis/katachi",
                json = definitionJson(roles = listOf(roleJson("Mixed", "MixedRole.kt", layout = listOf(layoutFile("README.md"), layoutFile("LICENSE"))))),
            )
            val run = runCapturing(project, listOf("lint", "--module-dir", "static-analysis/katachi"), env = env, gradle = gradle)

            run.status shouldBe 0
            run.out shouldContain "種類の違うファイルを1つの Role に抱えていそうなものが 1 件あります（1 件を走査）。"
            run.out shouldContain "定義: file://${project.resolve("static-analysis/katachi/src/test/kotlin")}"
        }
    }

    "定義のディレクトリが無ければ、--module-dir でも指定できると言って 1 で止まる" {
        val run = runCapturing(createTempDirectory("katachi-project-"), listOf("lint"))

        run.status shouldBe 1
        run.err shouldContain "違うなら --module-dir <パス> で指定するか、チェックリストの meta.moduleDir を直す"
    }

    "定義がコンパイルできなければ、点検せずコンパイルエラーを出して 1 で止まる" {
        val project = gradleProject("BrokenRole.kt" to "// Broken")
        val errors = (1..12).joinToString("\n") { "e: file:///p/architecture-test/src/test/kotlin/BrokenRole.kt:$it:5 Unresolved reference 'x$it'." }
        val run = runCapturing(project, listOf("lint"), gradle = FakeGradle(status = 1, failure = "> Task :architecture-test:compileTestKotlin FAILED\n$errors\n\nFAILURE: Build failed"))

        run.status shouldBe 1
        run.err shouldContain "[x] lint: 定義を Gradle で評価できなかったので、点検していません"
        run.err shouldContain "終了コード 1"
        run.err shouldContain "コンパイルエラー（12 件）:"
        run.err shouldContain "BrokenRole.kt:1:5 Unresolved reference 'x1'."
        run.err shouldContain "ほか 2 件"
        run.err shouldContain "定義を直してから、もう一度 katachi lint を実行してください。"
        run.out shouldNotContain "Role の点検"
    }

    "コンパイルエラーの行が無い失敗は、Gradle の出力の末尾を出す" {
        val project = gradleProject("CleanRole.kt" to "// Clean")
        val run = runCapturing(project, listOf("lint"), gradle = FakeGradle(status = 1, failure = (1..30).joinToString("\n") { "line $it" }), env = mapOf("KATACHI_LANG" to "en"))

        run.status shouldBe 1
        run.err shouldContain "the definition could not be evaluated by Gradle, so nothing was checked"
        run.err shouldContain "The end of Gradle's output:"
        run.err shouldContain "line 30"
        run.err shouldNotContain "line 15\n"
    }

    "検査用モジュールに katachiInternalDefinitionJson が無ければ、プラグインを案内して 1 で止まる" {
        val project = gradleProject("CleanRole.kt" to "// Clean")
        val failure = "* What went wrong:\nCannot locate tasks that match ':architecture-test:katachiInternalDefinitionJson' as task " +
            "'katachiInternalDefinitionJson' not found in project ':architecture-test'."
        val run = runCapturing(project, listOf("lint"), gradle = FakeGradle(status = 1, failure = failure))

        run.status shouldBe 1
        run.err shouldContain "[x] lint: :architecture-test に katachiInternalDefinitionJson のタスクがありません"
        run.err shouldContain "me.tbsten.katachi"
    }

    "gradlew が無ければ、Gradle を起動せずに言って 1 で止まる" {
        val project = createTempDirectory("katachi-project-")
        project.resolve("architecture-test/src/test/kotlin").createDirectories().resolve("CleanRole.kt").writeText("// Clean")
        val run = runCapturing(project, listOf("lint"))

        run.status shouldBe 1
        run.err shouldContain "[x] lint: プロジェクトのルートに gradlew がありません"
    }

    "Gradle が成功しても、今回の実行で書かれた JSON でなければ読まない（前回の JSON が残っているとき）" {
        val project = gradleProject("CleanRole.kt" to "// Clean")
        val old = project.resolve("architecture-test/build/katachi/internalDefinitionJson/definition.json")
        old.parent.createDirectories()
        old.writeText(definitionJson(roles = listOf(roleJson("Clean", "CleanRole.kt", layout = listOf(layoutFile("README.md"), layoutFile("LICENSE"))))))
        Files.setLastModifiedTime(old, FileTime.fromMillis(System.currentTimeMillis() - 3_600_000))

        val run = runCapturing(project, listOf("lint"), gradle = FakeGradle(write = false))

        run.status shouldBe 1
        run.err shouldContain "Gradle が書いた定義の JSON を読めませんでした"
        run.out shouldNotContain "Role の点検"
    }

    "この katachi コマンドが読めない新しい版の JSON は、更新を案内して 1 で止まる" {
        val project = gradleProject("CleanRole.kt" to "// Clean")
        val run = runCapturing(project, listOf("lint"), gradle = FakeGradle(json = definitionJson(schemaVersion = 2)))

        run.status shouldBe 1
        run.err shouldContain "schemaVersion 2"
        run.err shouldContain "katachi コマンドを更新してください"
    }

    "壊れた JSON は読めなかったと言って 1 で止まる" {
        val project = gradleProject("CleanRole.kt" to "// Clean")
        val run = runCapturing(project, listOf("lint"), gradle = FakeGradle(json = """{"schemaVersion": 1, "groups": []}"""))

        run.status shouldBe 1
        run.err shouldContain "Gradle が書いた定義の JSON を読めませんでした"
        run.err shouldContain "\"roles\" is missing"
    }
})

/** `KATACHI_WORKDIR` pointing at a working directory whose check list records [project] and [preferences]. */
private fun preferences(project: Path, preferences: String): Map<String, String> {
    val workdir = workdirWith("""{"meta": {"projectRoot": "$project"}, "preferences": {$preferences}, "steps": []}""")
    return mapOf("KATACHI_WORKDIR" to workdir.toString())
}
