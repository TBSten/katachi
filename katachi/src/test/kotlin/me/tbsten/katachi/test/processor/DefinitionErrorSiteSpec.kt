package me.tbsten.katachi.test.processor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException
import me.tbsten.katachi.ide.KatachiDefinitionJsonIoException
import me.tbsten.katachi.processor.internal.definitionErrorSiteOf
import me.tbsten.katachi.processor.internal.definitionErrorsOf
import me.tbsten.katachi.util.runCatchingScoped

/** `definitionErrorSiteOf` and `definitionErrorsOf`: K-1 and K-7 of the IDE definition authoring plan. */
class DefinitionErrorSiteSpec : FreeSpec({
    "definitionErrorSiteOf" - {
        "declaredAt を持つ例外はその値" {
            val thrown = KatachiTemplateOnWildcardException("data.Repository", "repository/*.kt", DeclarationSite("ScreenRole.kt", 4))

            definitionErrorSiteOf(thrown) shouldBe DeclarationSite("ScreenRole.kt", 4)
        }

        "declaredAt を持たない例外は、利用者の lambda から投げた行" {
            var line = 0
            val thrown = runCatching { line = Throwable().stackTrace.first().lineNumber; throw NoSiteException() }.exceptionOrNull()!!

            val site = definitionErrorSiteOf(thrown)!!
            site.fileName shouldBe "DefinitionErrorSiteSpec.kt"
            site.lineNumber shouldBe line
        }

        "外部ライブラリ(jar)の中から投げても、場所はライブラリのファイルでなく利用者の呼び出しの行" {
            // The top frame is a class of a jar on the test classpath (kotest), as when a template
            // calls into a library and the library throws.
            val libraryFrame = StackTraceElement("io.kotest.core.spec.style.FreeSpec", "invoke", "FreeSpec.kt", 7)
            var line = 0
            val thrown = NoSiteException().apply {
                line = Throwable().stackTrace.first().lineNumber
                stackTrace = arrayOf(libraryFrame, StackTraceElement("me.tbsten.katachi.test.processor.DefinitionErrorSiteSpec", "invoke", "DefinitionErrorSiteSpec.kt", line))
            }

            val site = definitionErrorSiteOf(thrown)!!
            site.fileName shouldBe "DefinitionErrorSiteSpec.kt"
            site.lineNumber shouldBe line
        }

        "declaredAt が Unknown なら次の規則(スタック)へ" {
            val thrown = runCatching { throw UnknownSiteException() }.exceptionOrNull()!!

            definitionErrorSiteOf(thrown)!!.fileName shouldBe "DefinitionErrorSiteSpec.kt"
        }

        "フレームが全部 katachi なら null" {
            val thrown = NoSiteException().apply {
                stackTrace = arrayOf(StackTraceElement("me.tbsten.katachi.dsl.Group", "group", "Group.kt", 10))
            }

            definitionErrorSiteOf(thrown).shouldBeNull()
        }

        "行番号の無いフレームは場所にしない" {
            val thrown = NoSiteException().apply {
                stackTrace = arrayOf(StackTraceElement("com.example.Define", "define", "Define.kt", -1))
            }

            definitionErrorSiteOf(thrown).shouldBeNull()
        }
    }

    "definitionErrorsOf" - {
        val first = NoSiteException()
        val second = UnknownSiteException()

        "定義の誤りはそれ 1 件" {
            definitionErrorsOf(first) shouldContainExactly listOf(first)
        }

        "ExceptionInInitializerError は中を開く" {
            definitionErrorsOf(ExceptionInInitializerError(first)) shouldContainExactly listOf(first)
        }

        "KatachiMultipleFailuresException は入っている定義の誤りを 1 件ずつ(2 件なら 2 件)" {
            val multiple = runCatchingScoped {
                failure(first)
                failure(second)
            }.exceptionOrNull()!!

            definitionErrorsOf(multiple) shouldContainExactly listOf(first, second)
        }

        "ExceptionInInitializerError の中の複数も開く" {
            val multiple = runCatchingScoped {
                failure(first)
                failure(IllegalStateException("not the definition"))
                failure(second)
            }.exceptionOrNull()!!

            definitionErrorsOf(ExceptionInInitializerError(multiple)) shouldContainExactly listOf(first, second)
        }

        "環境の誤り・katachi の不具合・それ以外は入れない" {
            definitionErrorsOf(KatachiDefinitionJsonIoException("out.json", RuntimeException("disk"))).shouldBeEmpty()
            definitionErrorsOf(IllegalStateException("boom")).shouldBeEmpty()
            definitionErrorsOf(ExceptionInInitializerError(IllegalStateException("boom"))).shouldBeEmpty()
            definitionErrorsOf(ExceptionInInitializerError()).shouldBeEmpty()
        }
    }
})

@OptIn(ExperimentalKatachiApi::class)
internal class NoSiteException : KatachiDeclarationException("no site")

@OptIn(ExperimentalKatachiApi::class)
internal class UnknownSiteException : KatachiDeclarationException("unknown site") {
    @Suppress("unused")
    val declaredAt: DeclarationSite = DeclarationSite.Unknown
}
