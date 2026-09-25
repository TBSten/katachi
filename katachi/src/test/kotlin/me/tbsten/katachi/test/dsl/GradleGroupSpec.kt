package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.scan.MissingFile
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.labels
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.docs.documents
import me.tbsten.katachi.test.fs.FakeFileSystemScope

class GradleGroupSpec : FreeSpec({
    /** A Kotlin DSL build as `gradle init` leaves it, with two modules below the root project. */
    fun kotlinDslBuild(block: FakeFileSystemScope.() -> Unit = {}) = repositoryOf {
        "settings.gradle.kts"()
        "build.gradle.kts"()
        "gradle.properties"()
        "gradlew"()
        "gradlew.bat"()
        "gradle" {
            "libs.versions.toml"()
            "wrapper" {
                "gradle-wrapper.jar"()
                "gradle-wrapper.properties"()
            }
        }
        "app" { "build.gradle.kts"() }
        "core" { "data" { "build.gradle.kts"() } }
        block()
    }

    "典型的な Gradle プロジェクト" - {
        "gradle() だけの定義で違反が出ない" {
            architectureOf { gradle() }.validate(kotlinDslBuild()).labels().shouldBeEmpty()
        }

        "Groovy DSL のビルドでも違反が出ない" {
            val groovyBuild = repositoryOf {
                "settings.gradle"()
                "build.gradle"()
                "gradlew"()
                "gradlew.bat"()
                "gradle" {
                    "wrapper" {
                        "gradle-wrapper.jar"()
                        "gradle-wrapper.properties"()
                    }
                }
                "app" { "build.gradle"() }
            }
            architectureOf { gradle() }.validate(groovyBuild).labels().shouldBeEmpty()
        }

        "libs 以外の名前のカタログも、複数のカタログも許可される" {
            val build = kotlinDslBuild { "gradle" { "sample.versions.toml"() } }
            architectureOf { gradle() }.validate(build).labels().shouldBeEmpty()
        }

        "ルートにビルドスクリプトが無いプロジェクトでも違反が出ない" {
            val build = repositoryOf {
                "settings.gradle.kts"()
                "gradlew"()
                "gradlew.bat"()
                "gradle" {
                    "wrapper" {
                        "gradle-wrapper.jar"()
                        "gradle-wrapper.properties"()
                    }
                }
                "app" { "build.gradle.kts"() }
            }
            architectureOf { gradle() }.validate(build).labels().shouldBeEmpty()
        }

        "モジュールの中身を言う役割と並べても、ビルドスクリプトが重複の警告にならない" {
            val build = kotlinDslBuild {
                "app" { "src/main/kotlin" { "Main.kt"() } }
            }
            architectureOf {
                gradle()
                "App" { layout { ":app".module { mainSourceSet / kotlin / "Main".ktFile() } } }
            }.validate(build).labels().shouldBeEmpty()
        }
    }

    "余計なファイル" - {
        "gradle ディレクトリに宣言外のファイルがあると弾かれる" {
            val build = kotlinDslBuild { "gradle" { "init.gradle"() } }
            architectureOf { gradle() }.validate(build).labels() shouldBe
                listOf("[UnexpectedFile] gradle/init.gradle")
        }

        "モジュールの中身は gradle() では許可されない" {
            val build = kotlinDslBuild { "app" { "src/main/kotlin" { "Main.kt"() } } }
            architectureOf { gradle() }.validate(build).labels() shouldBe
                listOf("[UnexpectedDirectory] app/src")
        }

        "buildSrc は宣言しないので弾かれる" {
            val build = kotlinDslBuild { "buildSrc" { "build.gradle.kts"() } }
            architectureOf { gradle() }.validate(build).labels() shouldBe
                listOf("[UnexpectedDirectory] buildSrc")
        }

        "block で足した役割はそのファイルを許可する" {
            val build = kotlinDslBuild { "gradle" { "gradle-daemon-jvm.properties"() } }
            architectureOf {
                gradle {
                    "DaemonJvmProperties" {
                        documented = false
                        layout { "gradle/gradle-daemon-jvm.properties".file() }
                    }
                }
            }.validate(build).labels().shouldBeEmpty()
        }
    }

    "無いファイル" - {
        "wrapper のファイルは必須で、無いと Missing になる" {
            val build = repositoryOf {
                "settings.gradle.kts"()
                "gradlew"()
                "gradle" { "wrapper" { "gradle-wrapper.properties"() } }
            }
            architectureOf { gradle() }.validate(build).labels() shouldContainExactly listOf(
                "[MissingFile] gradlew.bat",
                "[MissingFile] gradle/wrapper/gradle-wrapper.jar",
            )
        }

        "requireWrapper = false なら wrapper が無くても違反が出ず、あれば許可される" {
            val withoutWrapper = repositoryOf { "settings.gradle.kts"() }
            val withPart = repositoryOf {
                "settings.gradle.kts"()
                "gradlew"()
            }
            architectureOf { gradle(requireWrapper = false) }.validate(withoutWrapper).labels().shouldBeEmpty()
            architectureOf { gradle(requireWrapper = false) }.validate(withPart).labels().shouldBeEmpty()
        }

        "settings・gradle.properties・カタログは無くても違反にならない" {
            val build = repositoryOf {
                "build.gradle.kts"()
                "gradlew"()
                "gradlew.bat"()
                "gradle" {
                    "wrapper" {
                        "gradle-wrapper.jar"()
                        "gradle-wrapper.properties"()
                    }
                }
            }
            architectureOf { gradle() }.validate(build).labels().shouldBeEmpty()
        }

        "Missing の宣言位置は gradle() を呼んだ行を指す" {
            val line = Throwable().stackTrace.first().lineNumber + 1
            val arch = architectureOf { gradle() }
            val missing = arch.validate(repositoryOf { "settings.gradle.kts"() })
                .filterIsInstance<MissingFile>()
            missing.map { it.declaredAt }.distinct() shouldBe
                listOf(DeclarationSite("GradleGroupSpec.kt", line))
        }
    }

    "宣言の形" - {
        "1つの group の下に、ファイルの種類ごとに1つの役割が並ぶ" {
            architecture { gradle() }.allRoles.map { it.qualifiedName } shouldContainExactly listOf(
                "Gradle/SettingsScript",
                "Gradle/BuildScript",
                "Gradle/GradleProperties",
                "Gradle/VersionCatalog",
                "Gradle/GradleWrapper/LauncherScript",
                "Gradle/GradleWrapper/WrapperJar",
                "Gradle/GradleWrapper/WrapperProperties",
            )
        }

        "group も役割もすべて documented = false を自分で持つ" {
            val arch = architecture { gradle() }
            arch.allGroups.map { it[Documented] }.distinct() shouldBe listOf(false)
            arch.allRoles.map { it[Documented] }.distinct() shouldBe listOf(false)
        }

        "生成されるドキュメントに Gradle のページが出ない" {
            val documents = architecture {
                gradle()
                "UseCase" { layout { "src" / "*UseCase.kt".file() } }
            }.documents()
            documents shouldContainKey "README.md"
            documents.keys.filter { it.startsWith("Gradle") }.shouldBeEmpty()
            documents shouldNotContainKey "Gradle/README.md"
            documents.getValue("README.md") shouldNotContain "Gradle"
        }
    }
})
