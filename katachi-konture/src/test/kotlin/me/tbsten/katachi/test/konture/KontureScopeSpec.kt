@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation

/** The rejected paths of a run, in the order the report will print them. */
private fun List<Violation>.rejectedPaths(): List<String> =
    filterIsInstance<UnsatisfiedFileConstraint>().map { it.path }

/** `path:declaration:line` of every rejection, in report order. */
private fun List<Violation>.rejectedSites(): List<String> =
    filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.path}:${it.declaration}:${it.line}" }

/**
 * What the four kinds of rule collect, and how a Konture violation is read back into a path, a
 * declaration and a line.
 */
class KontureScopeSpec : FreeSpec({
    "4 つの suite" - {
        "files の違反はファイルだけを指し、宣言も行も持たない" {
            val violations = kontureRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
                "src/Helper.kt" to HELPER_KT,
            ) {
                files { should() nameEndsWith "Thing.kt" }
            }

            violations.rejectedSites() shouldContainExactly listOf("src/Helper.kt:null:null")
        }

        "classes の違反はパッケージを除いたクラス名と宣言行を持つ" {
            val violations = kontureRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
            ) {
                classes { should().beInternal() }
            }

            violations.rejectedSites() shouldContainExactly listOf("src/PublicThing.kt:PublicThing:3")
        }

        "functions の違反はクラスで修飾した関数名と宣言行を持つ" {
            val violations = kontureRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
            ) {
                functions { should().beInternal() }
            }

            // Konture names the subject `com.example.PublicThing.run`; the report drops the package.
            violations.rejectedSites() shouldContainExactly listOf("src/PublicThing.kt:PublicThing.run:4")
        }

        "properties の違反はクラスで修飾したプロパティ名と宣言行を持つ" {
            val violations = kontureRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
            ) {
                properties { should().beVal() }
            }

            violations.rejectedSites() shouldContainExactly listOf("src/PublicThing.kt:PublicThing.count:5")
        }

        "依存の規則は import から読まれ、覆っていないパッケージへの依存も見える" {
            val violations = kontureRun(
                "src/HomeViewModel.kt" to COMPOSABLE_VIEW_MODEL_KT,
                "src/PublicThing.kt" to PUBLIC_THING_KT,
            ) {
                classes { should() notDependOnPackages "androidx.compose.." }
            }

            violations.rejectedSites() shouldContainExactly listOf("src/HomeViewModel.kt:HomeViewModel:5")
        }

        "何も落ちなければ違反は 0 件" {
            val violations = kontureRun("src/InternalThing.kt" to INTERNAL_THING_KT) {
                classes { should().beInternal() }
            }

            violations.shouldBeEmpty()
        }
    }

    "that() の絞り込み" - {
        "that() が 0 件を選んでも緑" {
            // Konture's own default is an AssertionError here; katachi calls `allowEmpty()` on
            // every builder, because "there is none of these" is the layout check's job.
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes {
                    that() inPackage "com.nowhere.."
                    should().beInternal()
                }
            }

            violations.shouldBeEmpty()
        }

        "that() で絞った宣言だけが判定される" {
            val violations = kontureRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
                "src/Helper.kt" to HELPER_KT,
            ) {
                classes {
                    that() inPackage "com.example"
                    should().beInternal()
                }
            }

            // `Helper` is public too, but lives in `com.example.other`, outside the selection.
            violations.rejectedSites() shouldContainExactly listOf("src/PublicThing.kt:PublicThing:3")
        }
    }

    "同じ宣言と別の宣言" - {
        "同じ宣言が 2 つの規則で落ちても 1 件" {
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes { should().beInternal() }
                classes { should().beInternal() }
            }

            violations.rejectedSites() shouldContainExactly listOf("src/PublicThing.kt:PublicThing:3")
        }

        "同じファイルの別の宣言なら 2 件、suite の宣言順に並ぶ" {
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                functions { should().beInternal() }
                classes { should().beInternal() }
            }

            // Both in one file, so the walk order does not separate them: what is left is the
            // order the block declared the rules in, functions first.
            violations.rejectedSites() shouldContainExactly listOf(
                "src/PublicThing.kt:PublicThing.run:4",
                "src/PublicThing.kt:PublicThing:3",
            )
        }

        "別のファイルの違反は walk の順に並ぶ" {
            val violations = kontureRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
            ) {
                classes { should().bePrivate() }
            }

            violations.rejectedPaths() shouldContainExactly listOf("src/InternalThing.kt", "src/PublicThing.kt")
        }
    }

    "宣言の名前は ktFileContent と同じ形" - {
        "入れ子のクラスは外側のクラスで修飾され、パッケージは付かない" {
            val violations = kontureRun("src/Screens.kt" to SCREENS_KT) {
                classes { should().bePrivate() }
            }

            violations.rejectedSites() shouldContainExactly listOf(
                "src/Screens.kt:HomeScreen:3",
                "src/Screens.kt:HomeScreen.State:4",
                "src/Screens.kt:DetailScreen:9",
                "src/Screens.kt:DetailScreen.State:10",
            )
        }

        "同じ単純名の入れ子のクラスのメンバーは、宣言行でどちらのクラスか分かれる" {
            val violations = kontureRun("src/Screens.kt" to SCREENS_KT) {
                functions { should().beInternal() }
            }

            // Konture names both `com.example.State.render`; only the line tells them apart.
            violations.rejectedSites() shouldContainExactly listOf(
                "src/Screens.kt:HomeScreen.State.render:5",
                "src/Screens.kt:DetailScreen.State.render:11",
            )
        }

        "companion object のメンバーは Companion で修飾される" {
            val violations = kontureRun("src/User.kt" to USER_WITH_COMPANION_KT) {
                functions { should().beInternal() }
            }

            violations.rejectedSites() shouldContainExactly listOf("src/User.kt:User.Companion.create:5")
        }

        "トップレベルの関数・プロパティは名前だけ" {
            val violations = kontureRun("src/Helpers.kt" to TOP_LEVEL_KT) {
                functions { should().beInternal() }
                properties { should().beVal() }
            }

            violations.rejectedSites() shouldContainExactly listOf(
                "src/Helpers.kt:helper:3",
                "src/Helpers.kt:counter:5",
            )
        }

        "package の無いファイルでもクラスで修飾され、先頭に . が付かない" {
            val violations = kontureRun("src/Models.kt" to DEFAULT_PACKAGE_KT) {
                functions { should().beInternal() }
                properties { should().beVal() }
            }

            // Konture names them `.User.save` and `.User.id`, from an empty package and a dot.
            violations.rejectedSites() shouldContainExactly listOf(
                "src/Models.kt:User.save:3",
                "src/Models.kt:User.id:2",
            )
        }
    }

    "@Suppress" - {
        "@Suppress(\"konture:classes.rule\") を付けた宣言の違反は報告されない" {
            val violations = kontureRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/Suppressed.kt" to SUPPRESSED_KT,
            ) {
                classes { should().beInternal() }
            }

            // The reader wrote the suppression into the code, so katachi honours it, and the
            // unsuppressed class next to it is still reported.
            violations.rejectedSites() shouldContainExactly listOf("src/PublicThing.kt:PublicThing:3")
        }
    }

    "スコープに入るもの" - {
        ".md はスコープに入らない" {
            val violations = kontureRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/notes.md" to "# notes\n",
                declarations = listOf("*.kt", "notes.md"),
            ) {
                // Every file Konture parsed, rejected. A `.md` in the graph would show up here.
                files { should() satisfy { false } }
            }

            violations.rejectedPaths() shouldContainExactly listOf("src/PublicThing.kt")
        }

        ".kts はスコープに入らず、件数照合も壊さない" {
            val violations = kontureRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/Task.kts" to "val task = 1\n",
                declarations = listOf("*.kt", "Task.kts"),
            ) {
                files { should() satisfy { false } }
            }

            // One rejection, not two, and no `[UncheckedFileConstraint]`: the `.kts` was covered
            // by the constraint, never handed to Konture, and never counted against it.
            violations.rejectedPaths() shouldContainExactly listOf("src/PublicThing.kt")
            violations.size shouldBe 1
        }
    }

    "報告される内容" - {
        "role と constraintName が埋まる" {
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes { should().beInternal() }
            }

            val rejected = violations.filterIsInstance<UnsatisfiedFileConstraint>().single()
            rejected.path shouldBe "src/PublicThing.kt"
            rejected.declaration shouldBe "PublicThing"
            rejected.line shouldBe 3
            rejected.constraintName shouldBe "規約"
            rejected.role.qualifiedName shouldBe "domain.UseCase"
        }
    }
})

