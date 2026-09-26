package me.tbsten.katachi.test.processor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.LayoutCheck
import me.tbsten.katachi.check.internal.assertNoErrors
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.found
import me.tbsten.katachi.test.check.labels
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

/**
 * How many times one run evaluates each role's `layout { }` block.
 *
 * `declaredEntries` (the definition read without the project) and the walk (the definition
 * read against the modules that exist) used to evaluate every block once each. The two only
 * differ for a role that wrote a wildcard module key, so every other role is evaluated once and
 * both readings share that one result. What must not change is what either reading answers.
 */
class LayoutEvaluationCountSpec : FreeSpec({
    "layout ブロックを評価する回数" - {
        "LayoutCheck を1回走らせると、役割の layout ブロックは1回だけ評価される" {
            var evaluations = 0
            val definition = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            evaluations++
                            "useCase" / "*UseCase".ktFile()
                        }
                    }
                }
            }
            val tree = repositoryOf { "useCase" { "GetUserUseCase.kt"() } }

            definition.process(LayoutCheck(), tree).getOrThrow() shouldBe emptyList()

            evaluations shouldBe 1
        }

        "assert 系の入口でも1回だけ評価される" {
            var evaluations = 0
            val definition = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            evaluations++
                            "useCase" / "*UseCase".ktFile()
                        }
                    }
                }
            }

            definition.assertNoErrors(repositoryOf { "useCase" { "GetUserUseCase.kt"() } }) shouldBe emptyList()

            evaluations shouldBe 1
        }

        "declaredEntries を走査より先に読んでも1回だけ評価される" {
            var evaluations = 0
            val definition = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout {
                            evaluations++
                            "useCase" / "*UseCase".ktFile()
                        }
                    }
                }
            }
            val tree = repositoryOf { "useCase" { "GetUserUseCase.kt"() } }

            definition.process(tree) { context ->
                context.declaredEntries
                context.filesOf(context.roles.single())
            } shouldBe listOf("useCase/GetUserUseCase.kt")

            evaluations shouldBe 1
        }

        "ワイルドカードの module キーを書いた役割だけは2回、書いていない役割は1回" {
            // The wildcard role has two different answers -- the pattern as written, and the
            // modules that exist -- so it has to be evaluated once for each.
            var wildcardEvaluations = 0
            var plainEvaluations = 0
            val definition = architectureOf {
                "feature".group {
                    "Module" {
                        layout {
                            wildcardEvaluations++
                            ":feature:*".module { }
                        }
                    }
                }
                "core".group {
                    "Data" {
                        layout {
                            plainEvaluations++
                            ":core:data".module { }
                        }
                    }
                }
            }
            val tree = repositoryOf {
                "feature" { "home" { "build.gradle.kts"() } }
                "core" { "data" { "build.gradle.kts"() } }
            }

            definition.process(LayoutCheck(), tree).getOrThrow() shouldBe emptyList()

            wildcardEvaluations shouldBe 2
            plainEvaluations shouldBe 1
        }
    }

    "評価を分け合っても答えは変わらない" - {
        val definition = architectureOf {
            "feature".group {
                "Module" {
                    layout {
                        ":feature:*".module { "src" / "*.kt".file() }
                    }
                }
            }
            "core".group {
                "Data" {
                    layout {
                        ":core:data".module { "src" / "Repository.kt".file() }
                        "docs" / "README.md".file()
                    }
                }
            }
        }
        val tree = repositoryOf {
            "feature" {
                "home" { "build.gradle.kts"(); "src" { "Home.kt"() } }
                "settings" { "build.gradle.kts"() }
            }
            "core" { "data" { "build.gradle.kts"(); "src" { "Repository.kt"(); "Extra.kt"() } } }
            "tmp" { "scratch.txt"() }
        }

        "走査のあとに読んだ declaredEntries は、宣言だけから読んだものと同じ" {
            val afterWalk = definition.process(tree) { context ->
                context.filesOf(context.roles.first())
                context.declaredEntries
            }

            afterWalk.everything() shouldBe definition.flattenLayout().everything()
        }

        "走査より先に読んだ declaredEntries も、宣言だけから読んだものと同じ" {
            val declaredOnly = definition.process(ForbiddenFileSystem) { context -> context.declaredEntries }

            declaredOnly.everything() shouldBe definition.flattenLayout().everything()
        }

        "declaredEntries を先に読んでも、LayoutCheck の違反は変わらない" {
            val fresh = definition.process(LayoutCheck(), tree).found().labels()
            val afterDeclared = definition.process(tree) { context ->
                context.declaredEntries
                LayoutCheck().process(context).found().labels()
            }

            fresh shouldBe listOf(
                "[UnexpectedFile] core/data/src/Extra.kt",
                "[UnexpectedDirectory] tmp",
                "[MissingFile] docs/README.md",
            )
            afterDeclared shouldBe fresh
        }

        "declaredEntries を先に読んでも、各役割のファイルは変わらない" {
            fun Architecture.filesOfEveryRole(readDeclaredFirst: Boolean): List<List<String>> =
                process(tree) { context ->
                    if (readDeclaredFirst) context.declaredEntries
                    context.roles.map { context.filesOf(it) }
                }

            val fresh = definition.filesOfEveryRole(readDeclaredFirst = false)

            fresh shouldBe listOf(
                listOf(
                    "feature/home/build.gradle.kts",
                    "feature/home/src/Home.kt",
                    "feature/settings/build.gradle.kts",
                ),
                listOf("core/data/build.gradle.kts", "core/data/src/Repository.kt"),
            )
            definition.filesOfEveryRole(readDeclaredFirst = true) shouldBe fresh
        }
    }
})

/** Every field of an entry, the declaration site included, so that "the same" means the same. */
private fun List<LayoutEntry>.everything(): List<String> = map { entry ->
    listOf(
        entry.path,
        entry.kind,
        entry.required,
        entry.role.qualifiedName,
        entry.declaredAt,
        entry.description,
        entry.synthetic,
        entry.place,
        entry.modulePath,
        entry.pathInModule,
    ).joinToString(" | ")
}
