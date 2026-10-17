package me.tbsten.katachi.intellij.ide.authoring.completion

import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.ide.authoring.AuthoringPorts
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness

/**
 * The popup opens by itself: typing the quote that opens the first argument of `captureValue(`, `template(` or `arg(`
 * shows the names, where Kotlin would not open a popup inside a string. The editor and the typing are the real ones
 * (the platform's auto-popup tester), the definition is a fake.
 */
internal class DefinitionAutoPopupTest : CompletionAutoPopupTestCase() {
    override fun runInDispatchThread(): Boolean = false

    private fun open(text: String) = runInEdtAndWait {
        val service = FakeDefinitionModelService()
        project.replaceService(AuthoringPorts::class.java, AuthoringPorts { service }, testRootDisposable)
        service.emit(DefinitionState.Ready(listOf(AuthoringFixtures.snapshotWithSiteAt(2)), Freshness.Fresh))
        myFixture.configureByText("ScreenRole.kt", AuthoringFixtures.inFunction(text.trimIndent()))
        service.resolveWith(DeclarationSiteRef("ScreenRole.kt", 2), ResolvedSite.Found(myFixture.file.virtualFile, 2))
    }

    private fun offered(): List<String> = myFixture.lookupElementStrings.orEmpty().filter { it != "ScreenRole.kt" }

    fun `test captureValue の引用符を打つと自動ポップアップが開き 続けて打つと絞られる`() {
        open(
            """
            "*Screen.kt".file().template {
                captureValue(<caret>)
            }
            """,
        )

        type("\"")
        assertEquals(listOf("feature", "name", "nameOf"), offered())

        type("na")
        assertEquals(listOf("name", "nameOf"), offered())
    }

    fun `test 関係の無い関数の引用符では自動ポップアップを開かない`() {
        open(
            """
            "*Screen.kt".file().template {
                stringParameter(<caret>)
            }
            """,
        )

        type("\"")

        assertNull(myFixture.lookup)
    }

    fun `test 引用符の中で文字を打ち始めても自動ポップアップが開く`() {
        open(
            """
            "*Screen.kt".file().template {
                captureValue("<caret>")
            }
            """,
        )

        type("n")

        assertEquals(listOf("name", "nameOf"), offered())
    }

    fun `test 関係の無い関数の引用符の中では 文字を打ち始めても自動ポップアップは開かない`() {
        open(
            """
            "*Screen.kt".file().template {
                stringParameter("<caret>")
            }
            """,
        )

        type("n")

        assertNull(myFixture.lookup)
    }

    fun `test templateSet の template の引用符でも自動ポップアップが開く`() {
        open(
            """
            "NewFeature".templateSet {
                template(<caret>)
            }
            """,
        )

        type("\"")

        assertEquals(listOf("data.Repository.a", "data.Repository.b", "feature.FeatureViewModel", "ui.Screen"), offered().sorted())
    }
}
