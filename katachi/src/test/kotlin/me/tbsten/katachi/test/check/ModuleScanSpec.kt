package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.gradle.capitalizedModuleNamePackage
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.test.fs.FakeFileSystemScope

/**
 * The module keys seen from the check, not from the flattening.
 *
 * Every other spec about `.module { }` evaluates the layout directly and hands it a module
 * index it built itself. That leaves one thing unsaid: `validate()` has to build that index
 * from the tree it was given. Until it did, a literal module path still resolved — the
 * resolver alone is enough for one — while a wildcard key quietly expanded to nothing, and
 * no spec noticed. These run the real entry point instead.
 */
class ModuleScanSpec : FreeSpec({
    /** Two feature modules and one that is only a directory, under `/repo`. */
    fun repository(block: FakeFileSystemScope.() -> Unit = {}) = repositoryOf {
        "build.gradle.kts"()
        "settings.gradle.kts"()
        "feature" {
            "home" { "build.gradle.kts"() }
            "settings" { "build.gradle.kts"() }
        }
        "core" { "data" { "build.gradle.kts"() } }
        block()
    }

    "名指ししたモジュール" - {
        "モジュールディレクトリ配下で宣言したファイルが許可される" {
            layoutArchitecture {
                ":".module { "settings.gradle".ktsFile() }
                ":core:data".module { mainSourceSet / kotlin / "Repository".ktFile() }
                ":feature:*".module { }
            }
                .validate(repository { "core" { "data" { "src/main/kotlin" { "Repository.kt"() } } } })
                .labels() shouldBe emptyList()
        }

        "解決先ディレクトリが存在しない必須モジュールは Missing になる" {
            layoutArchitecture {
                ":".module { "settings.gradle".ktsFile() }
                ":core:data".module { }
                ":core:domain".module { }
                ":feature:*".module { }
            }
                .validate(repository())
                .labels() shouldBe listOf("[MissingFile] core/domain/build.gradle.kts")
        }
    }

    "ワイルドカードのモジュールキー" - {
        // The regression guard: with an empty module index this key expands to nothing and
        // `feature` comes back as one [UnexpectedDirectory].
        "実在するモジュールに展開されて検査される" {
            layoutArchitecture {
                ":".module { "settings.gradle".ktsFile() }
                ":core:*".module { }
                ":feature:*".module {
                    mainSourceSet / kotlin / "${wildcards[0].pascalCase}Screen".ktFile()
                }
            }
                .validate(
                    repository {
                        "feature" {
                            "home" { "src/main/kotlin" { "HomeScreen.kt"() } }
                            "settings" { "src/main/kotlin" { "SettingsScreen.kt"() } }
                        }
                    },
                )
                .labels() shouldBe emptyList()
        }

        "捕捉値と噛み合わない名前のファイルは Unexpected になる" {
            layoutArchitecture {
                ":".module { "settings.gradle".ktsFile() }
                ":core:*".module { }
                ":feature:*".module {
                    mainSourceSet / kotlin / "${wildcards[0].pascalCase}Screen".ktFile()
                }
            }
                .validate(
                    repository {
                        "feature" {
                            "home" { "src/main/kotlin" { "HomeScreen.kt"(); "ProfileScreen.kt"() } }
                            "settings" { "src/main/kotlin" { "SettingsScreen.kt"() } }
                        }
                    },
                )
                .labels() shouldBe
                listOf("[UnexpectedFile] feature/home/src/main/kotlin/ProfileScreen.kt")
        }

        "マッチが0件でも Missing にならない" {
            layoutArchitecture {
                ":".module { "build.gradle".ktsFile(); "settings.gradle".ktsFile() }
                ":nothing:*".module { mainSourceSet / kotlin / "Anything".ktFile() }
            }
                .validate(repositoryOf { "build.gradle.kts"(); "settings.gradle.kts"() })
                .labels() shouldBe emptyList()
        }
    }

    "差し替えた moduleResolver" - {
        "規約と異なるディレクトリを配置場所として検査できる" {
            val architecture = architectureOf {
                // `:feature:home` lives in `features/home`, the way a `projectDir` override
                // in settings.gradle.kts puts it there.
                moduleResolver = ModuleResolver { module ->
                    module.segments.joinToString("/") { if (it == "feature") "features" else it }
                }
                "app".group { "Screen" { layout { ":feature:home".module { "Home".ktFile() } } } }
            }

            architecture
                .validate(
                    repositoryOf {
                        "features" { "home" { "build.gradle.kts"(); "Home.kt"() } }
                    },
                )
                .labels() shouldBe emptyList()
        }
    }

    "modulePackage" - {
        "assert の時点でモジュールごとに解決される" {
            val modulePackage = capitalizedModuleNamePackage("com.example")

            layoutArchitecture {
                ":".module { "settings.gradle".ktsFile() }
                // This role really does live in two places, so both say which one to use — a
                // `layout { }` with two of them and no `description` is a `MissingDescription`
                // warning, and this spec is about `modulePackage`, not about that.
                ":feature:*".module {
                    description = "One feature's own screen"
                    mainSourceSet / kotlin / modulePackage / "Screen".ktFile()
                }
                ":core:*".module {
                    description = "What more than one feature reads through"
                    mainSourceSet / kotlin / modulePackage / "Repository".ktFile()
                }
            }
                .validate(
                    repository {
                        "feature" {
                            "home" { "src/main/kotlin/com/example/feature/home" { "Screen.kt"() } }
                            "settings" { "src/main/kotlin/com/example/feature/settings" { "Screen.kt"() } }
                        }
                        "core" { "data" { "src/main/kotlin/com/example/core/data" { "Repository.kt"() } } }
                    },
                )
                .labels() shouldBe emptyList()
        }
    }
})
