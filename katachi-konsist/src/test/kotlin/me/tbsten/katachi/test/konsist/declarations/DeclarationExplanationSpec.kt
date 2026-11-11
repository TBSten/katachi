@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.internal.DeclarationVerdict
import me.tbsten.katachi.dsl.internal.FileDeclarationsExplanation
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

/** [body] as a file of package `com.example`: its first line is line 3. */
private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

/** Each top-level declaration as `<name> <verdict> [<rule text>, ...]`. */
private fun FileDeclarationsExplanation.verdicts(): List<String> =
    declarations.map { "${it.name} ${it.verdict} ${it.rules.map { rule -> rule.text }}" }

/** One case of the table the invariant runs over: a source and the block it is checked against. */
private class Case(val name: String, val source: String, val block: KtFileContentScope.() -> Unit)

/**
 * The cases of `DeclarationJudgeSpec`, `KtFileContentRequiredSpec` and the design's examples, as
 * sources and blocks: every rule of the judging, each once.
 */
private val CASES: List<Case> = listOf(
    Case("禁止は許可より強い", kt("class FooImpl")) { allow(`class`, "*"); not.allow(`class`, "*Impl") },
    Case("except", kt("class DefaultImpl\nclass FooImpl")) { allow(`class`, "*"); not.allow(`class`, "*Impl").except("DefaultImpl") },
    Case("anythingElse", kt("class Sample\nprivate fun helper() {}\nfun leak() {}")) { required(`class`, fileName); allow(private, anythingElse) },
    Case("禁止の行だけ", kt("class Sample")) { not.allow(`class`, "*Impl") },
    Case("public", kt("class A\nprivate class B")) { allow(public, `class`, "*") },
    Case("or", kt("internal class A\nprivate class B\nclass C")) { allow(internal or private, `class`, "*") },
    Case("アノテーション", kt("@A @B class X\n@A class Y")) { allow(annotated("A", "B"), `class`, "*") },
    Case("種類", kt("class Sample")) { required(`interface`, fileName) },
    Case("メンバ", kt("abstract class Sample : Base() {\n    private val a = 1\n    protected fun b() {}\n}")) { required(`class`, fileName) { } },
    Case("型", kt("val name: String? = null")) { required(`val`, "name").type("String") },
    Case("引数", kt("fun load(a: String, b: Int) {}\nfun save(x: Int) {}\nfun drop(x: Int) {}")) {
        required(`fun`, "load")("a: String", "b: Int")
        required(`fun`, "save")()
        required(`fun`, "drop")
    },
    Case("戻り値", kt("fun onA() {}\nfun onB(): Unit {}\nfun onC() = Unit")) { allow(`fun`, "on*")().returns("Unit") },
    Case("親の型", kt("class Sample : Repository<User>")) { required(`class`, fileName).superclass("Repository<Admin>") },
    Case("import だけ", kt("class Anything\nfun whatever() {}")) { `import`("**") },
    Case("必須が足りない", kt("interface Sample")) { required(`interface`, fileName); required(`class`, "Default$fileName").superclass(fileName) },
    Case("allow は無くてもよい", kt("interface Sample")) { required(`interface`, fileName); allow(`class`, "Default$fileName"); allow(`object`, "*Factory") },
    Case("名前だけ当たる", kt("class Sample")) { required(data, `class`, fileName) },
    Case("Found を添える", kt("class Sample {\n    private fun invoke() {}\n}")) {
        required(`class`, fileName) { required(override, `fun`, "invoke"); allow(private, anythingElse) }
    },
    Case("入れ子のメンバ", kt("class Sample {\n    class Mode {\n        fun next() {}\n    }\n}")) {
        required(`class`, fileName) { required(`class`, "Mode") { required(`fun`, "previous") } }
    },
    Case("ファイル直下とメンバの足りない", kt("class Sample {\n}")) { required(`class`, fileName) { required(`val`, "state") }; required(`object`, "Keys") },
    Case("オーバーロード", kt("fun load(a: String) {}\nfun load(a: Int) {}")) { required(`fun`, "load")("a: String") },
    Case("2 行に当たる", kt("class Sample")) { allow(`class`, "*"); required(`class`, fileName) },
    Case("拡張関数", kt("fun String.shout(): String = uppercase()")) { required(`fun`, "shout") },
    Case("typealias", kt("typealias Id = String\nclass Sample")) { required(`class`, fileName) },
    Case("宣言なしのファイル", kt("")) { required(`class`, fileName) },
    Case("package と import と宣言", kt("import kotlin.math.max\n\nclass Sample\nobject Other")) {
        `package`("com.example")
        `import`("kotlin.**")
        required(`class`, fileName)
    },
)

