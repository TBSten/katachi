package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.camelCase
import me.tbsten.katachi.dsl.flatCase
import me.tbsten.katachi.dsl.kebabCase
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.screamingSnakeCase
import me.tbsten.katachi.dsl.snakeCase
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

/** A tree with a feature module that holds a file of the role, a file of no role and a missing directory's sibling. */
private fun featureTree(): FakeFileSystem = repositoryOf {
    "feature" {
        "home" {
            "build.gradle.kts"()
            "src" { "HomeViewModel.kt"(); "Stray.txt"() }
        }
        "settings" {
            "build.gradle.kts"()
            "src" { "SettingsViewModel.kt"(); "OtherViewModel.kt"() }
            "extra" { "Note.kt"() }
        }
    }
    "docs" {
        "home" { "HomeViewModel.kt"(); "README.md"() }
        "deep" { "nested" { "X.kt"() } }
    }
}

/**
 * A tree whose `feature/<module>/pkg/<package>/` levels agree for `home` and disagree for the
 * `settings` package inside the `home` module, with file names that follow and do not follow the
 * feature name.
 */
private fun boundTree(): FakeFileSystem = repositoryOf {
    "feature" {
        "home" {
            "pkg" {
                "home" { "HomeScreen.kt"(); "ProfileScreen.kt"() }
                "settings" { "HomeScreen.kt"() }
            }
        }
        "my-page" { "pkg" { "my-page" { "MyPageScreen.kt"() } } }
    }
}

/** The violations [block] finds in [featureTree], as report lines. */
private fun violationsOf(block: LayoutScope.() -> Unit): List<String> =
    layoutArchitecture(block = block).validate(featureTree()).labels().sorted()

/** The violations [block] finds in [tree], as report lines. */
private fun violationsIn(tree: FakeFileSystem, block: LayoutScope.() -> Unit): List<String> =
    layoutArchitecture(block = block).validate(tree).labels().sorted()

/** The files [role] owns once [arch] is checked against [tree]. */
private fun filesOf(arch: Architecture, tree: FakeFileSystem, role: String): List<String> =
    arch.process(tree) { context -> context.filesOf(context.architecture.allRoles.single { it.name == role }) }

/**
 * A named wildcard checks exactly as the `*` it names: the same tree gives the same violations,
 * line for line. [me.tbsten.katachi.test.dsl.LayoutCaptureSpec] compares the flattened entries;
 * this compares what the check actually reports over a fake file system.
 */
