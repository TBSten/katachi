package me.tbsten.katachi.intellij.ide.injection

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import me.tbsten.katachi.intellij.AnalysisTestBase
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import java.io.File

/**
 * Verification: one architecture-definition .kt file holding several `.template { }` blocks (in a
 * loop, several ids of one role, next to a broken one, and the real definition files of the samples).
 */
internal class MultiTemplateInjectionTest : AnalysisTestBase() {
    private lateinit var hostFile: PsiFile

    private fun configure(source: String, name: String = "Architecture.kt") {
        val configured = myFixture.configureByText(name, source.trimIndent())
        hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(configured)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun hosts(): List<KtStringTemplateExpression> = PsiTreeUtil.findChildrenOfType(hostFile, KtStringTemplateExpression::class.java).toList()

    private fun hostContaining(marker: String): KtStringTemplateExpression {
        val all = hosts().filter { it.text.contains(marker) }
        return all.single { host -> all.none { it !== host && PsiTreeUtil.isAncestor(host, it, true) } }
    }

    private fun injectedOf(host: KtStringTemplateExpression): List<PsiFile> = runReadActionBlocking {
        InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host).orEmpty().map { it.first.containingFile }.distinct()
    }

    private fun errorsIn(host: KtStringTemplateExpression): List<String> =
        myFixture.doHighlighting(HighlightSeverity.ERROR).filter { host.textRange.contains(it.startOffset) }.map { "${it.description} at ${it.startOffset}" }

    fun `test forEach の中で id 違いに attach された template はそれぞれ自分の文字列にだけ注入され混ざらない`() {
        configure(
            """
            fun define() {
                Domain.entries.forEach { domain ->
                    domain.packageName {
                        "${'$'}{domain.name}Repository".ktFile()
                        "${'$'}{domain.name}${'$'}{capture("name")}Repository".ktFile()
                            .template(id = domain.templateId) { "interface IfaceMarker(val a: Int)" }
                        "${'$'}{domain.name}${'$'}{capture("name")}RepositoryImpl".ktFile()
                            .template(id = "${'$'}{domain.templateId}Impl") {
                                ${"\"\"\""}
                                    class ImplMarker : IfaceMarker(1) {
                                        override fun load() = ""
                                    }
                                ${"\"\"\""}.trimIndent() + "\n"
                            }
                    }
                }
            }
            """,
        )
        val iface = injectedOf(hostContaining("IfaceMarker(val"))
        val impl = injectedOf(hostContaining("class ImplMarker"))
        assertEquals(1, iface.size)
        assertEquals(1, impl.size)
        assertEquals(KotlinLanguage.INSTANCE, iface.single().language)
        assertEquals("interface IfaceMarker(val a: Int)", iface.single().text)
        assertFalse(impl.single().text, impl.single().text.contains("interface IfaceMarker(val a"))
        assertTrue(impl.single().text, impl.single().text.contains("class ImplMarker : IfaceMarker(1)"))
        // The two contents and the `+ "\n"` literal after the second: nothing else (not the file names, not the ids) is injected.
        assertEquals(3, hosts().count { injectedOf(it).isNotEmpty() })
        assertEquals(listOf("interface IfaceMarker(val a: Int)"), hosts().filter { it.text.contains("IfaceMarker(val") }.map { injectedOf(it).single().text })
        assertEquals(emptyList<String>(), errorsIn(hostContaining("IfaceMarker(val")))
        assertEquals(emptyList<String>(), errorsIn(hostContaining("class ImplMarker")))
    }

    fun `test 1つの役割に id 違いの template が4つあっても各文字列は別々に注入され構文エラーは壊れた1つの文字列にだけ出る`() {
        configure(
            """
            fun define() {
                "Repository" {
                    layout {
                        "*Repository".ktFile().template(id = "user") { "class UserRepo {}" }
                        "*Repository".ktFile().template(id = "settings") { "class SettingsRepo {}" }
                        "*Repository".ktFile().template(id = "broken") { "class BrokenRepo {{{ fun (" }
                        "build.gradle".ktsFile().template(id = "gradle") { "plugins { id(\"scriptRepo\") }" }
                    }
                }
            }
            """,
        )
        val texts = listOf("UserRepo", "SettingsRepo", "BrokenRepo", "scriptRepo").map { injectedOf(hostContaining(it)) }
        assertEquals(listOf(1, 1, 1, 1), texts.map { it.size })
        assertEquals(4, hosts().count { injectedOf(it).isNotEmpty() })
        assertEquals(listOf("kt", "kt", "kt", "kts"), texts.map { it.single().virtualFile.extension })
        assertEquals("class UserRepo {}", texts[0].single().text)
        assertEquals("class SettingsRepo {}", texts[1].single().text)
        assertEquals(emptyList<String>(), errorsIn(hostContaining("UserRepo")))
        assertEquals(emptyList<String>(), errorsIn(hostContaining("SettingsRepo")))
        assertEquals(emptyList<String>(), errorsIn(hostContaining("scriptRepo")))
        assertFalse(errorsIn(hostContaining("BrokenRepo")).isEmpty())
    }

    fun `test template が入れ子の lambda を持ち別の template の直後に続いても外側の文字列に混ざらない`() {
        configure(
            """
            fun define() {
                "*A".ktFile().template {
                    val items = listOf("one", "two").map { "item ${'$'}it" }
                    items.joinToString("\n") { "fun f${'$'}it() {}" }
                }
                "*B".ktFile().template { "class BMarker" }
            }
            """,
        )
        // The last statement of template A is a call, not a string: nothing of A is injected, the nested map / joinToString lambdas neither.
        assertEquals(1, hosts().count { injectedOf(it).isNotEmpty() })
        assertEquals("class BMarker", injectedOf(hostContaining("BMarker")).single().text)
    }

    fun `test 実際の sample の定義ファイルで注入される文字列と template の数`() {
        // The repository this build sits in (the test runs in katachi-intellij-plugin/), unless pointed elsewhere.
        val repoRoot = File(System.getProperty("katachi.verify.repo", "..")).canonicalFile
        val report = StringBuilder()
        for (sample in listOf("jvm", "kmp", "android")) {
            val dir = File(repoRoot, "sample/$sample/architecture-test/src/test/kotlin")
            dir.walkTopDown().filter { it.extension == "kt" }.sortedBy { it.path }.forEach { file ->
                val text = file.readText()
                val templates = Regex("""\.template\s*[({]""").findAll(text).count()
                if (templates == 0) return@forEach
                configure(text, file.name)
                val injected = hosts().filter { injectedOf(it).isNotEmpty() }
                val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).filter { h -> injected.any { it.textRange.contains(h.startOffset) } }
                report.appendLine("$sample/${file.name}: templates=$templates injected=${injected.size} errorsInInjectedStrings=${errors.size}")
                errors.forEach { report.appendLine("    ${it.description} at ${it.startOffset}") }
                // Distinct strings never share one fragment.
                val fragments = injected.flatMap { injectedOf(it) }
                assertEquals("$sample/${file.name}: a fragment is shared by two strings", fragments.size, fragments.distinct().size)
            }
        }
        File(System.getProperty("katachi.verify.dump", "build/verify-multi-template/dump") + ".injection.txt")
            .apply { parentFile?.mkdirs() }
            .writeText(report.toString())
    }
}
