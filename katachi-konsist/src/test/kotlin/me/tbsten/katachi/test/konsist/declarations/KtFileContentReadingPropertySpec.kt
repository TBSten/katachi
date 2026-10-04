package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * What the matching reads off a file through Konsist (plan P2), over files that write the same
 * kinds of declaration in many ways: annotations with arguments and over several lines, KDoc and
 * comments, context parameters, multi-dollar and raw strings, backticked names, bounded generics,
 * `expect` and `actual`, trailing commas, delegation with `by`, nested classes, companion objects,
 * primary constructor properties, anonymous objects, enum entries with bodies, local declarations.
 *
 * Every declaration's kind, modifiers, name, annotations (and a full name each may stand for),
 * supertypes, written types, parameter types, return type and line are compared with what the
 * file was written with (`KtFileContentReading*SpecSupport.kt`), and so are the package and the
 * imports.
 */
class KtFileContentReadingPropertySpec : FreeSpec({
    "書き方を変えても、読み取った種類・修飾子・名前・アノテーション・親の型・型・引数・戻り値・行が書いたものと一致する" {
        checkTrials(
            property = "reading",
            trials = pbtTrials(DEFAULT_TRIALS),
            seed = PBT_SEED + 30,
            generate = { random -> random.readCase() },
            shrinks = ReadCase::shrinks,
            describe = { case -> numbered(readFileOf(case).text) },
        ) { case ->
            val file = readFileOf(case)
            val facts = factsOf(file.text)
            withClue("the source:\n${numbered(file.text)}") {
                describeActualHeader(facts) shouldBe describeHeader(file)
                describeActual(facts.declarations, file.declarations) shouldBe describeExpected(file.declarations)
            }
        }
    }
})

/** Trials unless `-Pkatachi.pbt.scale` says otherwise: each parses one file. */
private const val DEFAULT_TRIALS: Int = 300

/** [text] with its line numbers, for a failure to point at. */
private fun numbered(text: String): String =
    text.lines().withIndex().joinToString("\n") { (index, line) -> "${(index + 1).toString().padStart(3)}| $line" }
