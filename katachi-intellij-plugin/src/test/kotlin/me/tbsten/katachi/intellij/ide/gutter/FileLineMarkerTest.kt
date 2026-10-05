package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.lang.Language
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.DumbModeTestUtils
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel

/**
 * V2 of the gutter's P4: the ◆ of a file that is not Kotlin (design 2.1, 2.4): one, on the first thing in the file, from the
 * provider registered for every language. The real daemon draws `.md`, `.txt` and `.properties`; `.xml` and `.toml` cannot be
 * highlighted in the test environment (a bundled plugin's class fails to load, F-5 of the background notes), so the provider is
 * asked for them directly, as it is for `.kts`.
 */
internal class FileLineMarkerTest : GutterTestBase() {
    private lateinit var fileProvider: KatachiFileLineMarkerProvider

    private val adr = roleModel("docs.Adr", layoutEntry("docs/adr/0001-record.md"))
    private val index = roleModel("docs.Index", layoutEntry("docs/adr/0001-record.md"))

    override fun setUp() {
        super.setUp()
        fileProvider = KatachiFileLineMarkerProvider { ports() }
        LineMarkerProviders.getInstance().addExplicitExtension(Language.ANY, fileProvider, testRootDisposable)
    }

    override fun tearDown() {
        try {
            LineMarkerSettings.getSettings().setEnabled(provider, true)
        } finally {
            super.tearDown()
        }
    }

    /** The definition holds [fileName] by the roles named (`docs.Adr` when none is). */
    private fun readyFile(fileName: String, vararg roleNames: String) {
        val roles = roleNames.ifEmpty { arrayOf("docs.Adr") }.map { if (it == "docs.Adr") adr else index }
        ready(fileName, fileRoles("docs/$fileName", *roles.toTypedArray()))
    }

    fun `test Markdown の先頭に Role の ◆ が 1 つ PSI の根が 2 つあっても 2 つにならない`() {
        readyFile("0001-record.md")
        val marks = drawn("0001-record.md", "# 0001. Record architecture decisions\n\nWe need to record.\n")
        assertEquals(listOf(1 to "katachi: docs.Adr"), marks.map { it.line to it.tooltip })
        assertTrue(marks.single().hasMenu)
    }

    fun `test 空行や空白から始まるテキストでは 最初の空白でない要素に付く`() {
        readyFile("notes.txt")
        val marks = drawn("notes.txt", "\n\n   first words\nsecond line\n")
        // A plain text file is one leaf from its first line; the ◆ is keyed on where the words start.
        assertEquals(listOf(3), marks.map { it.line })
        assertEquals("katachi: docs.Adr", marks.single().tooltip)
    }

    fun `test properties にも付き Role が 2 つなら名前を並べる`() {
        readyFile("app.properties", "docs.Adr", "docs.Index")
        val marks = drawn("app.properties", "# comment\nname=katachi\n")
        assertEquals(listOf(1 to "katachi: docs.Adr, docs.Index"), marks.map { it.line to it.tooltip })
    }

    fun `test XML と TOML は provider に直に聞いても ファイルの先頭に 1 つ`() {
        for ((name, text, firstLeaf) in listOf(
            Triple("pom.xml", "\n<project>\n  <name>x</name>\n</project>\n", 2),
            Triple("libs.toml", "# versions\n[versions]\nkotlin = \"2.0\"\n", 1),
        )) {
            readyFile(name)
            val file = myFixture.configureByText(name, text)
            val marks = collectedDirectly(file, fileProvider)
            assertEquals("$name: $marks", listOf(firstLeaf), marks.map { it.line })
            assertEquals("katachi: docs.Adr", marks.single().tooltip)
        }
    }

    fun `test Kotlin のファイルには この provider は何も足さず 二重にならない`() {
        ready("HealthRepository.kt", fileRoles(GutterFixtures.REPOSITORY_PATH, roleModel("data.Repository", layoutEntry(GutterFixtures.REPOSITORY_PATH))))
        val file = myFixture.configureByText("HealthRepository.kt", GutterFixtures.HEALTH_REPOSITORY_KT)
        assertEquals("the file provider leaves .kt to the Kotlin one", emptyList<DrawnMark>(), collectedDirectly(file, fileProvider))
        assertEquals("the Kotlin provider draws it", 1, collectedDirectly(file).size)
        // Both are registered for the daemon, which asks the one for every language about Kotlin too.
        assertEquals(1, redrawn().size)
    }

    fun `test kts にも この provider は何も足さず Kotlin の provider の ◆ だけが 1 つ`() {
        ready("build.gradle.kts", fileRoles("build.gradle.kts", adr))
        val file = myFixture.configureByText("build.gradle.kts", "\nplugins {\n    kotlin(\"jvm\")\n}\n")
        assertEquals(emptyList<DrawnMark>(), collectedDirectly(file, fileProvider))
        assertEquals(listOf(2), collectedDirectly(file).map { it.line })
    }

