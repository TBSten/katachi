package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.internal.CaptureTransform
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.ModuleCapture
import me.tbsten.katachi.dsl.internal.PathCapture
import me.tbsten.katachi.dsl.internal.fillCapturedPath
import me.tbsten.katachi.dsl.internal.fillCapturedSegments

/**
 * `CaptureFill.kt`: the one place a capture occurrence is written into a path. The caller says
 * what each occurrence reads as; the function itself neither converts nor interprets anything.
 */
class CaptureFillSpec : FreeSpec({
    val captures = listOf(
        PathCapture(1, 0, "f", null),
        PathCapture(2, 0, "a", null),
        PathCapture(2, 1, "f", CaptureTransform.PascalCase),
    )

    "出現ごとに渡した値で左から埋まり、関数の中では変換しない" {
        val filled = fillCapturedSegments("feature/*/*-*.kt", captures) { capture ->
            if (capture.transform != null) "DERIVED" else capture.name.uppercase()
        }

        filled shouldBe listOf("feature", "F", "A-DERIVED.kt")
    }

    "値の無い出現の * はそのまま残る" {
        fillCapturedSegments("feature/*/*-*.kt", captures) { capture -> "x".takeIf { capture.name == "a" } } shouldBe
            listOf("feature", "*", "x-*.kt")
    }

    "名前の無い * は何番目かで埋め、エスケープした \\* は数えない" {
        val filled = fillCapturedSegments(
            path = "data/\\**-*.kt",
            captures = listOf(PathCapture(1, 1, "source", null)),
            valueOf = { "remote" },
            unnamedOf = { _, wildcardIndex -> "unnamed$wildcardIndex" },
        )

        filled shouldBe listOf("data", "\\*unnamed0-remote.kt")
    }

    "fillCapturedPath はモジュールキーの * も名前の値で埋める" {
        val variant = LayoutCaptures(
            pathCaptures = listOf(PathCapture(2, 0, "name", null)),
            moduleCapture = ModuleCapture(":feature:*", listOf("feature")),
        )

        fillCapturedPath("feature/*/*.kt", variant, moduleValues = mapOf("feature" to "home")) { "Home" } shouldBe
            "feature/home/Home.kt"
    }

    "fillCapturedPath はモジュールの値が無ければパスの capture だけを埋める" {
        val variant = LayoutCaptures(
            pathCaptures = listOf(PathCapture(2, 0, "name", null)),
            moduleCapture = ModuleCapture(":feature:*", listOf("feature")),
        )

        fillCapturedPath("feature/*/*.kt", variant, moduleValues = emptyMap()) { "Home" } shouldBe "feature/*/Home.kt"
    }
})
