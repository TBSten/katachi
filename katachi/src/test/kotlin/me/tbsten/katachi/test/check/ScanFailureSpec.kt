package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.assert
import me.tbsten.katachi.check.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.KatachiUnresolvableModulePatternException
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.scan.UncheckedFile
import me.tbsten.katachi.test.fs.ThrowingFileSystem
import me.tbsten.katachi.test.fs.failingAt
import me.tbsten.katachi.test.fs.failingToListAt

/**
 * The traversal carrying on past a path it could not check.
 *
 * Everything lives under `src/`, which module discovery never walks into, so the only thing
 * a failing path affects is the walk itself — which is what these specs are about.
 */
class ScanFailureSpec : FreeSpec({
    val definition = layoutArchitecture {
        "src" {
            "App".ktFile()
            "Missing".ktFile()
        }
    }
    val tree = repositoryOf {
        "src" {
            "App.kt"()
            "Broken.kt"()
            "notes.md"()
            "other.md"()
        }
    }
    val brokenFile = "/repo/src/Broken.kt"

    "ファイル単位" - {
        "何も投げなければ Broken.kt も普通の違反として報告される" {
            definition.validate(tree).labels() shouldBe listOf(
                "[UnexpectedFile] src/Broken.kt",
                "[UnexpectedFile] src/notes.md",
                "[UnexpectedFile] src/other.md",
                "[MissingFile] src/Missing.kt",
            )
        }

        "1ファイルで例外が出ても残りの違反はすべて報告される" {
            definition
                .validate(tree.failingAt(brokenFile) { IOException("cannot read it") })
                .labels() shouldBe listOf(
                "[UnexpectedFile] src/notes.md",
                "[UnexpectedFile] src/other.md",
                "[MissingFile] src/Missing.kt",
                "[UncheckedFile] src/Broken.kt",
            )
        }

        "例外が出たファイルは UncheckedFile として握った例外つきで報告される" {
            val violations = definition
                .validate(tree.failingAt(brokenFile) { IOException("cannot read it") })

            val unchecked = violations.filterIsInstance<UncheckedFile>().single()
            unchecked.path shouldBe "src/Broken.kt"
            unchecked.cause.shouldBeInstanceOf<IOException>()
            violations.report() shouldContain "[UncheckedFile] src/Broken.kt"
        }
    }

    "ディレクトリ単位" - {
        val siblingDefinition = layoutArchitecture {
            "src" {
                "broken" { anyFile() }
                "other" { "App".ktFile() }
            }
        }
        val siblingTree = repositoryOf {
            "src" {
                "broken" { "a.kt"() }
                "other" { "App.kt"(); "notes.md"() }
            }
        }

        "list が失敗しても兄弟ディレクトリの走査は続く" {
            siblingDefinition
                .validate(siblingTree.failingToListAt("/repo/src/broken") { IOException("cannot list it") })
                .labels() shouldBe listOf(
                "[UnexpectedFile] src/other/notes.md",
                "[UncheckedDirectory] src/broken",
            )
        }
    }

    "握ってはいけない例外" - {
        "StackOverflowError はそのまま投げられる" {
            shouldThrow<StackOverflowError> {
                definition.validate(tree.failingAt(brokenFile) { StackOverflowError() })
            }
        }

        "KatachiArchitectureAssertionError はそのまま投げられる" {
            shouldThrow<KatachiArchitectureAssertionError> {
                definition.validate(
                    tree.failingAt(brokenFile) {
                        KatachiArchitectureAssertionError(violations = emptyList(), maxViolations = 1)
                    },
                )
            }
        }

        "LinkageError はそのまま投げられる" {
            shouldThrow<NoClassDefFoundError> {
                definition.validate(tree.failingAt(brokenFile) { NoClassDefFoundError("SomeClass") })
            }
        }
    }

    "握ってよい例外" - {
        "KatachiInternalException は UncheckedFile になる" {
            definition
                .validate(tree.failingAt(brokenFile) { KatachiUnresolvableModulePatternException(":app") })
                .labels() shouldContain "[UncheckedFile] src/Broken.kt"
        }
    }

    "報告" - {
        val openDefinition = layoutArchitecture { "src" { anyFile() } }
        val openTree = repositoryOf { "src" { "App.kt"(); "Broken.kt"() } }
        val openBroken = openTree.failingAt(brokenFile) { IOException("cannot read Broken.kt") }

        "UncheckedFile のブロックに cause の型名とメッセージが出る" {
            openDefinition.validate(openBroken).report() shouldBe
                """
                Katachi check failed: 1 violation (Failed: 1)

                [UncheckedFile] src/Broken.kt
                  Katachi failed while checking this file, so nothing is known about it.
                  Cause: java.io.IOException: cannot read Broken.kt

                  How to fix:
                    - Check that the file is readable, then run the check again
                    - If it is, report this at https://github.com/TBSten/katachi/issues with the cause above

                1 file could not be checked.
                """.trimIndent()
        }

        "UncheckedDirectory のブロックは配下を見ていないことを言う" {
            val directoryDefinition = layoutArchitecture { "src" { "broken" { anyFile() } } }
            val directoryTree = repositoryOf { "src" { "broken" { "a.kt"() } } }

            directoryDefinition
                .validate(directoryTree.failingToListAt("/repo/src/broken") { IOException("cannot list broken") })
                .report() shouldBe
                """
                Katachi check failed: 1 violation (Failed: 1)

                [UncheckedDirectory] src/broken
                  Katachi failed while checking this directory. Nothing below it was checked.
                  Cause: java.io.IOException: cannot list broken

                  How to fix:
                    - Check that the directory is readable, then run the check again
                    - If it is, report this at https://github.com/TBSten/katachi/issues with the cause above

                1 directory could not be checked.
                """.trimIndent()
        }

        "末尾に検査できなかった件数の行が出る" {
            definition
                .validate(tree.failingAt(brokenFile) { IOException("cannot read it") })
                .report()
                .lines()
                .last() shouldBe "1 file could not be checked."
        }

        "打ち切られてもその行は消えない" {
            definition
                .validate(tree.failingAt(brokenFile) { IOException("cannot read it") })
                .report(maxViolations = 1)
                .lines()
                .last() shouldBe "1 file could not be checked."
        }

        "ファイルとディレクトリが混ざると paths と書く" {
            val mixedDefinition = layoutArchitecture {
                "src" {
                    "App".ktFile()
                    "broken" { anyFile() }
                }
            }
            val mixedTree = repositoryOf {
                "src" {
                    "App.kt"()
                    "Broken.kt"()
                    "broken" { "a.kt"() }
                }
            }
            val mixed = ThrowingFileSystem(
                delegate = mixedTree,
                onIsDirectory = mapOf(brokenFile to { IOException("cannot read it") }),
                onList = mapOf("/repo/src/broken" to { IOException("cannot list it") }),
            )

            mixedDefinition.validate(mixed).report().lines().last() shouldBe "2 paths could not be checked."
        }

        "例外が出なければ報告はその行を持たない" {
            definition.validate(tree).report() shouldNotContain "could not be checked."
        }
    }

    "assert" - {
        "検査できなかったファイルだけでも検査は失敗する" {
            val openDefinition = layoutArchitecture { "src" { anyFile() } }
            val openTree = repositoryOf { "src" { "App.kt"(); "Broken.kt"() } }

            val failure = shouldThrow<KatachiArchitectureAssertionError> {
                openDefinition.assert(openTree.failingAt(brokenFile) { IOException("cannot read it") })
            }

            failure.message!! shouldContain "[UncheckedFile] src/Broken.kt"
            failure.message!!.lines().last() shouldBe "1 file could not be checked."
        }
    }
})