/**
 * How `ktFileContent { }` explains each top-level declaration to the IDE plugin, and that the
 * explanation never disagrees with the check (plan V8): a declaration is not allowed exactly when
 * the check rejects it, and a line is missing exactly when the check says `Missing:`.
 */
class DeclarationExplanationSpec : FreeSpec({
    "宣言ごとの verdict と決めた行" - {
        "許可の行に当たれば Allowed で、当たった行を書いた順にすべて" {
            judgedAndExplained(kt("class Sample\nclass Other")) {
                allow(`class`, "*")
                required(`class`, fileName)
            }.explanation.verdicts() shouldContainExactly listOf(
                "Sample Allowed [class *, class Sample]",
                "Other Allowed [class *]",
            )
        }

        "anythingElse だけに当たれば AllowedByAnythingElse で、その行" {
            judgedAndExplained(kt("class Sample\nprivate fun helper() {}")) {
                required(`class`, fileName)
                allow(private, anythingElse)
            }.explanation.verdicts()[1] shouldStartWith "helper AllowedByAnythingElse [private"
        }

        "禁止の行は Prohibited、名前だけ当たれば Mismatched と違い、どれにも当たらなければ Unexpected" {
            val explanation = judgedAndExplained(kt("class FooImpl\nclass Sample\nfun stray() {}")) {
                not.allow(`class`, "*Impl")
                required(`interface`, fileName)
            }.explanation
            explanation.verdicts() shouldContainExactly listOf(
                "FooImpl Prohibited [not class *Impl]",
                "Sample Mismatched [interface Sample]",
                "stray Unexpected []",
            )
            explanation.declarations[1].difference shouldBe "kind (is class, expected interface)"
            explanation.declarations.map { it.kind } shouldContainExactly listOf("class", "class", "fun")
            explanation.declarations.map { it.line } shouldContainExactly listOf(3, 4, 5)
        }

        "オーバーロードは宣言ごとに決まる" {
            judgedAndExplained(kt("fun load(a: String) {}\nfun load(a: Int) {}")) {
                required(`fun`, "load")("a: String")
            }.explanation.verdicts() shouldContainExactly listOf(
                "load Allowed [fun load(a: String)]",
                "load Mismatched [fun load(a: String)]",
            )
        }

        "fileName で書いた行は、そのファイルの値で描く" {
            judgedAndExplained(kt("class HealthService"), path = "src/main/kotlin/com/example/HealthService.kt") {
                required(`class`, fileName)
            }.explanation.declarations.single().rules.single().text shouldBe "class HealthService"
        }

        "行の宣言元は書いた行。ヘルパー関数に書いた行はヘルパーの行" {
            val explanation = judgedAndExplained(kt("class Sample\nval logger = 1")) {
                required(`class`, fileName)
                allowLoggerHelper()
            }.explanation
            explanation.declarations[0].rules.single().declaredAt.fileName shouldBe "DeclarationExplanationSpec.kt"
            explanation.declarations[1].rules.single().declaredAt shouldBe helperLineSite.shouldNotBeNull()
            helperLineSite?.fileName shouldBe "DeclarationExplanationSpecSupport.kt"
        }

        "必須の行に当たる宣言が無ければ missing に入る" {
            judgedAndExplained(kt("interface Sample")) {
                required(`interface`, fileName)
                required(`class`, "Default$fileName").superclass(fileName)
            }.explanation.missing.map { it.text } shouldContainExactly listOf("class DefaultSample : Sample")
        }

        "import だけのブロックも宣言を判定し、どの宣言も Unexpected と説明する" {
            val explanation = judgedAndExplained(kt("class Anything")) { `import`("**") }.explanation
            explanation.declarationsChecked shouldBe true
            explanation.verdicts() shouldBe listOf("Anything Unexpected []")
        }
    }

    "検査との一致" - {
        "ケース表のすべてで、許可でない宣言 ⇔ 違反、missing ⇔ Missing:" {
            for (case in CASES) {
                val result = judgedAndExplained(case.source, block = case.block)
                withClue("${case.name}\n${case.source}\nfailures: ${result.failures}\nexplained: ${result.explanation.verdicts()}") {
                    inconsistenciesOf(result.failures, result.explanation).shouldBeEmpty()
                }
            }
        }

        "生成したケースでも成り立つ" {
            checkTrials(
                property = "explanation agrees with the check",
                trials = pbtTrials(60),
                seed = PBT_SEED + 50,
                generate = { random -> random.genCase() },
                shrinks = { it.shrinks() },
                describe = ::describeCase,
            ) { case ->
                val result = judgedAndExplained(sourceOf(case), case.path) { writeCase(case) }
                inconsistenciesOf(result.failures, result.explanation).shouldBeEmpty()
            }
        }

        "実際の検査の 1 回の中で、同じ subject に対して一致する" {
            val run = explainedRun(
                "src/main/kotlin/com/example/HealthService.kt" to kt("class HealthService\nfun stray() {}"),
                "src/main/kotlin/com/example/UserService.kt" to kt("object UserService"),
                "src/main/kotlin/com/example/notes.txt" to "not Kotlin",
            ) {
                required(`class`, fileName)
                allow(`object`, "*Factory")
            }
            val explanations = run.explanations.getOrThrow()
            explanations.map { it.file } shouldContainExactly listOf(
                "src/main/kotlin/com/example/HealthService.kt",
                "src/main/kotlin/com/example/UserService.kt",
            )
            val failures = run.failures.getOrThrow()
            for (explanation in explanations) {
                inconsistenciesOf(failures.filter { it.file == explanation.file }, explanation).shouldBeEmpty()
            }
            explanations[1].verdicts() shouldContainExactly listOf("UserService Mismatched [class UserService]")
        }
    }

    "説明できないファイル" - {
        "ブロックがあるファイルで投げると、そのファイルだけ unexplainedReason で、ほかは答える" {
            val run = explainedRun(
                "src/main/kotlin/com/example/Broken.kt" to kt("class Broken"),
                "src/main/kotlin/com/example/Fine.kt" to kt("class Fine"),
            ) {
                if (fileName == "Broken") throw IllegalStateException("no rules for Broken\nsecond line")
                required(`class`, fileName)
            }
            // The check stops at the first file that throws: a constraint answers as a whole.
            run.failures.isFailure shouldBe true
            val explanations = run.explanations.getOrThrow()
            explanations[0].unexplainedReason shouldBe "IllegalStateException: no rules for Broken"
            explanations[0].declarations.shouldBeEmpty()
            explanations[1].verdicts() shouldContainExactly listOf("Fine Allowed [class Fine]")
        }

        "どのファイルにも行を書かないブロックは、検査も説明も例外にならず、宣言をすべて許されないと判定する" {
            val run = explainedRun("src/main/kotlin/com/example/Sample.kt" to kt("class Sample")) { }
            run.failures.isSuccess shouldBe true
            val explanations = run.explanations.getOrThrow()
            explanations.single().declarationsChecked shouldBe true
            explanations.single().unexplainedReason shouldBe null
            explanations.single().declarations.map { "${it.name} ${it.verdict.name}" } shouldBe listOf("Sample Unexpected")
        }
    }

    "DeclarationVerdict.isAllowed は Allowed と AllowedByAnythingElse だけ" {
        DeclarationVerdict.entries.filter { it.isAllowed } shouldContainExactly
            listOf(DeclarationVerdict.Allowed, DeclarationVerdict.AllowedByAnythingElse)
    }
})
