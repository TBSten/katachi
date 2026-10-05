package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import me.tbsten.katachi.dsl.DeclarationKind
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException
import me.tbsten.katachi.dsl.KatachiFileConstraintDirectOnlyWithoutDirectoryException
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.metadata
import me.tbsten.katachi.test.docs.documents

/** A word of this spec's own, the way a processor brings one in: written through [owner]. */
private val Owner: MetadataKey<String> = metadata()
private var MetadataScope.owner: String? by Owner


/**
 * The hooks of `gradle { }`: a constraint or a metadata value written on one of the roles
 * `gradle()` declares, through `buildScript { }` and its seven siblings.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */
class GradleRoleConstraintSpec : FreeSpec({
    "hook と Role の対応" - {
        "8 つの hook はそれぞれ対応する Role に制約を付ける" {
            val arch = architecture {
                gradle {
                    launcherScript { fileConstraint("launcher", check = silent()) }
                    wrapperJar { fileConstraint("jar", check = silent()) }
                    wrapperProperties { fileConstraint("wrapper properties", check = silent()) }
                    settingsScript { fileConstraint("settings", check = silent()) }
                    buildScript { fileConstraint("build", check = silent()) }
                    gradleProperties { fileConstraint("properties", check = silent()) }
                    versionCatalog { fileConstraint("catalog", check = silent()) }
                    daemonJvmProperties { fileConstraint("daemon", check = silent()) }
                }
            }

            arch.declaredFileConstraints(moduleIndexOf("app")).associate { it.role.qualifiedName to it.name } shouldBe mapOf(
                "Gradle.GradleWrapper.LauncherScript" to "launcher",
                "Gradle.GradleWrapper.WrapperJar" to "jar",
                "Gradle.GradleWrapper.WrapperProperties" to "wrapper properties",
                "Gradle.SettingsScript" to "settings",
                "Gradle.BuildScript" to "build",
                "Gradle.GradleProperties" to "properties",
                "Gradle.VersionCatalog" to "catalog",
                "Gradle.DaemonJvmProperties" to "daemon",
            )
        }

        "同じ hook を 2 回書くと制約が書いた順に両方付く" {
            val arch = architecture {
                gradle {
                    buildScript { fileConstraint("a", check = silent()) }
                    buildScript { fileConstraint("b", check = silent()) }
                }
            }

            arch.declaredFileConstraints(moduleIndexOf("app")).map { it.name } shouldContainExactly listOf("a", "b")
        }

        "hook の中の制約の宣言位置は利用者が制約を書いた行を指す" {
            val line = Throwable().stackTrace.first().lineNumber + 4
            val arch = architecture {
                gradle {
                    buildScript {
                        fileConstraint("here", check = silent())
                    }
                }
            }

            arch.declaredFileConstraints(moduleIndexOf("app")).single().declaredAt shouldBe
                DeclarationSite("GradleRoleConstraintSpec.kt", line)
        }

        "hook の中に書いた metadata はその Role に載る" {
            val arch = architecture {
                gradle {
                    buildScript { owner = "platform" }
                }
            }

            arch.allRoles.single { it.qualifiedName == "Gradle.BuildScript" }[Owner] shouldBe "platform"
            arch.allRoles.filterNot { it.qualifiedName == "Gradle.BuildScript" }.forEach { it[Owner].shouldBeNull() }
            arch.allGroups.forEach { it[Owner].shouldBeNull() }
        }

        "hook に DirectOnly の制約を書くと役割の直下と同じ例外になる" {
            shouldThrow<KatachiFileConstraintDirectOnlyWithoutDirectoryException> {
                architecture {
                    gradle {
                        buildScript { fileConstraint("direct", scope = FileConstraintRange.DirectOnly, check = silent()) }
                    }
                }
            }
        }
    }

    "block の中の宣言" - {
        "gradle { } の中で宣言した Role と group は katachi の Role の後に並ぶ" {
            val arch = architecture {
                gradle {
                    "Extra".role { layout { "extra.txt".file() } }
                    "Sub".group { "Nested".role { layout { "nested.txt".file() } } }
                    buildScript { fileConstraint("build", check = silent()) }
                }
            }

            // `allRoles` lists a group's own roles before those of its nested groups, so the
            // project's role sits after katachi's five direct ones and its nested group after the wrapper's.
            arch.allRoles.map { it.qualifiedName } shouldContainExactly listOf(
                "Gradle.SettingsScript",
                "Gradle.BuildScript",
                "Gradle.GradleProperties",
                "Gradle.VersionCatalog",
                "Gradle.DaemonJvmProperties",
                "Gradle.Extra",
                "Gradle.GradleWrapper.LauncherScript",
                "Gradle.GradleWrapper.WrapperJar",
                "Gradle.GradleWrapper.WrapperProperties",
                "Gradle.Sub.Nested",
            )
            arch.allGroups.map { it.qualifiedName } shouldContainExactly listOf("Gradle", "Gradle.GradleWrapper", "Gradle.Sub")
            arch.allGroups.single { it.qualifiedName == "Gradle" }.roles.map { it.name } shouldContainExactly
                listOf("SettingsScript", "BuildScript", "GradleProperties", "VersionCatalog", "DaemonJvmProperties", "Extra")
        }

        "gradle { } の中で宣言した Role の宣言位置は書いた行を指す" {
            val line = Throwable().stackTrace.first().lineNumber + 3
            val arch = architecture {
                gradle {
                    "Extra".role { layout { "extra.txt".file() } }
                }
            }

            arch.allRoles.single { it.qualifiedName == "Gradle.Extra" }.declaredAt shouldBe
                DeclarationSite("GradleRoleConstraintSpec.kt", line)
        }

        "gradle { } の中で katachi と同名の Role を書くと重複の例外になり、先に宣言したのは katachi 側" {
            val gradleLine = Throwable().stackTrace.first().lineNumber + 4
            var thrown: KatachiDuplicateDeclarationException? = null
            try {
                architecture {
                    gradle {
                        "BuildScript".role { layout { "build.gradle.kts".file() } }
                    }
                }
            } catch (failure: KatachiDuplicateDeclarationException) {
                thrown = failure
            }

            val failure = thrown.shouldNotBeNull()
            failure.kind shouldBe DeclarationKind.Role
            failure.name shouldBe "BuildScript"
            failure.firstDeclaredAt shouldBe DeclarationSite("GradleRoleConstraintSpec.kt", gradleLine)
            failure.declaredAt shouldBe DeclarationSite("GradleRoleConstraintSpec.kt", gradleLine + 1)
        }

        "gradle() を 2 回書くと Gradle group の重複の例外になる" {
            val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
                architecture {
                    gradle()
                    gradle { buildScript { fileConstraint("twice", check = silent()) } }
                }
            }
            thrown.kind shouldBe DeclarationKind.Group
            thrown.name shouldBe "Gradle"
        }

        "制約を足しても生成ドキュメントに Gradle のページは出ない" {
            val documents = architecture {
                gradle {
                    buildScript { fileConstraint("documented nowhere", check = silent()) }
                }
                "UseCase".role { layout { "src" / "*UseCase.kt".file() } }
            }.documents()

            documents shouldContainKey "README.md"
            documents.keys.filter { it.startsWith("Gradle") }.shouldBeEmpty()
            documents shouldNotContainKey "Gradle/README.md"
            documents.getValue("README.md") shouldNotContain "Gradle"
        }
    }
})
