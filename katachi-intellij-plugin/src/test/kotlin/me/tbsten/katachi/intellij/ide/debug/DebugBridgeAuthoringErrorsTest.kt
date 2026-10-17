package me.tbsten.katachi.intellij.ide.debug

import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.ide.authoring.AuthoringPorts
import me.tbsten.katachi.intellij.ide.authoring.errors.DefinitionErrorTestBase
import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent

/**
 * `describeDefinitionErrors` of the debug bridge: the mistakes katachi found and the underlines the IDE drew for them, as the
 * Driver smoke reads them. The banner is the part on screen; a unit test has none, so it is read in the smoke (V4).
 */
internal class DebugBridgeAuthoringErrorsTest : DefinitionErrorTestBase() {
    private val path get() = lightPath(SCREEN_ROLE).toString()

    private fun reader() = AuthoringDebugReader(project, onScreen = { true }, portsOf = { ports() })

    override fun setUp() {
        super.setUp()
        useLocalFiles()
        val service = definitions
        project.replaceService(AuthoringPorts::class.java, AuthoringPorts { service }, testRootDisposable)
    }

    fun `test 誤りがあれば 件数 古さ 誤りの場所と 1 行目 それに その行の下線を 返す`() {
        openDefinition()
        rejected(mistake(4, "the first line\nthe second line"))
        annotated()

        val lines = reader().describeDefinitionErrors(path).lines()

        assertEquals(listOf("definitionErrors=$path", "errors=1", "stale=false"), lines.take(3))
        assertEquals(
            "error=:arch|Processor|me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException|ScreenRole.kt:4|the first line",
            lines[3],
        )
        assertEquals("highlights=1", lines[4])
        assertTrue(lines[5], lines[5].startsWith("highlight=ERROR|4:1-4:"))
        assertTrue(lines[5], lines[5].contains("katachi: the first line\\nthe second line"))
        assertEquals("banners=0", lines.last())
    }

    fun `test 場所の無い誤りは site が none で 下線は無い`() {
        openDefinition()
        rejected(mistake(null))
        annotated()

        val lines = reader().describeDefinitionErrors(path).lines()

        assertTrue(lines.toString(), lines.any { it.startsWith("error=") && it.contains("|none|") })
        assertTrue(lines.toString(), "highlights=0" in lines)
    }

    fun `test 変更の後は stale=true で 直って読めたら errors=0 と 下線なし`() {
        openDefinition()
        rejected(mistake(4))
        definitions.emitErrors(DefinitionErrorsEvent.DefinitionChanged)
        assertEquals("stale=true", reader().describeDefinitionErrors(path).lines()[2])

        readWith()
        annotated()

        val lines = reader().describeDefinitionErrors(path).lines()
        assertEquals(listOf("errors=0", "stale=false", "highlights=0", "banners=0"), lines.drop(1))
    }
}
