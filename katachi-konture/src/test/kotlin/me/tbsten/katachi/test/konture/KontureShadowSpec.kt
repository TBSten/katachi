@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.github.baole.konture.Konture
import io.github.baole.konture.files
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UncheckedFileConstraintReason
import me.tbsten.katachi.test.report.reportForTest
import me.tbsten.katachi.konture.KatachiKontureDefaultGraphException

/**
 * Konture's thirty-seven entry points to its default project graph, and the fact that none of them
 * can be written inside a `konture { }` block.
 *
 * Two halves, because the containment has two halves. Which spellings fail to compile is
 * settled by the compiler and recorded below as its own output — a running test cannot assert
 * that something does not compile. What a suppressed call does at run time *is* testable, and
 * is the second half: the shadows return `Nothing` and throw rather than being empty `Unit`
 * bodies, so `@Suppress("DEPRECATION_ERROR")` buys a loud failure instead of a silent pass.
 *
 * The set of spellings that has to be covered is not maintained here either — see
 * [KontureAssumptionsSpec] (11), which reads every `*Kt` file facade of `io.github.baole.konture`
 * out of Konture's jar by reflection and compares them against `KontureDefaultGraphShadows`' members.
 */
class KontureShadowSpec : FreeSpec({
    "コンパイルエラーになる綴り" - {
        // 下の 37 綴りは実際に :katachi-konture:compileTestKotlin に通して、全部エラーになることを
        // 確認済み（Kotlin 2.4.10 / Konture 1.0.1、2026-10-04。32 本は .local/tmp/logs/core-probe.log、
        // あとから足した rule / architectureRules の 5 本は .local/tmp/logs/review-fix-probe.log）。
        // 引用しているのはその実出力。いずれも `KontureDefaultGraphShadows` の shadow（戻り型 Nothing）
        // に解決していて、Konture のトップレベル宣言には落ちていない——メンバがインポートされた
        // トップレベルより優先される、という仕組みが 37 本すべてで効いている証拠。
        //
        // val probe: KontureConstraintScope.() -> Unit = {
        //     architecture { }
        //     architecture(ProjectGraph(emptyMap())) { }
        //     Konture.architecture { }
        //     Konture.architecture(ProjectGraph(emptyMap())) { }
        //     Konture.files()
        //     Konture.files(SourceSets.production())
        //     Konture.files { }
        //     Konture.files(SourceSets.production()) { }
        //     Konture.classes()                                   // 同じ 4 形
        //     Konture.functions()                                 // 同じ 4 形
        //     Konture.properties()                                // 同じ 4 形
        //     Konture.modules()                                   // 同じ 4 形
        //     Konture.slices()                                    // 同じ 4 形
        //     Konture.layeredArchitecture()
        //     Konture.layered { }
        //     Konture.assertNoCycles()
        //     Konture.assertNoCycles(true)
        //     rule("x") { }
        //     architectureRules("x") { }
        //     architectureRules { }
        //     Konture.architectureRules("x") { }
        //     Konture.architectureRules { }
        // }
        //
        // e: ...:9:5 'fun architecture(block: KontureContext.() -> Unit): Nothing' is deprecated.
        //    architecture { } runs against Konture's default graph, which katachi does not build, and throws
        //    its violations as prose. Write files { } / classes { } / functions { } / properties { } directly
        //    in the konture { } block.
        // e: ...:10:5 'fun architecture(graph: ProjectGraph, block: KontureContext.() -> Unit): Nothing' is deprecated. (同上)
        // e: ...:11:13 'fun Konture.architecture(block: KontureContext.() -> Unit): Nothing' is deprecated. (同上)
        // e: ...:12:13 'fun Konture.architecture(graph: ProjectGraph, block: KontureContext.() -> Unit): Nothing' is deprecated. (同上)
        // e: ...:13:13 'fun Konture.files(): Nothing' is deprecated.
        //    Konture.files reads Konture's default graph, which katachi does not build. Use files { }.
        // e: ...:14:13 'fun Konture.files(sourceSets: SourceSetSelector): Nothing' is deprecated. (同上)
        // e: ...:15:13 'fun Konture.files(block: FilesRuleBuilder.() -> Unit): Nothing' is deprecated. (同上)
        // e: ...:16:13 'fun Konture.files(sourceSets: SourceSetSelector, block: FilesRuleBuilder.() -> Unit): Nothing' is deprecated. (同上)
        // e: ...:17:13 〜 :28:13 classes / functions / properties の 4 形ずつ。文面は files と同じ形で、
        //    `Konture.classes reads ... Use classes { }.` のように自分の名前を言う
        // e: ...:29:13 'fun Konture.modules(): Nothing' is deprecated.
        //    Module graph rules need Konture's Gradle plugin and build graph, which katachi does not provide.
        //    Use Konture directly in a test module of its own.
        // e: ...:30:13 〜 :32:13 modules の残り 3 形 (同上)
        // e: ...:33:13 〜 :36:13 slices の 4 形 (同上)
        // e: ...:37:13 'fun Konture.layeredArchitecture(): Nothing' is deprecated. (同上)
        // e: ...:38:13 'fun Konture.layered(block: LayeredArchitectureDsl.() -> Unit): Nothing' is deprecated. (同上)
        // e: ...:39:13 'fun Konture.assertNoCycles(): Nothing' is deprecated. (同上)
        // e: ...:40:13 'fun Konture.assertNoCycles(includeTestConfigurations: Boolean): Nothing' is deprecated. (同上)
        // e: ...:41:5 'fun rule(id: String, block: RuleBuilder.() -> Unit): Nothing' is deprecated.
        //    rule(id) { } binds its rules to Konture's default graph, which katachi does not build, and its id and
        //    severity never reach katachi's report. Write files { } / classes { } / functions { } / properties { }
        //    directly in the konture { } block; the constraint's name is what the report shows.
        // e: ...:42:5 'fun architectureRules(name: String? = ..., block: RuleSetBuilder.() -> Unit): Nothing' is deprecated.
        //    architectureRules { } builds a RuleSet whose check() runs against Konture's default graph, which katachi
        //    does not build. Write files { } / classes { } / functions { } / properties { } directly in the konture { } block.
        // e: ...:43:5 'fun architectureRules(block: RuleSetBuilder.() -> Unit): Nothing' is deprecated. (同上)
        // e: ...:44:13 'fun Konture.architectureRules(name: String? = ..., block: RuleSetBuilder.() -> Unit): Nothing' is deprecated. (同上)
        // e: ...:45:13 'fun Konture.architectureRules(block: RuleSetBuilder.() -> Unit): Nothing' is deprecated. (同上)
        "37 綴りすべてがコンパイルエラーになることを確認済み" {
            true shouldBe true
        }
    }

    "@Suppress を書いても黙って緑にはならない" - {
        "shadow の本体は Nothing を返して投げる" {
            // `Unit` + an empty body would make this line do nothing at all, and the block
            // would go on to be refused for declaring no rule -- or, next to a real rule, be
            // reported as satisfied. The escape hatch has to be louder than the thing it escapes.
            //
            // Loud, but not fatal to the run: the throw lands in `FileConstraintCheck`'s per
            // constraint catch, so this one rule is reported as unanswered and every other
            // rule of the definition still gets to say what it found.
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                @Suppress("DEPRECATION_ERROR")
                architecture { }
            }

            val unchecked = violations.filterIsInstance<UncheckedFileConstraint>().single()
            unchecked.reason shouldBe UncheckedFileConstraintReason.Failed
            unchecked.cause
                .shouldBeInstanceOf<KatachiKontureDefaultGraphException>()
                .spelling shouldBe "architecture"
        }

        "Konture の拡張の shadow も同じく投げる" {
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                @Suppress("DEPRECATION_ERROR")
                Konture.files()
            }

            val cause = violations.filterIsInstance<UncheckedFileConstraint>().single().cause
                .shouldBeInstanceOf<KatachiKontureDefaultGraphException>()
            cause.spelling shouldBe "Konture.files()"
            cause.message.orEmpty() shouldContain "Inside konture { } write files { }"

            // The `How to fix` of a failed constraint never says "this is a katachi bug" on its
            // own: the cause here is something the reader wrote, and a line that always blamed
            // katachi would be wrong most of the time.
            val report = violations.reportForTest()
            report shouldContain "- Read the cause above: it says what stopped the constraint"
            report shouldContain "- If the cause is a KatachiInternalException, report it at"
        }

        "shadow が投げても、隣の規則は評価されている訳ではなく制約ごと落ちる" {
            // The shadow throws while the block is still declaring, so the rule written next to
            // it is never evaluated; the whole constraint is one `[UncheckedFileConstraint]`.
            val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes { should().beInternal() }
                @Suppress("DEPRECATION_ERROR")
                Konture.classes()
            }

            violations.size shouldBe 1
            violations.single().shouldBeInstanceOf<UncheckedFileConstraint>()
        }
    }
})
