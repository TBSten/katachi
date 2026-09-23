package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.KatachiUnresolvableModulePatternException
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.wildcards
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.scan.UncheckedDirectory
import me.tbsten.katachi.scan.UncheckedDirectoryReason
import me.tbsten.katachi.test.fs.failingAt
import me.tbsten.katachi.test.fs.failingToExistAt
import me.tbsten.katachi.test.fs.failingToListAt

/**
 * The search for the project's modules carrying on past a directory it could not read.
 *
 * It runs before the walk and used to catch nothing, so one unreadable subdirectory threw a
 * raw `IOException` out of `validate()`: no violations, no report, and katachi's own name
 * nowhere in the stack trace. The specs below fix the two halves of the answer — the modules
 * of the sibling subtrees are still found, and the directory that failed is reported.
 *
 * `exists` is what breaks the search alone: of the three operations, it is the only one the
 * walk never calls, so a spec using [failingToExistAt] sees the search's own answer with
 * nothing of the walk's mixed in.
 */
class ModuleDiscoveryFailureSpec : FreeSpec({
    /** Two feature modules and one core module, all found by their build file. */
    val definition = layoutArchitecture {
        ":".module { "settings.gradle".ktsFile() }
        ":core:*".module { }
        ":feature:*".module { mainSourceSet / kotlin / "${wildcards[0].pascalCase}Screen".ktFile() }
    }
    val tree = repositoryOf {
        "settings.gradle.kts"()
        "build.gradle.kts"()
        "feature" {
            "cart" { "build.gradle.kts"(); "src/main/kotlin" { "CartScreen.kt"() } }
            "home" { "build.gradle.kts"(); "src/main/kotlin" { "HomeScreen.kt"() } }
        }
        "core" { "data" { "build.gradle.kts"() } }
    }

    /**
     * The same project named module by module.
     *
     * A key without a wildcard resolves without the search having found anything, so the walk
     * keeps looking into `feature/home` however the search went. That is what leaves a failed
     * search as the only violation of the run.
     */
    val namedDefinition = layoutArchitecture {
        ":".module { "settings.gradle".ktsFile() }
        ":feature:home".module { }
    }
    val namedTree = repositoryOf {
        "settings.gradle.kts"()
        "build.gradle.kts"()
        "feature" { "home" { "build.gradle.kts"() } }
    }
    val homeSettingsFile = "/repo/feature/home/settings.gradle.kts"

    "偽陽性" - {
        "1つも落ちないプロジェクトでは探索由来の違反が出ない" {
            definition.validate(tree).labels().shouldBeEmpty()
        }

        "名指しのモジュールでも探索由来の違反が出ない" {
            namedDefinition.validate(namedTree).labels().shouldBeEmpty()
        }

        "報告に検査できなかった件数の行が出ない" {
            definition.validate(tree).report() shouldNotContain "could not be checked."
        }
    }

    "隣のサブツリーは独立して答える" - {
        "exists が落ちてもその子だけの失敗で済む" {
            definition
                .validate(tree.failingToExistAt("/repo/feature/home/build.gradle.kts") { IOException("cannot read it") })
                .labels() shouldBe listOf(
                // `feature/cart` と `core/data` は見つかっているので、そこからは何も出ない。
                // `feature/home` はモジュールとして数えられなかったので、走査からは未知に見える。
                "[UnexpectedDirectory] feature/home",
                "[UncheckedDirectory] feature/home",
            )
        }

        "isDirectory が落ちてもその子だけの失敗で済む" {
            definition
                .validate(tree.failingAt("/repo/feature/home") { IOException("cannot classify it") })
                .labels() shouldBe listOf(
                "[UncheckedDirectory] feature/home",
                "[UncheckedFile] feature/home",
            )
        }

        "list が落ちてもそのディレクトリ1つの失敗で済む" {
            definition
                .validate(tree.failingToListAt("/repo/feature") { IOException("cannot list it") })
                .labels() shouldBe listOf(
                // `core/data` は別のサブツリーなので見つかったまま。
                "[UnexpectedDirectory] feature",
                "[UncheckedDirectory] feature",
            )
        }
    }

    "reason" - {
        "探索の失敗は ModulesNotDiscovered になる" {
            val unchecked = namedDefinition
                .validate(namedTree.failingToExistAt(homeSettingsFile) { IOException("cannot read it") })
                .filterIsInstance<UncheckedDirectory>()
                .single()

            unchecked.path shouldBe "feature/home"
            unchecked.reason shouldBe UncheckedDirectoryReason.ModulesNotDiscovered
            unchecked.cause.shouldBeInstanceOf<IOException>()
        }

        "走査の失敗は NotWalked のまま" {
            // One tree, both readings of it failing at the same directory: the search cannot
            // tell whether it is a module, and the walk cannot look inside it.
            namedDefinition
                .validate(namedTree.failingToListAt("/repo/feature/home") { IOException("cannot list it") })
                .filterIsInstance<UncheckedDirectory>()
                .map { it.path to it.reason } shouldBe listOf(
                "feature/home" to UncheckedDirectoryReason.ModulesNotDiscovered,
                "feature/home" to UncheckedDirectoryReason.NotWalked,
            )
        }
    }

    "報告" - {
        "探索の失敗のブロックはモジュールキーの話をする" {
            namedDefinition
                .validate(namedTree.failingToExistAt(homeSettingsFile) { IOException("cannot read it") })
                .report() shouldBe
                """
                Katachi check failed: 1 violation (Failed: 1)

                [UncheckedDirectory] feature/home
                  Katachi failed while looking for modules here, so a module key may have expanded to fewer modules than the project has.
                  Cause: java.io.IOException: cannot read it

                  How to fix:
                    - Check that the directory is readable, then run the check again
                    - If it is, report this at https://github.com/TBSten/katachi/issues with the cause above

                1 directory could not be checked.
                """.trimIndent()
        }

        "末尾の件数行に乗る" {
            definition
                .validate(tree.failingToListAt("/repo/feature") { IOException("cannot list it") })
                .report()
                .lines()
                .last() shouldBe "1 directory could not be checked."
        }

        "打ち切られてもその行は消えない" {
            definition
                .validate(tree.failingToListAt("/repo/feature") { IOException("cannot list it") })
                .report(maxViolations = 1)
                .lines()
                .last() shouldBe "1 directory could not be checked."
        }
    }

    "握ってはいけない例外" - {
        "VirtualMachineError はそのまま投げられる" {
            shouldThrow<StackOverflowError> {
                namedDefinition.validate(namedTree.failingToExistAt(homeSettingsFile) { StackOverflowError() })
            }
        }

        "LinkageError はそのまま投げられる" {
            shouldThrow<NoClassDefFoundError> {
                namedDefinition.validate(namedTree.failingToExistAt(homeSettingsFile) { NoClassDefFoundError("SomeClass") })
            }
        }

        "InterruptedException はそのまま投げられる" {
            shouldThrow<InterruptedException> {
                namedDefinition.validate(namedTree.failingToExistAt(homeSettingsFile) { InterruptedException("stop") })
            }
        }

        "AssertionError はそのまま投げられる" {
            shouldThrow<KatachiArchitectureAssertionError> {
                namedDefinition.validate(
                    namedTree.failingToExistAt(homeSettingsFile) {
                        KatachiArchitectureAssertionError(violations = emptyList(), maxViolations = 1)
                    },
                )
            }
        }
    }

    "握ってよい例外" - {
        "KatachiInternalException は UncheckedDirectory になる" {
            namedDefinition
                .validate(
                    namedTree.failingToExistAt(homeSettingsFile) {
                        KatachiUnresolvableModulePatternException(":app")
                    },
                )
                .labels() shouldContain "[UncheckedDirectory] feature/home"
        }
    }
})