    fun `test 当たらないファイル 答えの無いファイル 定義モジュールの無いプロジェクト 中身の無いファイルには出さない`() {
        ready("a.md", fileRoles("docs/a.md", outcome = MatchOutcome.Unexpected))
        assertEquals(emptyList<DrawnMark>(), drawn("a.md", "# a\n"))

        ready("a.md", null)
        assertEquals(emptyList<DrawnMark>(), redrawn())

        readyFile("a.md")
        hasDefinitionModule = false
        assertEquals(emptyList<DrawnMark>(), redrawn())

        hasDefinitionModule = true
        assertEquals("a file with nothing in it has no first thing to put a ◆ on", emptyList<DrawnMark>(), drawn("a.md", "\n\n  \n"))
        // A plain text file is one leaf of its whole text, white space or not.
        readyFile("blank.txt")
        assertEquals("a text file of white space only has nothing to put a ◆ on", emptyList<DrawnMark>(), drawn("blank.txt", "\n\n  \n"))
    }

    fun `test 古さの印は Role だけの ◆ には付かず 読み直しの失敗だけは 2 行目で言う`() {
        readyFile("a.md")
        modifiedAt = DEFINITION_INSTANT.plusSeconds(60)
        assertEquals("a role does not depend on the file's content: no (stale)", "katachi: docs.Adr", drawn("a.md", "# a\n").single().tooltip)

        val failure = StaleReason.ReloadFailed(LoadFailure.Gradle(GradleFailure.ArchitectureNotSet(emptyList())))
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Stale(DEFINITION_INSTANT, failure)))
        assertEquals(
            "katachi: docs.Adr<br>The definition could not be read again: katachi { architecture } is not set",
            redrawn().single().tooltip,
        )
    }

    fun `test メニューの見出しはファイル名と Role で 先頭は Role の行`() {
        readyFile("a.md")
        drawn("a.md", "# a\n")
        val menu = menus().single()
        assertEquals("a.md — docs.Adr", menu.title())
        assertTrue(menu.shown().toString(), menu.shown().first().startsWith("(disabled) ◇ Role docs.Adr"))
    }

    fun `test 状態の列 まだ読んでいない 読めた 古い定義の katachi 読めた で 出る 消える 出る`() {
        readyFile("a.md")
        open("a.md", "# a\n")
        definitions.emit(DefinitionState.NotLoaded)
        assertEquals("nothing is read yet", emptyList<DrawnMark>(), redrawn())
        assertTrue("the ◆ asked the definition to load", definitions.ensureLoadedCalls > 0)

        val answer = DefinitionState.Ready(listOf(snapshot), Freshness.Fresh)
        definitions.emit(answer)
        assertEquals(1, redrawn().size)

        typeAtStart("added\n")
        assertEquals("an edit moves the first thing: the ◆ goes with it", listOf(1), redrawn().map { it.line })

        definitions.emit(DefinitionState.Unavailable(DefinitionUnavailableReason.KatachiTooOld, answer))
        assertEquals(emptyList<DrawnMark>(), redrawn())

        definitions.emit(answer)
        assertEquals(1, redrawn().size)
    }

    fun `test Gutter Icons の katachi を切ると Kotlin と Kotlin 以外の両方から消える`() {
        readyFile("a.md")
        assertEquals(1, drawn("a.md", "# a\n").size)
        ready("HealthRepository.kt", fileRoles(GutterFixtures.REPOSITORY_PATH, roleModel("data.Repository", layoutEntry(GutterFixtures.REPOSITORY_PATH))))
        assertEquals(1, drawn("HealthRepository.kt", GutterFixtures.HEALTH_REPOSITORY_KT).size)

        // One switch for both (the same id), as the settings page lists one row.
        assertEquals(provider.id, fileProvider.id)
        LineMarkerSettings.getSettings().setEnabled(fileProvider, false)
        assertEquals("the Kotlin file lost its ◆ with the switch the other provider owns", emptyList<DrawnMark>(), redrawn())
        readyFile("a.md")
        assertEquals(emptyList<DrawnMark>(), drawn("a.md", "# a\n"))

        LineMarkerSettings.getSettings().setEnabled(provider, true)
        assertEquals(1, redrawn().size)
    }

    fun `test dumb mode でも出る`() {
        readyFile("a.md")
        val file = myFixture.configureByText("a.md", "# a\n")
        assertTrue(DumbService.isDumbAware(fileProvider))
        val smart = collectedDirectly(file, fileProvider)
        val dumb = DumbModeTestUtils.computeInDumbModeSynchronously(project) { collectedDirectly(file, fileProvider) }
        assertEquals(1, smart.size)
        assertEquals(smart, dumb)
    }
}
