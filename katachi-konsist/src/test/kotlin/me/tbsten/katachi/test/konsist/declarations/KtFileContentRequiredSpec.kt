package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/** Which lines are required: the same rule as `layout { }`. */
class KtFileContentRequiredSpec : FreeSpec({
    "宣言の行" - {
        "* を含まない名前は必須、含む名前は任意" {
            dumpOf {
                `class`("Fixed")
                `class`("*Free")
                `fun`("on*")
                `val`("**")
            } shouldBe """
                required class{} Fixed
                optional class{} *Free
                optional fun{} on*
                optional val{} **
            """.trimIndent()
        }

        "\\* は文字の * なので、1 つに決まる名前として必須" {
            dumpOf { `class`("""Star\*""") } shouldBe """required class{} Star\*"""
        }

        "optional() で任意になる。* を含む行の optional() は何も変えない" {
            dumpOf {
                `class`("Fixed").optional()
                `fun`("main").optional()
                `val`("TAG").optional()
                `typealias`("Key").optional()
                `class`("*Free").optional()
            } shouldBe """
                optional class{} Fixed
                optional fun{} main
                optional val{} TAG
                optional typealias{} Key
                optional class{} *Free
            """.trimIndent()
        }

        "fileName や captureValue を埋めた名前は、埋めた後に * が無ければ必須" {
            dumpOf(fileName = "HomeViewModel", captures = mapOf("feature" to "home")) {
                `class`(fileName)
                `object`("${captureValue("feature")}Module")
            } shouldBe """
                required class{} HomeViewModel
                required object{} homeModule
            """.trimIndent()
        }

        "禁止の行は必須にならない" {
            dumpOf { not.`class`("Fixed") }.startsWith("prohibited") shouldBe true
            rulesOf { not.`class`("Fixed") }.firstDeclaration.required shouldBe false
        }

        "メンバも同じ決まり" {
            dumpOf {
                `class`("Host") {
                    `val`("uiState")
                    `fun`("on*")
                    companion.`object`("Companion").optional()
                }
            } shouldBe """
                required class{} Host
                  members:
                    required val{} uiState
                    optional fun{} on*
                    optional object{companion} Companion
            """.trimIndent()
        }
    }

    "import の行" - {
        "* を含まないパスは必須、含むパスは任意、optional() で任意、禁止の行は必須にならない" {
            dumpOf {
                `import`("com.example.App")
                `import`("androidx.compose.**")
                `import`("com.example.Logger").optional()
                not.`import`("android.content.Context")
            } shouldBe """
                import com.example.App required
                import androidx.compose.** optional
                import com.example.Logger optional
                import android.content.Context prohibited
            """.trimIndent()
        }

        "captureValue を埋めたパスも、埋めた後に * が無ければ必須" {
            dumpOf(captures = mapOf("app" to "com.example")) {
                `import`("${captureValue("app")}.App")
            } shouldBe "import com.example.App required"
        }
    }
})
