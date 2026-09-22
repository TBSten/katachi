package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.scan.ViolationDetail

class ViolationDetailSpec : FreeSpec({
    "等値比較" - {
        "同じ label と value を持つインスタンス同士は shouldBe で等しい" {
            val actual = ViolationDetail("Rule", "LongMethod")
            actual shouldBe ViolationDetail("Rule", "LongMethod")
        }

        "label か value のどちらかが違えば等しくない" {
            val actual = ViolationDetail("Rule", "LongMethod")
            (actual == ViolationDetail("Rule", "ShortMethod")) shouldBe false
            (actual == ViolationDetail("Line", "LongMethod")) shouldBe false
        }
    }

    "文字列表現" - {
        "label: value の形になる" {
            ViolationDetail("Rule", "LongMethod").toString() shouldBe "Rule: LongMethod"
        }
    }
})
