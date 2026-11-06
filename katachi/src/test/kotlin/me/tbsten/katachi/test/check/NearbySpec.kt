@file:Suppress("DEPRECATION_ERROR")

package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.UnexpectedFile
import me.tbsten.katachi.check.internal.LayoutIndex
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * What `[UnexpectedFile]` offers as nearby locations: every declared place with the file's
 * path filled into its wildcards, ordered by how far the file is from it, three at most.
 *
 * The ranking is asked of the index directly, with the entries the definition flattens to,
 * because the walk reports a file inside an undeclared directory as that directory rather
 * than as the file -- and the cases worth pinning are exactly a `component2` beside a
 * declared `component`. One test at the end goes through `validate()` so that the wiring
 * from the walk to the index is covered too.
 *
 * A test that says a place is *not* a candidate always keeps a place that is, so that an
 * empty answer for an unrelated reason cannot pass it.
 */
class NearbySpec : FreeSpec({
    "ワイルドカードを含む置き場所" - {
        "ワイルドカードを含む置き場所は違反ファイルのパスで具体化して候補になる" {
            featureDefinition().nearbyOf(
                "feature/home/src/main/kotlin/com/example/sample/feature/home/component2/HomeUserCard.kt",
            ) shouldBe listOf(
                Triple("feature.FeatureComponent", "$HOME_SOURCES/component", "*.kt"),
                Triple("feature.Screen", HOME_SOURCES, "*Screen.kt"),
                Triple("feature.ViewModel", HOME_SOURCES, "*ViewModel.kt"),
            )
        }

        "segment の一部だけがワイルドカードの置き場所は当たらない名前を埋めて具体化する" {
            val definition = architectureOf {
                "feature".group {
                    "Screen".role {
                        layout { "feature" / "ui-*" / "src/main/kotlin/com/example/sample/feature" / "*" / "*Screen".ktFile() }
                    }
                }
            }

            definition.nearbyOf("feature/home/src/main/kotlin/com/example/sample/feature/home/HomeScreen.kt") shouldBe
                listOf(Triple("feature.Screen", "feature/ui-home/src/main/kotlin/com/example/sample/feature/home", "*Screen.kt"))
        }

        "ワイルドカードを埋められないほど浅いファイルには候補が無い" {
            val definition = architectureOf {
                "feature".group {
                    "Screen".role { layout { "feature" / "*" / "src/main/kotlin" / "*Screen".ktFile() } }
                }
                gradle()
            }

            definition.nearbyOf("feature/Stray.kt").shouldBeEmpty()
            definition.nearbyOf("feature/home/Stray.kt") shouldBe
                listOf(Triple("feature.Screen", "feature/home/src/main/kotlin", "*Screen.kt"))
        }

        "** は残りの階層数に合わせて具体化する" {
            val definition = architectureOf {
                "testing".group {
                    "Test".role { layout { "src/test/kotlin" / "**" / "*Test".ktFile() } }
                    "Resource".role { layout { "src" / "**" / "resources" / "*.properties".file() } }
                }
            }

            definition.nearbyOf("src/main/kotlin/com/example/Application.kt") shouldBe
                listOf(Triple("testing.Test", "src/test/kotlin/com/example", "*Test.kt"))
            definition.nearbyOf("src/main/Shallow.kt") shouldBe
                listOf(Triple("testing.Test", "src/test/kotlin", "*Test.kt"))
            definition.nearbyOf("src/main/kotlin/com/example/app.properties") shouldBe
                listOf(Triple("testing.Resource", "src/main/kotlin/com/resources", "*.properties"))
        }

        "** が 2 つある置き場所は候補にならない" {
            val definition = architectureOf {
                "testing".group {
                    "Deep".role { layout { "src" / "**" / "test" / "**" / "*.kt".file() } }
                    "Shallow".role { layout { "src" / "**" / "*.kt".file() } }
                }
            }

            definition.nearbyOf("src/test/Foo.kt") shouldBe
                listOf(Triple("testing.Shallow", "src/test", "*.kt"))
        }
    }

    "並び" - {
        "ディレクトリ名が 1 文字違うだけの置き場所が先頭に出る" {
            featureDefinition().nearbyOf(
                "core/domain/src/main/kotlin/com/example/sample/usecase/GetUserUseCase.kt",
            ) shouldBe listOf(
                Triple("domain.UseCase", "$DOMAIN_SOURCES/useCase", "*UseCase.kt"),
                Triple("domain.Repository", "$DOMAIN_SOURCES/repository", "*Repository.kt"),
            )
        }

        "置き場所の 1 階層下に宣言がある Role が親ディレクトリの Role より先に出る" {
            featureDefinition().nearbyOf("app/src/main/kotlin/com/example/sample/di/AppModule.kt") shouldBe listOf(
                Triple("app.DiModule", "$APP_SOURCES/di/module", "*Module.kt"),
                Triple("app.ActivityEntrypoint", APP_SOURCES, "MainActivity.kt"),
            )
        }

        "別のモジュールの同じ Role の置き場所が同点なら宣言順に並ぶ" {
            // `settings` against `home` is the same substitution for both FeatureTest places,
            // so the two tie on every distance and keep the order they were declared in.
            // Screen fills its trailing `*` with the file's own `settings`, so it is one level
            // (`test` against `main`) away and comes third.
            featureDefinition().nearbyOf(
                "feature/home/src/test/kotlin/com/example/sample/feature/settings/HomeViewModelTest.kt",
            ) shouldBe listOf(
                Triple("feature.FeatureTest", "feature/home/src/test/kotlin/com/example/sample/feature/home", "*Test.kt"),
                Triple("feature.FeatureTest", "feature/settings/src/test/kotlin/com/example/sample/feature/settings", "*Test.kt"),
                Triple("feature.Screen", "feature/home/src/main/kotlin/com/example/sample/feature/settings", "*Screen.kt"),
            )
        }

        "ディレクトリが同じで名前だけ合わない Role は距離 0 で先頭に出る" {
            featureDefinition().nearbyOf("feature/home/src/main/kotlin/com/example/sample/feature/home/Foo.kt") shouldBe
                listOf(
                    Triple("feature.Screen", HOME_SOURCES, "*Screen.kt"),
                    Triple("feature.ViewModel", HOME_SOURCES, "*ViewModel.kt"),
                    Triple("feature.FeatureComponent", "$HOME_SOURCES/component", "*.kt"),
                )
        }

        "今までの例と同じ並びになる" {
            // The example `ViolationReportSpec` prints: both one level away by name alone, the
            // shorter directory name first.
            domainDefinition().nearbyOf("core/domain/TokenRefresher.kt") shouldBe listOf(
                Triple("domain.UseCase", "core/domain/useCase", "*UseCase.kt"),
                Triple("domain.Repository", "core/domain/repository", "*Repository.kt"),
            )
        }

        "同じ Role の同じディレクトリは組が最小の 1 件にまとまりパターンもその 1 件のものになる" {
            val definition = architectureOf {
                "domain".group {
                    "UseCase".role {
                        layout {
                            "core/domain/useCase" / "GetUserUseCase".ktFile()
                            "core/domain/useCase" / "*".ktFile()
                        }
                    }
                    "Repository".role { layout { "core/domain/repository" / "*Repository".ktFile() } }
                }
            }

            definition.nearbyOf("core/domain/useCases/Foo.kt") shouldBe listOf(
                Triple("domain.UseCase", "core/domain/useCase", "*.kt"),
                Triple("domain.Repository", "core/domain/repository", "*Repository.kt"),
            )
        }

        "候補は 3 件まで" {
            val definition = architectureOf {
                "domain".group {
                    for (name in listOf("UseCase", "Repository", "Service", "Mapper", "Model")) {
                        name.role { layout { "core/domain" / "*$name".ktFile() } }
                    }
                }
            }

            val nearby = definition.nearbyOf("core/domain/Foo.kt")

            nearby shouldHaveSize 3
            nearby.map { it.first } shouldBe listOf("domain.UseCase", "domain.Repository", "domain.Service")
        }
    }

    "候補にならない置き場所" - {
        "ファイルの拡張子を受け取れない置き場所は候補にならない" {
            // Diagram is declared before UseCase in the very directory the file sits in: if
            // the extension were not looked at, it would tie with UseCase and come first.
            val definition = architectureOf {
                "docs".group {
                    "Diagram".role { layout { "core/domain/src/main/kotlin/com/example/sample/useCase" / "*.puml".file() } }
                }
                "domain".group {
                    "UseCase".role { layout { "core/domain/src/main/kotlin/com/example/sample/useCase" / "*UseCase".ktFile() } }
                }
                "build".group {
                    "Convention".role { layout { "core/domain" / "build.gradle.kts".file() } }
                }
            }

            definition.nearbyOf("core/domain/src/main/kotlin/com/example/sample/useCase/Notes.kt") shouldBe
                listOf(Triple("domain.UseCase", "core/domain/src/main/kotlin/com/example/sample/useCase", "*UseCase.kt"))
        }

        "拡張子の位置がワイルドカードの置き場所はどの拡張子も受ける" {
            val definition = featureDefinition {
                "docs".group {
                    "Changelog".role { layout { "feature" / "*" / "CHANGELOG".file() } }
                    "FeatureReadme".role { layout { "feature" / "*" / "README*".file() } }
                }
            }

            definition.nearbyOf("feature/home/README.md") shouldBe
                listOf(Triple("docs.FeatureReadme", "feature/home", "README*"))
        }

        "拡張子の無い名前だけの置き場所は拡張子の無いファイルだけ受ける" {
            val definition = architectureOf {
                "docs".group {
                    "License".role { layout { "feature" / "*" / "LICENSE".file() } }
                    "FeatureReadme".role { layout { "feature" / "*" / "README*".file() } }
                }
            }

            definition.nearbyOf("feature/home/NOTICE") shouldBe listOf(
                Triple("docs.License", "feature/home", "LICENSE"),
                Triple("docs.FeatureReadme", "feature/home", "README*"),
            )
            definition.nearbyOf("feature/home/NOTICE.txt") shouldBe
                listOf(Triple("docs.FeatureReadme", "feature/home", "README*"))
        }

        "module の糖衣と gradle() が足した build.gradle.kts の置き場所は候補にならない" {
            // Both sugars put `app/build.gradle.kts` next to the file, which would be the
            // nearest place of all; nobody wrote that line, so it is not offered.
            val definition = architectureOf {
                "app".group {
                    "Main".role { layout { ":app".module { "src/main/kotlin" / "*".ktFile() } } }
                }
                "build".group {
                    "Convention".role { layout { "app/conventions" / "*.gradle.kts".file() } }
                }
                gradle()
            }

            definition.nearbyOf("app/extra.gradle.kts") shouldBe
                listOf(Triple("build.Convention", "app/conventions", "*.gradle.kts"))
        }

        "先頭の階層を共有しない置き場所は候補にならない" {
            val definition = architectureOf {
                "app".group { "Main".role { layout { "app/src" / "*".ktFile() } } }
                "domain".group { "UseCase".role { layout { "core/domain/useCase" / "*UseCase".ktFile() } } }
            }

            definition.nearbyOf("core/domain/Foo.kt") shouldBe
                listOf(Triple("domain.UseCase", "core/domain/useCase", "*UseCase.kt"))
        }

        "ルート直下のファイルには候補が無い" {
            domainDefinition().nearbyOf("Stray.kt").shouldBeEmpty()
            domainDefinition().nearbyOf("core/Stray.kt") shouldBe listOf(
                Triple("domain.UseCase", "core/domain/useCase", "*UseCase.kt"),
                Triple("domain.Repository", "core/domain/repository", "*Repository.kt"),
            )
        }
    }

    "validate の UnexpectedFile にも同じ並びで載る" {
        val tree = repositoryOf {
            "core/domain" {
                "TokenRefresher.kt"()
                "useCase" { "GetUserUseCase.kt"() }
                "repository" { "UserRepository.kt"() }
            }
        }

        val unexpected = domainDefinition().validate(tree).filterIsInstance<UnexpectedFile>().single()

        unexpected.path shouldBe "core/domain/TokenRefresher.kt"
        unexpected.nearby.map { Triple(it.role.qualifiedName, it.directory, it.filePattern) } shouldBe listOf(
            Triple("domain.UseCase", "core/domain/useCase", "*UseCase.kt"),
            Triple("domain.Repository", "core/domain/repository", "*Repository.kt"),
        )
        unexpected.nearby.map { it.toString() } shouldBe listOf(
            "domain.UseCase -> core/domain/useCase/*UseCase.kt",
            "domain.Repository -> core/domain/repository/*Repository.kt",
        )
    }
})

