@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist

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

class KonsistScopeSpec : FreeSpec({
    "must / mustNot / mustBeEmpty" - {
        "must は述語が false になったファイルだけを返す" {
            val violations = konsistRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
            ) {
                classes().must { it.hasInternalModifier }
            }

            violations.rejectedPaths() shouldContainExactly listOf("src/PublicThing.kt")
        }

        "mustNot は逆になる" {
            val violations = konsistRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
            ) {
                classes().mustNot { it.hasInternalModifier }
            }

            violations.rejectedPaths() shouldContainExactly listOf("src/InternalThing.kt")
        }

        "mustBeEmpty は全件を返す" {
            val violations = konsistRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
            ) {
                classes().mustBeEmpty()
            }

            // Walk order, which for one directory is the file system's own sorted listing.
            violations.rejectedPaths() shouldContainExactly
                listOf("src/InternalThing.kt", "src/PublicThing.kt")
        }

        "何も落ちなければ違反は 0 件" {
            val violations = konsistRun("src/InternalThing.kt" to INTERNAL_THING_KT) {
                classes().must { it.hasInternalModifier }
            }

            violations.shouldBeEmpty()
        }

        "1 つのブロックに書いた must 2 つが両方走る" {
            val violations = konsistRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/InternalThing.kt" to INTERNAL_THING_KT,
            ) {
                // The first rejects the public class, the second every function. A collector
                // that stopped at the first expectation -- the way Konsist's own assertions
                // stop at the first rejection -- would lose the second entirely.
                classes().must { it.hasInternalModifier }
                functions().must { false }
            }

            // Three rejections from two expectations: one class and both files' `run`. Walk
            // order first, then the order the block produced them -- the sort is stable, so
            // the class the first `must` rejected still precedes the function the second did.
            violations.filterIsInstance<UnsatisfiedFileConstraint>().map {
                "${it.path}:${it.declaration}"
            } shouldContainExactly listOf(
                "src/InternalThing.kt:InternalThing.run",
                "src/PublicThing.kt:PublicThing",
                "src/PublicThing.kt:PublicThing.run",
            )
        }
    }

    "同じ宣言と別の宣言" - {
        "同じ宣言が 2 回落ちても 1 件" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must { false }
                classes().must { false }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map {
                "${it.path}:${it.declaration}"
            } shouldContainExactly listOf("src/PublicThing.kt:PublicThing")
        }

        "同じファイルの別の宣言なら 2 件" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must { false }
                functions().must { false }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.declaration } shouldBe
                listOf("PublicThing", "PublicThing.run")
        }
    }

    "宣言の名前は ktFileContent と同じ形" - {
        "クラスの中の宣言はクラスで修飾され、パッケージは付かない" {
            val violations = konsistRun("src/Models.kt" to MODELS_KT) {
                properties().must { false }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { "${it.declaration}:${it.line}" } shouldContainExactly
                listOf("User.id:4", "Order.id:8")
        }

        "入れ子のクラスは外側のクラスで修飾される" {
            val violations = konsistRun("src/Screens.kt" to SCREENS_KT) {
                classes().must { false }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.declaration } shouldContainExactly
                listOf("HomeScreen", "HomeScreen.State", "DetailScreen", "DetailScreen.State")
        }

        "companion object のメンバーは Companion で修飾される" {
            val violations = konsistRun("src/User.kt" to USER_WITH_COMPANION_KT) {
                functions().must { false }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.declaration } shouldContainExactly
                listOf("User.Companion.create")
        }

        "トップレベルの宣言と import は名前だけ" {
            val violations = konsistRun("src/Helpers.kt" to TOP_LEVEL_KT) {
                functions().must { false }
                properties().must { false }
                files.flatMap { it.imports }.must { false }
            }

            violations.filterIsInstance<UnsatisfiedFileConstraint>().map { it.declaration } shouldContainExactly
                listOf("helper", "counter", "com.example.other.Helper")
        }
    }

    "スコープに入るもの" - {
        ".md はスコープに入らない" {
            val violations = konsistRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/notes.md" to "# notes\n",
                declarations = listOf("*.kt", "notes.md"),
            ) {
                // Every file Konsist parsed, rejected. A `.md` in the scope would show up here.
                files.mustBeEmpty()
            }

            violations.rejectedPaths() shouldContainExactly listOf("src/PublicThing.kt")
        }

        // 設計（§9 層3 の 4 番と、ステップ7 の完了条件）は「.kts はスコープに入る」と書いているが、
        // Konsist 0.17.3 の `File.isKotlinFile` は `name.endsWith(".kt")` ひとつなので**入らない**
        // （KonsistAssumptionsSpec (4) が実測で固定している）。§7.3 の `konsistScopeOf` を設計の
        // ままに書くと `.kts` が `wanted` に入り、slice 後の件数と必ず食い違って
        // KatachiKonsistScopeIncompleteException（＝「katachi のバグ」）が出る。だから katachi 側も
        // `.kt` だけを要求する。これはその決着を固定するテスト。報告済み。
        ".kts はスコープに入らず、件数照合も壊さない" {
            val violations = konsistRun(
                "src/PublicThing.kt" to PUBLIC_THING_KT,
                "src/Task.kts" to "val task = 1\n",
                declarations = listOf("*.kt", "Task.kts"),
            ) {
                files.mustBeEmpty()
            }

            // One rejection, not two, and no `[UncheckedFileConstraint]`: the `.kts` was covered by
            // the constraint, never handed to Konsist, and never counted against it.
            violations.rejectedPaths() shouldContainExactly listOf("src/PublicThing.kt")
            violations.size shouldBe 1
        }
    }

    "報告される内容" - {
        "declaration と line が埋まる" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must { false }
            }

            val rejected = violations.filterIsInstance<UnsatisfiedFileConstraint>().single()
            rejected.path shouldBe "src/PublicThing.kt"
            rejected.declaration shouldBe "PublicThing"
            rejected.line shouldBe 3
            rejected.constraintName shouldBe "規約"
            rejected.role.qualifiedName shouldBe "domain.UseCase"
        }

        "declarations().must { } が書けて、落ちた宣言が報告される" {
            // The reason `must` is bounded at `KoBaseProvider` rather than `KoPathProvider`:
            // `declarations()` comes back as `List<KoBaseDeclaration>`, whose only supertype is
            // `KoBaseProvider`. With the tighter bound the most natural Konsist query of all
            // would not compile, and the only thing left to write would be `assertTrue`.
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                declarations().must { false }
            }

            val rejected = violations.filterIsInstance<UnsatisfiedFileConstraint>()
            rejected.map { it.path }.distinct() shouldContainExactly listOf("src/PublicThing.kt")
            // A file declaration carries a name and a path but no location at all, so it is
            // reported with `line = null` rather than being dropped or throwing. Konsist names
            // a file without its extension, which is why the first and third entries read the
            // same apart from the line.
            rejected.map { "${it.declaration}:${it.line}" } shouldContainExactly listOf(
                "PublicThing:null",
                "com.example:1",
                "PublicThing:3",
                "PublicThing.run:4",
            )
        }
    }
})

/** `User.id` on line 4 and `Order.id` on line 8. */
private val MODELS_KT: String = """
    package com.example

    class User {
        val id: String = ""
    }

    class Order {
        val id: String = ""
    }
""".trimIndent() + "\n"

/** Two screens, each with a nested `State`. */
private val SCREENS_KT: String = """
    package com.example

    class HomeScreen {
        class State
    }

    class DetailScreen {
        class State
    }
""".trimIndent() + "\n"

/** A class whose unnamed companion object declares `create`. */
private val USER_WITH_COMPANION_KT: String = """
    package com.example

    class User {
        companion object {
            fun create(): User = User()
        }
    }
""".trimIndent() + "\n"

/** An import, a top-level function and a top-level property. */
private val TOP_LEVEL_KT: String = """
    package com.example

    import com.example.other.Helper

    fun helper(): Helper? = null

    val counter: Int = 0
""".trimIndent() + "\n"
