package me.tbsten.katachi.intellij.ide.authoring.completion

import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.ide.authoring.AuthoringPorts
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.module

/**
 * F-4 and C-3 of ide-authoring over the real thing: the completion inside a `templateSet { }` and a `.template { }` of `sample/jvm`,
 * answered from the `definition.json` katachi wrote for it (`contract/definition/real-jvm-definition.json`), whose `templates` and
 * `templateSets` are the sample's own (`api.Endpoint`, made of `api.Controller` and `domain.Service`).
 */
internal class DefinitionCompletionRealShapeTest : AnalysisTestBase() {
    private lateinit var service: FakeDefinitionModelService

    override fun setUp() {
        super.setUp()
        service = FakeDefinitionModelService()
        project.replaceService(AuthoringPorts::class.java, AuthoringPorts { service }, testRootDisposable)
        val parsed = DefinitionFixtures.parsed("real-jvm-definition")
        val module = module(":architecture-test")
        val snapshot = DefinitionSnapshot(module, parsed.definition, parsed.templates, parsed.definition.katachiVersion, DEFINITION_INSTANT)
        service.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
    }

    /** Opens `ServiceRole.kt` with [text] in a function (so its first line is line 2) and makes the real `.template` of line 57 resolve to that line. */
    private fun open(text: String) {
        myFixture.configureByText("ServiceRole.kt", AuthoringFixtures.inFunction(text.trimIndent()))
        service.resolveWith(DeclarationSiteRef("ServiceRole.kt", 57), ResolvedSite.Found(myFixture.file.virtualFile, 2))
    }

    private fun offered(): List<String> {
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty().filter { it != "ServiceRole.kt" }
    }

    fun `test 本物の templateSet の template では sample の全テンプレートの指定が出て 組自身は出ない`() {
        open(
            """
            "Endpoint".templateSet {
                template("<caret>")
            }
            """,
        )

        val offered = offered()
        assertTrue(offered.toString(), "api.Controller" in offered && "domain.Service" in offered)
        assertFalse(offered.toString(), "api.Endpoint" in offered)
    }

    fun `test 本物の Role 名の一部でも当たり Enter で完全な指定に置き換わる`() {
        open(
            """
            "Endpoint".templateSet {
                template("Contr<caret>")
            }
            """,
        )

        offered()
        myFixture.checkResult(AuthoringFixtures.inFunction("\"Endpoint\".templateSet {\n    template(\"api.Controller\")\n}"))
    }

    fun `test 本物の組のメンバの arg には そのメンバの capture と パラメータが出る`() {
        open(
            """
            "Endpoint".templateSet {
                template("domain.Service") { arg("<caret>", "x") }
            }
            """,
        )

        assertEquals(listOf("kdoc", "name"), offered().sorted())
    }

    fun `test 本物の ServiceRole の template の中の captureValue には その template の capture が出る`() {
        open(
            """
            "src/main/kotlin/com/example/service/${'$'}{capture("name")}Service.kt".file().template {
                captureValue("<caret>")
            }
            """,
        )

        assertEquals(listOf("name"), offered())
    }
}
