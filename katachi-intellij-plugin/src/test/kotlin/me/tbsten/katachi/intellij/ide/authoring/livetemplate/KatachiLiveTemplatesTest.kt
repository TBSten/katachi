package me.tbsten.katachi.intellij.ide.authoring.livetemplate

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import me.tbsten.katachi.intellij.AnalysisTestBase
import java.io.File

/**
 * The live templates of liveTemplates/katachi.xml, expanded in a real Kotlin editor: the name each one takes from the file,
 * the imports the shortened names add, and that the expansion has no error. The katachi API is a stub with the same names
 * (the light project has no katachi jar), so these prove the shape and the imports; the samples compile the real thing.
 */
internal class KatachiLiveTemplatesTest : AnalysisTestBase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = WITH_KOTLIN_STDLIB

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

    fun `test katachirole は ファイル名から Role 名と関数名を採り DeclarationContainerScope を import する`() {
        val expansion = expand("ServiceRole.kt", "katachirole")

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

    fun `test katachigroup は ファイル名から group 名と xxxGroup の関数名を採る`() {
        val expansion = expand("DomainGroup.kt", "katachigroup")

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
        val expansion = expand("useCase.kt", "katachirole")

        assertTrue(expansion.body, expansion.body.startsWith("fun DeclarationContainerScope.useCase() = \"UseCase\".role {"))
    }

    fun `test katachiarchitecture は projectArchitecture に architecture を入れる`() {
        val expansion = expand("ProjectArchitecture.kt", "katachiarchitecture")

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

    fun `test katachifilecontent は Role の名前の xxxFileContent に ktFileContent を切り出す`() {
        val expansion = expand("ServiceRole.kt", "katachifilecontent")

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

    fun `test katachitemplate は Role の名前の xxxTemplate にテンプレートの中身を切り出す`() {
        val expansion = expand("ServiceRole.kt", "katachitemplate")

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

    fun `test katachitest は FileConstraintCheck で検査するテストを出し assert を import する`() {
        val expansion = expand("ProjectArchitectureTest.kt", "katachitest")

        assertEquals(
            setOf(
                "me.tbsten.katachi.ExperimentalKatachiApi",
                "me.tbsten.katachi.check.FileConstraintCheck",
                "me.tbsten.katachi.check.assert",
                "org.junit.jupiter.api.Test",
            ),
            expansion.imports,
        )
        assertEquals(
            """
            @OptIn(ExperimentalKatachiApi::class)
            class ProjectArchitectureTest {
                @Test
                fun `the project matches the definition`() {
                    projectArchitecture.assert(FileConstraintCheck())
                }
            }
            """.trimIndent(),
            expansion.body,
        )
    }

    fun `test katachitemplateset は templateSet を xxxTemplateSet に切り出す`() {
        val expansion = expand("ApiGroup.kt", "katachitemplateset")

        assertEquals(setOf("me.tbsten.katachi.ExperimentalKatachiApi", "me.tbsten.katachi.dsl.DeclarationContainerScope"), expansion.imports)
        assertEquals(
            """
            @OptIn(ExperimentalKatachiApi::class)
            fun DeclarationContainerScope.nameTemplateSet() = "Name".templateSet {
                title = "Name"
                summary = ""
                val name by stringParameter()
                template("group.Role") {
                    arg("name", name)
                }
            }
            """.trimIndent(),
            expansion.body,
        )
        assertNoErrors()
    }

    fun `test katachikonsist は Role の中で 名前付きの konsist を出し konsist を import する`() {
        myFixture.configureByText("ServiceRole.kt", "package com.example\n\nfun f() {\n    <caret>\n}\n")
        myFixture.type("katachikonsist")
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
        val expansion = Expansion.of(myFixture.editor.document.text)

        assertEquals(setOf("me.tbsten.katachi.konsist.konsist"), expansion.imports)
        assertEquals(
            squeezed(
                """
                fun f() {
                    "what the files must be".konsist {
                        classes().must { }
                    }
                }
                """.trimIndent(),
            ),
            squeezed(expansion.body),
        )
    }

    fun `test 既に import してあれば 同じ import を足さない`() {
        myFixture.configureByText("ProjectArchitectureTest.kt", "package com.example\n\nimport me.tbsten.katachi.check.assert\n\n<caret>\n")
        myFixture.type("katachitest")
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)

        assertEquals(1, Regex("import me\\.tbsten\\.katachi\\.check\\.assert\\b").findAll(myFixture.editor.document.text).count())
    }

    fun `test 打ち直した Role 名が 関数名に入る`() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("ServiceRole.kt", "package com.example\n\n<caret>\n")
        myFixture.type("katachirole")
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
        val state = checkNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor)) { "katachirole did not start" }

        myFixture.type("UseCase")
        // Finishing the template shortens the qualified names, a PSI change that needs a command (the Tab of a real editor runs in one).
        CommandProcessor.getInstance().executeCommand(project, { state.gotoEnd(false) }, null, null)

        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("fun DeclarationContainerScope.useCase() = \"UseCase\".role {"))
    }

    fun `test 関数の中では展開しない`() {
        myFixture.configureByText("ServiceRole.kt", "package com.example\n\nfun f() {\n    <caret>\n}\n")
        myFixture.type("katachirole")
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)

        assertFalse(myFixture.editor.document.text, myFixture.editor.document.text.contains(".role {"))
    }

    fun `test どのテンプレートにも katachi グループの日本語と英語の説明がある`() {
        val templates = TemplateSettings.getInstance().templates.filter { it.groupName == "katachi" }

        assertEquals(
            setOf(
                "katachirole", "katachigroup", "katachiarchitecture", "katachifilecontent", "katachitemplate",
                "katachitest", "katachitemplateset", "katachikonsist",
            ),
            templates.map { it.key }.toSet(),
        )
        templates.forEach { assertTrue("${it.key}: ${it.description}", it.description.orEmpty().startsWith("katachi: ")) }
    }

    /** [text] with every run of spaces made one, for an empty lambda the formatter may write as `{ }` or `{  }`. */
    private fun squeezed(text: String): String = text.replace(Regex(" +"), " ")

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
        /** The default light project plus the Kotlin stdlib that the test task names in `katachi.test.kotlinStdlib`. */
        val WITH_KOTLIN_STDLIB: LightProjectDescriptor = object : DefaultLightProjectDescriptor() {
            override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
                super.configureModule(module, model, contentEntry)
                val jar = File(checkNotNull(System.getProperty("katachi.test.kotlinStdlib")) { "katachi.test.kotlinStdlib is not set: run the test from Gradle" })
                PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
            }
        }

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
                    @me.tbsten.katachi.ExperimentalKatachiApi
                    fun String.templateSet(block: TemplateSetScope.() -> Unit)
                }

                class TemplateParameter<T>(private val value: T) {
                    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = value
                }

                interface TemplateSetScope {
                    var title: String?
                    var summary: String?
                    fun stringParameter(default: String? = null): TemplateParameter<String>
                    fun template(specifier: String, block: TemplateSetMemberScope.() -> Unit = {})
                }

                interface TemplateSetMemberScope {
                    fun arg(name: String, value: String)
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
            "katachi/Check.kt" to """
                package me.tbsten.katachi.check

                class FileConstraintCheck

                @me.tbsten.katachi.ExperimentalKatachiApi
                fun me.tbsten.katachi.dsl.Architecture.assert(check: FileConstraintCheck) {}
            """.trimIndent(),
            "katachi/Konsist.kt" to """
                package me.tbsten.katachi.konsist

                fun String.konsist(block: Any.() -> Unit) {}
            """.trimIndent(),
            "katachi/Test.kt" to """
                package org.junit.jupiter.api

                annotation class Test
            """.trimIndent(),
            "katachi/KtFileContent.kt" to """
                package me.tbsten.katachi.konsist.declarations

                interface KtFileContentScope {
                    val fileName: String
                    val `class`: Any
                    fun `package`(pattern: String)
                    fun required(vararg line: Any, block: () -> Unit = {})
                }

                // The real one takes a context(FileConstraintScope), which the light project cannot declare without -Xcontext-parameters.
                @me.tbsten.katachi.ExperimentalKatachiApi
                fun ktFileContent(block: KtFileContentScope.() -> Unit) {}
            """.trimIndent(),
        )
    }
}
