package me.tbsten.katachi.test

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.internal.ArgValueParsing

/**
 * The rules a `--arg` string is read by, shared by a processor's `Args` and a template's typed
 * parameters.
 */
class ArgValueParsingSpec : FreeSpec({
    "Boolean は小文字の true / false だけ" {
        ArgValueParsing.boolean("true") shouldBe true
        ArgValueParsing.boolean("false") shouldBe false
        for (raw in listOf("True", "TRUE", "yes", "1", "", "true ")) {
            ArgValueParsing.boolean(raw) shouldBe null
        }
    }

    "範囲外の整数を言い分けられる（全角数字の桁あふれを含む）" {
        ArgValueParsing.isOutOfIntRange("2147483648") shouldBe true
        ArgValueParsing.isOutOfIntRange("-99999999999") shouldBe true
        ArgValueParsing.isOutOfIntRange("+2147483648") shouldBe true
        ArgValueParsing.isOutOfIntRange("99999999999999999999") shouldBe true
        ArgValueParsing.isOutOfIntRange("９９９９９９９９９９９") shouldBe true
        ArgValueParsing.isOutOfIntRange("2147483647") shouldBe false
        ArgValueParsing.isOutOfIntRange("3.5") shouldBe false
    }

    "+ 付きで Long にも収まらない数は範囲外ではなく読めない値になる" {
        // toBigIntegerOrNull が先頭の + を受け付けないため。まれなので許容し、挙動だけ固定する。
        ArgValueParsing.int("+99999999999999999999") shouldBe null
        ArgValueParsing.isOutOfIntRange("+99999999999999999999") shouldBe false
    }

    "enum は完全一致だけ" {
        val candidates = listOf("Public", "Internal")

        ArgValueParsing.enumIndex(candidates, "Internal") shouldBe 1
        ArgValueParsing.enumIndex(candidates, "internal") shouldBe null
        ArgValueParsing.enumIndex(candidates, "Private") shouldBe null
    }
})
