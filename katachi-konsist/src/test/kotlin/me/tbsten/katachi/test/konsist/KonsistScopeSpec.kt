@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.scan.UnsatisfiedConstraint
import me.tbsten.katachi.scan.Violation

/** The rejected paths of a run, in the order the report will print them. */
private fun List<Violation>.rejectedPaths(): List<String> =
    filterIsInstance<UnsatisfiedConstraint>().map { it.path }

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
            violations.filterIsInstance<UnsatisfiedConstraint>().map {
                "${it.path}:${it.declaration}"
            } shouldContainExactly listOf(
                "src/InternalThing.kt:run",
                "src/PublicThing.kt:PublicThing",
                "src/PublicThing.kt:run",
            )
        }
    }

    "同じ宣言と別の宣言" - {
        "同じ宣言が 2 回落ちても 1 件" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must { false }
                classes().must { false }
            }

            violations.filterIsInstance<UnsatisfiedConstraint>().map {
                "${it.path}:${it.declaration}"
            } shouldContainExactly listOf("src/PublicThing.kt:PublicThing")
        }

        "同じファイルの別の宣言なら 2 件" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes().must { false }
                functions().must { false }
            }

            violations.filterIsInstance<UnsatisfiedConstraint>().map { it.declaration } shouldBe
                listOf("PublicThing", "run")
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

            // One rejection, not two, and no `[UncheckedConstraint]`: the `.kts` was covered by
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

            val rejected = violations.filterIsInstance<UnsatisfiedConstraint>().single()
            rejected.path shouldBe "src/PublicThing.kt"
            rejected.declaration shouldBe "PublicThing"
            rejected.line shouldBe 3
            rejected.constraintName shouldBe "規約"
            rejected.role.qualifiedName shouldBe "domain/UseCase"
        }

        "declarations().must { } が書けて、落ちた宣言が報告される" {
            // The reason `must` is bounded at `KoBaseProvider` rather than `KoPathProvider`:
            // `declarations()` comes back as `List<KoBaseDeclaration>`, whose only supertype is
            // `KoBaseProvider`. With the tighter bound the most natural Konsist query of all
            // would not compile, and the only thing left to write would be `assertTrue`.
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                declarations().must { false }
            }

            val rejected = violations.filterIsInstance<UnsatisfiedConstraint>()
            rejected.map { it.path }.distinct() shouldContainExactly listOf("src/PublicThing.kt")
            // A file declaration carries a name and a path but no location at all, so it is
            // reported with `line = null` rather than being dropped or throwing. Konsist names
            // a file without its extension, which is why the first and third entries read the
            // same apart from the line.
            rejected.map { "${it.declaration}:${it.line}" } shouldContainExactly listOf(
                "PublicThing:null",
                "com.example:1",
                "PublicThing:3",
                "run:4",
            )
        }
    }
})