class CaptureCheckSpec : FreeSpec({
    "capture(...) で名前を付けても、検査の違反の列は * と同じ" - {
        "パスの capture" {
            val named = violationsOf { "docs" / capture("feature") / "*ViewModel".ktFile() }
            val plain = violationsOf { "docs" / "*" / "*ViewModel".ktFile() }

            named.shouldNotBeEmpty()
            named shouldBe plain
        }

        "ブロックとして書いたパスの capture" {
            val named = violationsOf { "docs" { capture("feature") { "*ViewModel".ktFile() } } }
            val plain = violationsOf { "docs" { "*" { "*ViewModel".ktFile() } } }

            named.shouldNotBeEmpty()
            named shouldBe plain
        }
    }

    "同じ名前を2回使ったパスは値が一致するファイルだけを許す" - {
        "値が一致しないファイルは CaptureMismatch になり UnexpectedFile にはならない" {
            val violations = violationsIn(boundTree()) {
                "feature" / capture("feature") / "pkg" / capture("feature") / "*Screen".ktFile()
            }

            violations shouldBe listOf("[CaptureMismatch] feature/home/pkg/settings/HomeScreen.kt")
        }

        "名前が1回だけなら同じ木でも違反は出ない" {
            violationsIn(boundTree()) {
                "feature" / capture("feature") / "pkg" / "*" / "*Screen".ktFile()
            } shouldBe emptyList()
        }

        "一致しないファイルを別の役割が許していれば違反にならない" {
            val arch = architectureOf {
                "app".group {
                    "Screen".role {
                        layout { "feature" / capture("feature") / "pkg" / capture("feature") / "*Screen".ktFile() }
                    }
                    "Legacy".role { layout { "feature" / "*" / "pkg" / "settings" / "*Screen".ktFile() } }
                }
            }

            arch.validate(boundTree()).labels() shouldBe emptyList()
            filesOf(arch, boundTree(), "Legacy") shouldBe listOf("feature/home/pkg/settings/HomeScreen.kt")
        }

        "同じパスを束縛付きと束縛なしで2回宣言したら束縛なしの宣言が許す" {
            violationsIn(boundTree()) {
                "feature" / capture("feature") / "pkg" / capture("feature") / "*Screen".ktFile()
                "feature" / "*" / "pkg" / "*" / "*Screen".ktFile()
            } shouldBe emptyList()
        }

        "anyFile() のディレクトリの束縛もファイルごとに検査する" {
            val tree = repositoryOf {
                "docs" { "home" { "home" { "a.md"(); "b.md"() }; "settings" { "c.md"() } } }
            }

            violationsIn(tree) { "docs" / capture("x") / capture("x") { anyFile() } } shouldBe
                listOf("[CaptureMismatch] docs/home/settings/c.md")
        }

        "ブロックで開いたディレクトリの名前と中のパスの名前の束縛は、開いたディレクトリ名で比べる" {
            val tree = repositoryOf {
                "feature/home" { "src" { "home" { "A.kt"() }; "settings" { "B.kt"() } } }
            }

            violationsIn(tree) {
                "feature" / capture("feature") { "src" / capture("feature") / "*.kt".file() }
            } shouldBe listOf("[CaptureMismatch] feature/home/src/settings/B.kt")
        }

        "一致しないファイルは filesOf(role) に入らない" {
            val arch = layoutArchitecture(role = "Screen") {
                "feature" / capture("feature") / "pkg" / capture("feature") / "*Screen".ktFile()
            }

            filesOf(arch, boundTree(), "Screen") shouldBe listOf(
                "feature/home/pkg/home/HomeScreen.kt",
                "feature/home/pkg/home/ProfileScreen.kt",
                "feature/my-page/pkg/my-page/MyPageScreen.kt",
            )
        }
    }

    "派生は変換後の値と等しいファイルだけを許す" - {
        "pascalCase のファイル名" {
            violationsIn(boundTree()) {
                "feature" / capture("feature") / "pkg" / capture("feature") / "${capture("feature").pascalCase}Screen".ktFile()
            } shouldBe listOf(
                "[CaptureMismatch] feature/home/pkg/home/ProfileScreen.kt",
                "[CaptureMismatch] feature/home/pkg/settings/HomeScreen.kt",
            )
        }

        listOf(
            Triple("camelCase", "myPage", { s: String -> s.camelCase }),
            Triple("pascalCase", "MyPage", { s: String -> s.pascalCase }),
            Triple("kebabCase", "my-page", { s: String -> s.kebabCase }),
            Triple("snakeCase", "my_page", { s: String -> s.snakeCase }),
            Triple("screamingSnakeCase", "MY_PAGE", { s: String -> s.screamingSnakeCase }),
            Triple("flatCase", "mypage", { s: String -> s.flatCase }),
        ).forEach { (spelling, converted, convert) ->
            "$spelling は my-page を $converted にしたディレクトリだけを許す" {
                val tree = repositoryOf {
                    "feature" { "my-page" { converted { "a.kt"() }; "other" { "b.kt"() } } }
                }

                violationsIn(tree) { "feature" / capture("m") / convert(capture("m")) / "*.kt".file() } shouldBe
                    listOf("[CaptureMismatch] feature/my-page/other/b.kt")
            }
        }

        "同じ名前に別々の変換を書いても出現ごとに比べる" {
            val tree = repositoryOf {
                "my-page" { "myPage" { "MyPage.kt"(); "Other.kt"() } }
            }

            violationsIn(tree) {
                capture("m") / capture("m").camelCase / "${capture("m").pascalCase}.kt".file()
            } shouldBe listOf("[CaptureMismatch] my-page/myPage/Other.kt")
        }

        "部分一致の派生はファイル名の * の部分だけを比べる" {
            val tree = repositoryOf {
                "home" { "HomeScreen.kt"(); "HomeViewModel.kt"(); "SettingsScreen.kt"() }
            }

            violationsIn(tree) {
                capture("f") / "${capture("f").pascalCase}Screen".ktFile()
                capture("f") / "*ViewModel".ktFile()
            } shouldBe listOf("[CaptureMismatch] home/SettingsScreen.kt")
        }

        "派生の元がブロックで開いたディレクトリの名前でも比べる" {
            val tree = repositoryOf {
                "feature/home" { "src" { "HomeScreen.kt"(); "CartScreen.kt"() } }
            }

            violationsIn(tree) {
                "feature" / capture("f") { "src" / "${capture("f").pascalCase}Screen".ktFile() }
            } shouldBe listOf("[CaptureMismatch] feature/home/src/CartScreen.kt")
        }
    }
})