/** A public class whose violations the reader has already suppressed in the source. */
private val SUPPRESSED_KT: String = """
    package com.example

    @Suppress("konture:classes.rule")
    class Suppressed
""".trimIndent() + "\n"

/** Two screens on lines 3 and 9, each with a nested `State` (lines 4 and 10) whose `render` is on lines 5 and 11. */
private val SCREENS_KT: String = """
    package com.example

    class HomeScreen {
        class State {
            fun render() = Unit
        }
    }

    class DetailScreen {
        class State {
            fun render() = Unit
        }
    }
""".trimIndent() + "\n"

/** A class whose unnamed companion object declares `create` on line 5. */
private val USER_WITH_COMPANION_KT: String = """
    package com.example

    class User {
        companion object {
            fun create(): User = User()
        }
    }
""".trimIndent() + "\n"

/** A top-level function on line 3 and a top-level `var` on line 5. */
private val TOP_LEVEL_KT: String = """
    package com.example

    fun helper() = Unit

    var counter: Int = 0
""".trimIndent() + "\n"

/** A file with no `package` line: a class on line 1, its `var id` on line 2 and `save` on line 3. */
private val DEFAULT_PACKAGE_KT: String = """
    class User {
        var id: String = ""
        fun save() = Unit
    }
""".trimIndent() + "\n"