private const val HOME_SOURCES = "feature/home/src/main/kotlin/com/example/sample/feature/home"
private const val APP_SOURCES = "app/src/main/kotlin/com/example/sample"
private const val DOMAIN_SOURCES = "core/domain/src/main/kotlin/com/example/sample"

/** The nearby locations of [file], as `(role, directory, filePattern)`. */
private fun Architecture.nearbyOf(file: String): List<Triple<String, String, String?>> =
    LayoutIndex(flattenLayout()).nearbyOf(file).map { Triple(it.role.qualifiedName, it.directory, it.filePattern) }

/**
 * The definition the design's worked cases are written against: a feature group written
 * with wildcards, two module-by-module places for tests, and `gradle()` for the synthetic
 * `build.gradle.kts` of every module.
 */
private fun featureDefinition(more: ArchitectureScope.() -> Unit = {}): Architecture = architectureOf {
    "feature".group {
        "Screen".role { layout { "feature" / "*" / FEATURE_PACKAGE / "*" / "*Screen".ktFile() } }
        "ViewModel".role { layout { "feature" / "*" / FEATURE_PACKAGE / "*" / "*ViewModel".ktFile() } }
        "FeatureComponent".role { layout { "feature" / "*" / FEATURE_PACKAGE / "*" / "component" / "*".ktFile() } }
        "FeatureTest".role {
            layout {
                "feature/home/src/test/kotlin/com/example/sample/feature/home" / "*Test".ktFile()
                "feature/settings/src/test/kotlin/com/example/sample/feature/settings" / "*Test".ktFile()
            }
        }
    }
    "app".group {
        "DiModule".role { layout { "$APP_SOURCES/di/module" / "*Module".ktFile() } }
        "ActivityEntrypoint".role { layout { APP_SOURCES / "MainActivity".ktFile() } }
    }
    "domain".group {
        "UseCase".role { layout { "$DOMAIN_SOURCES/useCase" / "*UseCase".ktFile() } }
        "Repository".role { layout { "$DOMAIN_SOURCES/repository" / "*Repository".ktFile() } }
    }
    gradle()
    more()
}

private const val FEATURE_PACKAGE = "src/main/kotlin/com/example/sample/feature"

/** The definition `ViolationReportSpec` prints the example block from. */
private fun domainDefinition(): Architecture = architectureOf {
    "domain".group {
        "UseCase".role { layout { "core/domain/useCase" / "*UseCase".ktFile() } }
        "Repository".role { layout { "core/domain/repository" / "*Repository".ktFile() } }
    }
}
