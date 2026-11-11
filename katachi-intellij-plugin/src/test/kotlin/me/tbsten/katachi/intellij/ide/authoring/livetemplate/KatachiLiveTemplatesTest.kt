package me.tbsten.katachi.intellij.ide.authoring.livetemplate

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.IdeActions
import me.tbsten.katachi.intellij.AnalysisTestBase

/**
 * The live templates of liveTemplates/katachi.xml, expanded in a real Kotlin editor: the name each one takes from the file,
 * the imports the shortened names add, and that the expansion has no error. The katachi API is a stub with the same names
 * (the light project has no katachi jar), so these prove the shape and the imports; the samples compile the real thing.
 */
internal class KatachiLiveTemplatesTest : AnalysisTestBase() {

    override fun setUp() {
        super.setUp()
        KATACHI_STUBS.forEach { (path, text) -> myFixture.addFileToProject(path, text) }
    }

    /** Types [abbreviation] at the top level of [fileName] and presses Tab; outside template testing the template ends at once with its defaults. */
    private fun expand(fileName: String, abbreviation: String): Expansion {
        myFixture.configureByText(fileName, "package com.example\n\n<caret>\n")
        myFixture.type(abbreviation)
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
        return Expansion.of(myFixture.editor.document.text)
    }

    private fun assertNoErrors() {
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).map { it.description }
        assertTrue("errors in the expansion: $errors\n${myFixture.editor.document.text}", errors.isEmpty())
    }

    fun `test krole は ファイル名から Role 名と関数名を採り DeclarationContainerScope を import する`() {
        val expansion = expand("ServiceRole.kt", "krole")

        assertEquals(setOf("me.tbsten.katachi.dsl.DeclarationContainerScope"), expansion.imports)
        assertEquals(
            """
            fun DeclarationContainerScope.service() = "Service".role {
                title = "Service"
                summary = ""
                layout {
                }
            }
            """.trimIndent(),
            expansion.body,
        )
        assertNoErrors()
    }

    fun `test kgroup は ファイル名から group 名と xxxGroup の関数名を採る`() {
        val expansion = expand("DomainGroup.kt", "kgroup")

        assertEquals(setOf("me.tbsten.katachi.dsl.DeclarationContainerScope"), expansion.imports)
        assertEquals(
            """
            fun DeclarationContainerScope.domainGroup() = "Domain".group {
                title = "Domain"
                summary = ""
            }
            """.trimIndent(),
            expansion.body,
        )
        assertNoErrors()
    }

    fun `test 接尾辞の無いファイル名は 名前全体を大文字で始めて使う`() {
        val expansion = expand("useCase.kt", "krole")

        assertTrue(expansion.body, expansion.body.startsWith("fun DeclarationContainerScope.useCase() = \"UseCase\".role {"))
    }

    fun `test karch は projectArchitecture に architecture を入れる`() {
        val expansion = expand("ProjectArchitecture.kt", "karch")

        assertEquals(setOf("me.tbsten.katachi.dsl.architecture"), expansion.imports)
        assertEquals(
            """
            val projectArchitecture = architecture {
            }
            """.trimIndent(),
            expansion.body,
        )
        assertNoErrors()
    }

    fun `test kcontent は Role の名前の xxxFileContent に ktFileContent を切り出す`() {
        val expansion = expand("ServiceRole.kt", "kcontent")

        assertEquals(
            setOf(
                "me.tbsten.katachi.ExperimentalKatachiApi",
                "me.tbsten.katachi.dsl.RoleScope",
                "me.tbsten.katachi.konsist.declarations.ktFileContent",
            ),
            expansion.imports,
        )
        assertEquals(
            """
            @OptIn(ExperimentalKatachiApi::class)
            private fun RoleScope.serviceFileContent() = ktFileContent {
                `package`("**")
                required(`class`, fileName) {
                }
            }
            """.trimIndent(),
            expansion.body,
        )
        assertNoErrors()
    }

    fun `test ktemplate は Role の名前の xxxTemplate にテンプレートの中身を切り出す`() {
        val expansion = expand("ServiceRole.kt", "ktemplate")

        assertEquals(setOf("me.tbsten.katachi.ExperimentalKatachiApi", "me.tbsten.katachi.dsl.TemplateScope"), expansion.imports)
        assertEquals(
            "@OptIn(ExperimentalKatachiApi::class)\n" +
                "private fun TemplateScope.serviceTemplate(): String {\n" +
                "    val name = captureValue(\"name\")\n" +
                "    return \"\"\"\n" +
                "    \"\"\".trimIndent() + \"\\n\"\n" +
                "}",
            expansion.body,
        )
        assertNoErrors()
    }

    fun `test 打ち直した Role 名が 関数名に入る`() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("ServiceRole.kt", "package com.example\n\n<caret>\n")
        myFixture.type("krole")
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
        val state = checkNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor)) { "krole did not start" }

        myFixture.type("UseCase")
        state.gotoEnd(false)

        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("fun DeclarationContainerScope.useCase() = \"UseCase\".role {"))
    }

    fun `test 関数の中では展開しない`() {
        myFixture.configureByText("ServiceRole.kt", "package com.example\n\nfun f() {\n    <caret>\n}\n")
        myFixture.type("krole")
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)

        assertFalse(myFixture.editor.document.text, myFixture.editor.document.text.contains(".role {"))
    }

    fun `test どのテンプレートにも katachi グループの日本語と英語の説明がある`() {
        val templates = TemplateSettings.getInstance().templates.filter { it.groupName == "katachi" }

        assertEquals(setOf("krole", "kgroup", "karch", "kcontent", "ktemplate"), templates.map { it.key }.toSet())
        templates.forEach { assertTrue("${it.key}: ${it.description}", it.description.orEmpty().startsWith("katachi: ")) }
    }

    /** A document split into its imports and the rest, without blank lines: how many the formatter keeps is the IDE's code style, not the template's. */
    private data class Expansion(val imports: Set<String>, val body: String) {
        companion object {
            fun of(text: String): Expansion {
                val lines = text.lines().map { it.trimEnd() }
                val imports = lines.filter { it.startsWith("import ") }.map { it.removePrefix("import ") }.toSet()
                val body = lines.filterNot { it.isEmpty() || it.startsWith("import ") || it.startsWith("package ") }.joinToString("\n")
                return Expansion(imports, body)
            }
        }
    }

    private companion object {
        val KATACHI_STUBS: Map<String, String> = mapOf(
            "katachi/ExperimentalKatachiApi.kt" to """
                package me.tbsten.katachi

                @RequiresOptIn
                annotation class ExperimentalKatachiApi
            """.trimIndent(),
            "katachi/Dsl.kt" to """
                package me.tbsten.katachi.dsl

                interface DeclarationContainerScope {
                    fun String.role(block: RoleScope.() -> Unit)
                    fun String.group(block: GroupScope.() -> Unit)
                }

                interface RoleScope {
                    var title: String
                    var summary: String?
                    fun layout(block: () -> Unit)
                }

                interface GroupScope : DeclarationContainerScope {
                    var title: String
                    var summary: String?
                }

                class Architecture

                fun architecture(block: DeclarationContainerScope.() -> Unit): Architecture = Architecture()

                @me.tbsten.katachi.ExperimentalKatachiApi
                interface TemplateScope {
                    fun captureValue(name: String): String
                }
            """.trimIndent(),
            "katachi/KtFileContent.kt" to """
                package me.tbsten.katachi.konsist.declarations

                interface KtFileContentScope {
                    val fileName: String
                    val `class`: Any
                    fun `package`(pattern: String)
                    fun required(vararg line: Any, block: () -> Unit = {})
                }

                @me.tbsten.katachi.ExperimentalKatachiApi
                fun me.tbsten.katachi.dsl.RoleScope.ktFileContent(block: KtFileContentScope.() -> Unit) {}
            """.trimIndent(),
        )
    }
}
