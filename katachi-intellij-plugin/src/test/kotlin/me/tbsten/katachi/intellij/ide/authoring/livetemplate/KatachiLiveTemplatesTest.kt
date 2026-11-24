package me.tbsten.katachi.intellij.ide.authoring.livetemplate

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.authoring.AuthoringPorts
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.CaptureRef
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.testing.DEFINITION_ROOT
import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
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


    /**
     * Opens `ServiceRole.kt` with the role `domain.Service` declared at its line 3 (where the caret is) over a definition that says
     * the role holds [files] (the path from the project root and the text), and puts the files where the live template can read them.
     */
    private fun openRoleHolding(pattern: String, captures: List<CaptureRef>, files: Map<String, String>) {
        val service = FakeDefinitionModelService()
        val onDisk = files.mapKeys { (path, _) -> DEFINITION_ROOT.resolve(path) }.mapValues { (path, text) ->
            myFixture.addFileToProject(path.toString().removePrefix("/w/repo/"), text).virtualFile
        }
        project.replaceService(AuthoringPorts::class.java, AuthoringPorts { service }, testRootDisposable)
        project.replaceService(LiveTemplatePorts::class.java, LiveTemplatePorts { onDisk[it] }, testRootDisposable)
        val role = roleModel("domain.Service", layoutEntry(pattern, captures = captures, matchedFiles = files.keys.toList())).declaredAt("ServiceRole.kt", 3)
        service.emit(DefinitionState.Ready(listOf(definitionSnapshot(definitionModel(role))), Freshness.Fresh))
        myFixture.configureByText("ServiceRole.kt", "package com.example\n\n<caret>\n")
        service.resolveWith(DeclarationSiteRef("ServiceRole.kt", 3), ResolvedSite.Found(myFixture.file.virtualFile, 3))
    }

    private fun expandHere(abbreviation: String): Expansion {
        myFixture.type(abbreviation)
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)
        return Expansion.of(myFixture.editor.document.text)
    }

    fun `test katachifilecontent は Role が持つファイルから package と import と宣言の行を埋める`() {
        openRoleHolding(
            "repo/*Service.kt",
            emptyList(),
            mapOf(
                "repo/UserService.kt" to "package com.example.service\n\nimport kotlinx.coroutines.flow.Flow\n\nclass UserService(private val repository: Any) {\n    fun find(): Flow<Int>? = null\n}\n",
                "repo/OrderService.kt" to "package com.example.service\n\nimport com.example.model.Order\n\nprivate val limit = 3\n\nclass OrderService\n",
            ),
        )

        val expansion = expandHere("katachifilecontent")

        assertEquals(
            """
            @OptIn(ExperimentalKatachiApi::class)
            private fun RoleScope.serviceFileContent() = ktFileContent {
                `package`("com.example.service")
                `import`("com.example.model.Order")
                `import`("kotlinx.coroutines.flow.Flow")
                required(`class`, fileName) {
                    allow(anythingElse)
                }
                allow(private, `val`, "limit")
            }
            """.trimIndent(),
            expansion.body,
        )
        assertNoErrors()
    }

    fun `test katachitemplate は Role のファイルを capture の値を参照に置き換えて本文にする`() {
        openRoleHolding(
            "repo/*/*Service.kt",
            listOf(CaptureRef("feature", 1), CaptureRef("name", 2)),
            mapOf(
                "repo/user/ProfileService.kt" to "class ProfileService // user",
                "repo/order/OrderListService.kt" to "class OrderListService {\n    // a longer file\n    // is not the shortest\n}",
            ),
        )

        val expansion = expandHere("katachitemplate")

        assertEquals(
            "@OptIn(ExperimentalKatachiApi::class)\n" +
                "private fun TemplateScope.serviceTemplate(): String {\n" +
                "    val feature = captureValue(\"feature\")\n" +
                "    val name = captureValue(\"name\")\n" +
                "    return \"\"\"\n" +
                "    class \${name}Service // \${feature}\n" +
                "    \"\"\".trimIndent() + \"\\n\"\n" +
                "}",
            expansion.body,
        )
        assertNoErrors()
    }

    fun `test Role がファイルを持たなければ 固定の文面のまま展開する`() {
        openRoleHolding("repo/*Service.kt", emptyList(), emptyMap())

        val expansion = expandHere("katachifilecontent")

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
    }

    fun `test 開いたファイルに Role が無ければ 定義があっても固定の文面のまま展開する`() {
        openRoleHolding("repo/*Service.kt", emptyList(), mapOf("repo/UserService.kt" to "package a\n\nclass UserService\n"))
        myFixture.configureByText("OtherRole.kt", "package com.example\n\n<caret>\n")

        val expansion = expandHere("katachifilecontent")

        assertTrue(expansion.body, expansion.body.contains("`package`(\"**\")"))
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
        /**
         * The empty light project of BasePlatformTestCase plus what the expanded code needs to resolve: the JDK the test runs on
         * (String has java.io.Serializable as a supertype) and the Kotlin stdlib that the test task names in `katachi.test.kotlinStdlib`.
         */
        val WITH_KOTLIN_STDLIB: LightProjectDescriptor = object : LightProjectDescriptor() {
            // One instance: the platform registers the SDK it gets here for each test and sets the same one on the module.
            private val jdk: Sdk by lazy { JavaSdk.getInstance().createJdk("katachi test JDK", System.getProperty("java.home"), false) }

            override fun getSdk(): Sdk = jdk

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
                    val `val`: Any
                    val private: Any
                    val anythingElse: Any
                    fun `package`(pattern: String)
                    fun `import`(pattern: String)
                    fun required(vararg line: Any, block: () -> Unit = {})
                    fun allow(vararg line: Any, block: () -> Unit = {})
                }

                // The real one takes a context(FileConstraintScope), which the light project cannot declare without -Xcontext-parameters.
                @me.tbsten.katachi.ExperimentalKatachiApi
                fun ktFileContent(block: KtFileContentScope.() -> Unit) {}
            """.trimIndent(),
        )
    }
}
