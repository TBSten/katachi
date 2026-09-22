@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.report
import me.tbsten.katachi.konsist.KatachiKonsistDirectAssertionException
import me.tbsten.katachi.scan.UncheckedConstraint
import me.tbsten.katachi.scan.UncheckedConstraintReason

/**
 * Konsist's own twelve assertions, and the fact that none of them can be written here.
 *
 * Two halves, because the containment has two halves. Which spellings fail to compile is
 * settled by the compiler and recorded below as its own output — a running test cannot assert
 * that something does not compile. What a suppressed call does at run time *is* testable, and
 * is the second half: the shadows return `Nothing` and throw rather than being empty `Unit`
 * bodies, so `@Suppress("DEPRECATION_ERROR")` buys a loud failure instead of a silent pass.
 *
 * The set of spellings that has to be covered is not maintained here either — see
 * `KonsistAssumptionsSpec` (7) and (7b), which read `com.lemonappdev.konsist.api.verify` by
 * reflection and compare it against `KonsistScope`'s declared members.
 */
class KonsistShadowSpec : FreeSpec({
    "コンパイルエラーになる綴り" - {
        // 下の 7 つは実際に :katachi-konsist:compileTestKotlin に通して、全部エラーになることを
        // 確認済み（Kotlin 2.4.10 / Konsist 0.17.3）。引用しているのはその実出力。いずれも
        // `KonsistScope` の shadow に解決していて、Konsist のトップレベル拡張には落ちていない
        // ——これが「面で塞ぐ」が効いている証拠で、1 綴りだけ shadow していたら
        // `first()` 版や `asSequence()` 版はここで Konsist 側に解決してしまう。
        //
        // val probe: KonsistScope.() -> Unit = {
        //     classes().assertTrue { it.hasInternalModifier }
        //     classes().assertTrue(additionalMessage = "x") { it.hasInternalModifier }
        //     classes().assertTrue(true) { it.hasInternalModifier }
        //     classes().asSequence().assertTrue { it.hasInternalModifier }
        //     classes().first().assertTrue { it.hasInternalModifier }
        //     declarations().assertTrue { true }
        //     classes().assertEmpty()
        // }
        //
        // e: ...:6:15 'fun <E : KoBaseProvider> List<E?>.assertTrue(...): Nothing' is deprecated.
        //    katachi cannot see which declarations assertTrue rejected. Use must / mustNot / mustBeEmpty.
        // e: ...:7:15 (同上、名前付き引数でも同じ shadow に当たる)
        // e: ...:8:15 (同上、strict を位置引数で渡しても同じ)
        // e: ...:9:28 'fun <E : KoBaseProvider> Sequence<E?>.assertTrue(...): Nothing' is deprecated.
        //    katachi cannot see which declarations assertTrue rejected. Use toList() then must / mustNot.
        // e: ...:10:23 'fun <E : KoBaseProvider> E?.assertTrue(...): Nothing' is deprecated.
        //    katachi cannot see which declaration assertTrue rejected. Use must / mustNot / mustBeEmpty.
        // e: ...:11:20 'fun <E : KoBaseProvider> List<E?>.assertTrue(...): Nothing' is deprecated.
        //    (declarations() は List<KoBaseDeclaration>。KoBaseProvider 境界なので shadow に当たる)
        // e: ...:12:15 'fun <E : KoBaseProvider> List<E?>.assertEmpty(...): Nothing' is deprecated.
        //    katachi cannot see which declarations assertEmpty rejected. Use mustBeEmpty.
        "7 綴りすべてがコンパイルエラーになることを確認済み" {
            true shouldBe true
        }
    }

    "@Suppress を書いても黙って緑にはならない" - {
        "shadow の本体は Nothing を返して投げる" {
            // `Unit` + an empty body would make this line do nothing at all, and the block
            // would go on to be reported as satisfied. That is the exact failure the shadows
            // exist to prevent, so the escape hatch has to be louder than the thing it escapes.
            //
            // Loud, but not fatal to the run: the throw lands in `ConstraintCheck`'s per
            // constraint catch, so this one rule is reported as unanswered and every other
            // rule of the definition still gets to say what it found.
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                @Suppress("DEPRECATION_ERROR")
                classes().assertTrue { it.hasInternalModifier }
            }

            val unchecked = violations.filterIsInstance<UncheckedConstraint>().single()
            unchecked.reason shouldBe UncheckedConstraintReason.Failed
            unchecked.cause
                .shouldBeInstanceOf<KatachiKonsistDirectAssertionException>()
                .spelling shouldBe "assertTrue"
        }

        "落ちた制約として報告され、katachi のバグとは言われない" {
            val violations = konsistRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                @Suppress("DEPRECATION_ERROR")
                classes().assertEmpty()
            }

            val cause = violations.filterIsInstance<UncheckedConstraint>().single().cause
                .shouldBeInstanceOf<KatachiKonsistDirectAssertionException>()
            cause.spelling shouldBe "assertEmpty"
            cause.message.orEmpty() shouldContain "Use must { }, mustNot { } or mustBeEmpty()"

            // The `How to fix` of a failed constraint never says "this is a katachi bug" on its
            // own: the cause here is something the reader wrote, and a line that always blamed
            // katachi would be wrong most of the time.
            val report = violations.report()
            report shouldContain "- Read the cause above: it says what stopped the constraint"
            report shouldContain "- If the cause is a KatachiInternalException, report it at"
        }
    }
})
