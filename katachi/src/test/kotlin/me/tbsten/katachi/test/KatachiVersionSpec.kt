package me.tbsten.katachi.test

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import me.tbsten.katachi.internal.KatachiVersion

/** The version `tool.driver.semanticVersion` reports, read from the resource the build expands. */
class KatachiVersionSpec : FreeSpec({
    "resources から版が読める" {
        KatachiVersion.current() shouldMatch Regex("""\d+\.\d+\.\d+.*""")
    }

    "version= の行を取り、無ければ 0.0.0-unknown" {
        KatachiVersion.parse("version=1.2.3\n") shouldBe "1.2.3"
        KatachiVersion.parse("# written by the build\nversion = 1.2.3-SNAPSHOT\n".replace(" = ", "=")) shouldBe "1.2.3-SNAPSHOT"
        KatachiVersion.parse("version=\n") shouldBe "0.0.0-unknown"
        KatachiVersion.parse("other=1\n") shouldBe "0.0.0-unknown"
        KatachiVersion.parse(null) shouldBe "0.0.0-unknown"
    }
})
