package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import me.tbsten.katachi.script.internal.Dsl
import me.tbsten.katachi.script.internal.GradleFile
import me.tbsten.katachi.script.internal.KotlinPlacement
import me.tbsten.katachi.script.internal.addSettingsInclude
import me.tbsten.katachi.script.internal.includedBuildDirs
import me.tbsten.katachi.script.internal.kotlinPluginPlacement
import me.tbsten.katachi.script.internal.planCatalogEntries
import me.tbsten.katachi.script.internal.runtimeDependencyDeclarations
import me.tbsten.katachi.script.internal.settingsIncludeEnd

/** Reading and editing the user's Gradle files, as the install kit's sh did. */
class GradleFilesSpec : FreeSpec({
    "settingsIncludeEnd" - {
        "複数行の include は閉じ括弧の行で終わる" {
            settingsIncludeEnd(listOf("rootProject.name = \"x\"", "include(", "    \":a\",", "    \":b\",", ")", "dependencyResolutionManagement {", "}")) shouldBe 5
        }

        "Groovy の , で続く include は続きの行で終わる" {
            settingsIncludeEnd(listOf("include ':a',", "        ':b'", "")) shouldBe 2
        }

        "{ } の中の include と includeBuild は数えない" {
            settingsIncludeEnd(listOf("includeBuild(\"build-logic\")", "if (true) {", "    include(\":x\")", "}")) shouldBe 0
        }

        "閉じていない include は判定できない" {
            settingsIncludeEnd(listOf("include(", "    \":a\",")) shouldBe null
        }
    }

    "addSettingsInclude は最後の include の後ろに足す" {
        val root = createTempDirectory("katachi-gradle-")
        root.resolve("settings.gradle.kts").writeText("include(\":app\")\n\ndependencyResolutionManagement {\n}\n")
        addSettingsInclude(root, GradleFile("settings.gradle.kts", Dsl.KTS), ":architecture-test") shouldBe null
        root.resolve("settings.gradle.kts").readText() shouldBe "include(\":app\")\ninclude(\":architecture-test\")\n\ndependencyResolutionManagement {\n}\n"
    }

    "includeBuildDirs はリポジトリの外と . を除く" {
        val root = createTempDirectory("katachi-gradle-")
        root.resolve("settings.gradle.kts").writeText("includeBuild(\"build-logic\")\nincludeBuild(\"../outside\")\nincludeBuild(\"./tools/\") // includeBuild(\"commented\")\n")
        includedBuildDirs(root) shouldBe listOf("build-logic", "tools")
    }

    "kotlinPluginPlacement" - {
        "ルートが Kotlin プラグインを持っていれば root" {
            val root = createTempDirectory("katachi-gradle-")
            root.resolve("build.gradle.kts").writeText("plugins {\n    kotlin(\"jvm\") version \"2.4.10\" apply false\n}\n")
            kotlinPluginPlacement(root, root.resolve("build.gradle.kts"), "architecture-test") shouldBe KotlinPlacement.ROOT
        }

        "コメントの中の宣言は数えない" {
            val root = createTempDirectory("katachi-gradle-")
            root.resolve("build.gradle.kts").writeText("plugins {\n    // id(\"org.jetbrains.kotlin.jvm\")\n}\n")
            kotlinPluginPlacement(root, root.resolve("build.gradle.kts"), "architecture-test") shouldBe KotlinPlacement.ADD
        }

        "サブプロジェクトだけが版付きで持っていれば module" {
            val root = createTempDirectory("katachi-gradle-")
            root.resolve("build.gradle.kts").writeText("")
            root.resolve("shared").createDirectories().resolve("build.gradle.kts").writeText("plugins {\n    kotlin(\"multiplatform\") version \"2.4.10\"\n}\n")
            kotlinPluginPlacement(root, root.resolve("build.gradle.kts"), "architecture-test") shouldBe KotlinPlacement.MODULE
        }

        "buildSrc が embeddedKotlin(\"gradle-plugin\") に依存していれば root（KATACHI-420）" {
            val root = projectWithBuildSrc(
                """
                plugins {
                    `kotlin-dsl`
                }

                dependencies {
                    implementation(embeddedKotlin("gradle-plugin"))
                }
                """.trimIndent(),
            )
            kotlinPluginPlacement(root, root.resolve("build.gradle.kts"), "architecture-test") shouldBe KotlinPlacement.ROOT
        }

        "buildSrc の implementation( が座標を次の行に書いていても root（KATACHI-420）" {
            val root = projectWithBuildSrc(
                """
                dependencies {
                    implementation(
                        "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21",
                    )
                }
                """.trimIndent(),
            )
            kotlinPluginPlacement(root, root.resolve("build.gradle.kts"), "architecture-test") shouldBe KotlinPlacement.ROOT
        }

        "buildSrc の compileOnly は、複数行でも、同じブロックに別の implementation があっても数えない" {
            val root = projectWithBuildSrc(
                """
                dependencies {
                    implementation(libs.foo)
                    compileOnly(
                        kotlin("gradle-plugin"),
                    )
                }
                """.trimIndent(),
            )
            kotlinPluginPlacement(root, root.resolve("build.gradle.kts"), "architecture-test") shouldBe KotlinPlacement.ADD
        }

        "buildSrc のコメントの中の implementation は数えない" {
            val root = projectWithBuildSrc(
                """
                dependencies {
                    /* implementation(
                        "org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21",
                    ) */
                    // implementation(embeddedKotlin("gradle-plugin"))
                }
                """.trimIndent(),
            )
            kotlinPluginPlacement(root, root.resolve("build.gradle.kts"), "architecture-test") shouldBe KotlinPlacement.ADD
        }
    }

    "runtimeDependencyDeclarations" - {
        "括弧の中を行をまたいで読み、Groovy の括弧なしは行の残りを読む" {
            runtimeDependencyDeclarations(
                listOf(
                    "dependencies {",
                    "    api(\"a:b:1\") // implementation(\"c:d:1\")",
                    "    implementation(",
                    "        platform(\"e:f:1\"),",
                    "    )",
                    "    runtimeOnly 'g:h:1'",
                    "    testImplementation(\"i:j:1\")",
                    "    println(\"implementation(k:l:1)\")",
                    "}",
                ),
            ) shouldBe listOf("\"a:b:1\"", "\n        platform(\"e:f:1\"),\n    ", "'g:h:1'")
        }
    }

    "planCatalogEntries" - {
        "無い項目だけを足し、既存の junit の下には camelCase で逃がす" {
            val root = createTempDirectory("katachi-gradle-")
            root.resolve("gradle").createDirectories().resolve("libs.versions.toml").writeText(
                "[libraries]\njunit = { module = \"junit:junit\", version = \"4.13.2\" }\n",
            )
            val plan = planCatalogEntries(root, "0.4.0", konsist = false, testFramework = "junit5")
            plan.fallback shouldBe null
            plan.versions shouldBe listOf("katachi = \"0.4.0\"", "junit = \"5.14.4\"")
            plan.libraries.first { it.contains("junit-bom") } shouldBe "junitBom = { module = \"org.junit:junit-bom\", version.ref = \"junit\" }"
            plan.junitBom shouldBe "libs.junitBom"
        }

        "同じ別名が別の中身なら理由を返す" {
            val root = createTempDirectory("katachi-gradle-")
            root.resolve("gradle").createDirectories().resolve("libs.versions.toml").writeText("[versions]\nkatachi = \"0.1.0\"\n")
            planCatalogEntries(root, "0.4.0", konsist = false, testFramework = "junit5").fallback?.ja shouldBe
                "別名 katachi が別の用途で使われています（[versions] katachi = \"0.1.0\"）"
        }
    }
})

/** A project with an empty root build file and [buildSrcBuild] as `buildSrc/build.gradle.kts`. */
private fun projectWithBuildSrc(buildSrcBuild: String): Path {
    val root = createTempDirectory("katachi-gradle-")
    root.resolve("build.gradle.kts").writeText("")
    root.resolve("buildSrc").createDirectories().resolve("build.gradle.kts").writeText(buildSrcBuild + "\n")
    return root
}
