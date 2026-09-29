package me.tbsten.katachi.intellij.ide.injection

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.DumbModeTestUtils
import me.tbsten.katachi.intellij.AnalysisTestBase
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * The content of a katachi template is highlighted as the language of the file it writes: which
 * strings get Kotlin injected, which do not, and that it holds in dumb mode and while typing.
 *
 * The definitions below are only parsed, never resolved (katachi is not on the test's classpath),
 * which is also all the injector itself looks at.
 */
internal class KatachiTemplateInjectorTest : AnalysisTestBase() {

    fun `test ktFile の template が返す文字列は Kotlin として注入される`() {
        configure(
            """
            "*UseCase".ktFile().template {
                "interface SampleUseCase"
            }
            """,
        )

        val injected = injectedInto("SampleUseCase")

        assertEquals(KotlinLanguage.INSTANCE, injected?.language)
        assertEquals("kt", injected?.virtualFile?.extension)
        assertEquals("interface SampleUseCase", injected?.text)
    }

    fun `test ktsFile の template が返す文字列は Kotlin Script として注入される`() {
        configure(
            """
            "build.gradle".ktsFile().template {
                ${"\"\"\""}
                    plugins { id("scriptMarker") }
                    dependencies { implementation(libs.katachi) }
                ${"\"\"\""}.trimIndent()
            }
            """,
        )

        val injected = injectedInto("scriptMarker")

        assertEquals(KotlinLanguage.INSTANCE, injected?.language)
        assertEquals("kts", injected?.virtualFile?.extension)
        assertEquals(emptyList<String>(), errorsIn(hostContaining("scriptMarker")))
    }

    fun `test package と import を持つ ktFile の中身にもエラーを出さない`() {
        configure(
            """
            "*Repository".ktFile().template {
                ${"\"\"\""}
                    package com.example.data

                    import kotlinx.coroutines.flow.Flow

                    interface PlainRepository {
                        fun load(): Flow<String>
                    }
                ${"\"\"\""}.trimIndent()
            }
            """,
        )

        assertEquals(KotlinLanguage.INSTANCE, injectedInto("PlainRepository")?.language)
        assertEquals(emptyList<String>(), errorsIn(hostContaining("PlainRepository")))
    }

    fun `test 名前が kt と kts で終わる file の template も注入され、ほかの拡張子は注入されない`() {
        configure(
            """
            "*Repository.kt".file().template { "interface KtByName" }
            "${'$'}{capture("name")}Settings.kts".file().template { "val ktsByName = 1" }
            "README.md".file().template { "# NotKotlin" }
            "${'$'}{capture("name")}".file().template { "EndsWithInterpolation" }
            """,
        )

        assertEquals("kt", injectedInto("KtByName")?.virtualFile?.extension)
        assertEquals("kts", injectedInto("ktsByName")?.virtualFile?.extension)
        assertNull(injectedInto("NotKotlin"))
        assertNull(injectedInto("EndsWithInterpolation"))
    }

    fun `test trimIndent と改行の連結は両方の文字列に注入され、補間は名前の仮の識別子になる`() {
        configure(
            """
            "*Service".ktFile()
                .template {
                    val name = captureValue("name")
                    val kdoc by stringParameter(default = "unusedDefault")
                    ${"\"\"\""}
                        /** ${'$'}kdoc */
                        class ${'$'}{name}Service {
                            fun execute(): String = ${'$'}{name.length}
                        }
                    ${"\"\"\""}.trimIndent() + "\n"
                }
            """,
        )

        val body = injectedInto("Service {")
        assertEquals(KotlinLanguage.INSTANCE, body?.language)
        val text = body!!.text
        assertTrue(text, text.contains("/** kdoc */"))
        assertTrue(text, text.contains("class nameService {"))
        assertTrue(text, text.contains("fun execute(): String = katachiValue"))
        assertEquals("the trailing newline literal is injected too", KotlinLanguage.INSTANCE, injectedIntoExactly("\"\\n\"")?.language)
        assertNull("a default of a parameter is not the content", injectedInto("unusedDefault"))
    }

    fun `test template の中でも戻り値でない文字列には注入しない`() {
        configure(
            """
            "*Controller".ktFile().template {
                val resource = captureValue("resourceKey")
                require(resource.isNotEmpty()) { "requireMessage" }
                val unused = "localValue"
                "class Controller"
            }
            """,
        )

        assertNull(injectedInto("resourceKey"))
        assertNull(injectedInto("requireMessage"))
        assertNull(injectedInto("localValue"))
        assertNotNull(injectedInto("class Controller"))
    }

    fun `test return@template と if や when の分岐の値にも注入する`() {
        configure(
            """
            "*A".ktFile().template {
                if (isPreview) return@template "class ReturnedEarly"
                if (flag) "class ThenBranch" else { log(); "class ElseBranch" }
            }
            "*B".ktFile().template {
                when (kind) {
                    "whenSubject" -> "class WhenBranch"
                    else -> ("class ParenthesizedBranch")
                }
            }
            """,
        )

        listOf("ReturnedEarly", "ThenBranch", "ElseBranch", "WhenBranch", "ParenthesizedBranch").forEach { marker ->
            assertEquals(marker, KotlinLanguage.INSTANCE, injectedInto(marker)?.language)
        }
        assertNull("a when condition is not the content", injectedInto("whenSubject"))
    }

    fun `test katachi のファイル宣言でない template や宣言の名前には注入しない`() {
        configure(
            """
            something.template { "NotOnDeclaration" }
            "*X".ktFile().render { "OtherFunction" }
            "NameOfDeclaration".ktFile().template { "class Real" }
            "*Y".ktFile("argument").template { "WithArguments" }
            template { "BareTemplate" }
            """,
        )

        assertNull(injectedInto("NotOnDeclaration"))
        assertNull(injectedInto("OtherFunction"))
        assertNull(injectedInto("NameOfDeclaration"))
        assertNull(injectedInto("WithArguments"))
        assertNull(injectedInto("BareTemplate"))
        assertNotNull(injectedInto("class Real"))
    }

    fun `test 補間だけの文字列や空文字には注入しない`() {
        configure(
            """
            "*A".ktFile().template { "${'$'}onlyInterpolation" }
            "*B".ktFile().template { "" }
            """,
        )

        assertNull(injectedIntoExactly("\"${'$'}onlyInterpolation\""))
        assertNull(injectedIntoExactly("\"\""))
    }

    fun `test dumb mode 中も注入する`() {
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            configure(
                """
                "*UseCase".ktFile().template { "interface DumbUseCase" }
                """,
            )

            assertEquals(KotlinLanguage.INSTANCE, injectedInto("DumbUseCase")?.language)
        }
    }

    fun `test 文字列に打ち込んでも注入が続き、宣言を ktFile から外すと注入が消える`() {
        configure(
            """
            "*UseCase".ktFile().template { "interface Typed<caret>UseCase" }
            """,
        )

        // The caret is in the fragment, so this types through the injected editor, as the user would.
        myFixture.type("More")
        assertEquals("interface TypedMoreUseCase", injectedInto("TypedMoreUseCase")?.text)

        // "ktFile" -> "txtFile": no longer a Kotlin file declaration.
        val document = hostFile.viewProvider.document!!
        WriteCommandAction.runWriteCommandAction(project) {
            val at = document.text.indexOf("ktFile")
            document.replaceString(at, at + "kt".length, "txt")
        }
        assertTrue(document.text, document.text.contains("\"*UseCase\".txtFile()"))
        assertNull(injectedInto("TypedMoreUseCase"))
    }

    fun `test 補間で構文が崩れても注入した中身にエラーを出さず、補間の無い中身の構文エラーは出す`() {
        configure(
            """
            "*Screen".ktFile().template {
                val imports = "import a.B"
                ${"\"\"\""}
                    package sample
                    ${'$'}imports
                    ${'$'}{if (true) "@Composable " else ""}fun HighlightedScreen() {}
                ${"\"\"\""}.trimIndent()
            }
            "*Broken".ktFile().template { "class BrokenWithoutValues {{{ fun (" }
            """,
        )
        val host = hostContaining("HighlightedScreen")

        assertEquals(KotlinLanguage.INSTANCE, injectedInto("HighlightedScreen")?.language)
        // `katachiValuefun HighlightedScreen()` does not parse; the value would have made it `@Composable fun`.
        assertEquals(emptyList<String>(), errorsIn(host))
        // Without a value in it, what does not parse is the template's own mistake, and is shown.
        assertFalse(errorsIn(hostContaining("BrokenWithoutValues")).isEmpty())
    }

    fun `test injector は1つだけ登録され dumb aware である`() {
        val registered = MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME.getExtensions(project).filterIsInstance<KatachiTemplateInjector>()

        assertEquals(1, registered.size)
        assertTrue(registered.single().isDumbAware)
    }

    /** The definition file itself; `myFixture.file` is the fragment instead while the caret is in one. */
    private lateinit var hostFile: PsiFile

    private fun configure(source: String) {
        val configured = myFixture.configureByText("Architecture.kt", "fun definitions() {\n${source.trimIndent()}\n}\n")
        hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(configured)
    }

    /** The errors highlighted inside [host]: the definition itself does not resolve here, only the template's content counts. */
    private fun errorsIn(host: KtStringTemplateExpression): List<String> =
        myFixture.doHighlighting(HighlightSeverity.ERROR)
            .filter { host.textRange.contains(it.startOffset) }
            .map { "${it.description} at ${it.startOffset}" }

    /** The one string whose text contains [marker], so that nested strings (`capture("x")`) do not count. */
    private fun hostContaining(marker: String): KtStringTemplateExpression {
        val hosts = PsiTreeUtil.findChildrenOfType(hostFile, KtStringTemplateExpression::class.java)
            .filter { it.text.contains(marker) }
        // The innermost one when strings are nested.
        return hosts.single { host -> hosts.none { it !== host && PsiTreeUtil.isAncestor(host, it, true) } }
    }

    private fun injectedInto(marker: String): PsiFile? {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        return injectedFileOf(hostContaining(marker))
    }

    private fun injectedIntoExactly(text: String): PsiFile? =
        injectedFileOf(PsiTreeUtil.findChildrenOfType(hostFile, KtStringTemplateExpression::class.java).single { it.text == text })

    private fun injectedFileOf(host: KtStringTemplateExpression): PsiFile? = runReadActionBlocking {
        val files = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host).orEmpty()
            .map { it.first.containingFile }.distinct()
        assertTrue("at most one injection per string: $files", files.size <= 1)
        files.singleOrNull()
    }
}
