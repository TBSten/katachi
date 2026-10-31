package me.tbsten.katachi.intellij.ide.authoring.gutter

import me.tbsten.katachi.intellij.ide.gutter.GutterSources
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.module

/**
 * F-11 of ide-authoring over the real thing: the `ServiceRole.kt` of sample/jvm (copied under `contract/gutter/sources/jvm`) and the
 * `definition.json` katachi wrote for it (`contract/definition/real-jvm-definition.json`). The shapes a sample really has are the
 * ones a hand-written source misses: a role as the body of a function, a file entry on the right of a `/` chain, a `${capture()}`
 * in its name, and `.template {` on the line below it.
 */
internal class DefinitionGutterRealShapeTest : DefinitionGutterTestBase() {
    private fun realSource(): String {
        val resource = "/contract/gutter/sources/jvm/architecture-test/src/test/kotlin/com/example/domain/ServiceRole.kt"
        return GutterSources::class.java.getResource(resource)?.readText() ?: throw IllegalStateException("No test resource $resource")
    }

    private fun readRealDefinition() {
        val definition = DefinitionFixtures.parsed("real-jvm-definition").definition.copy(projectRoot = root)
        snapshot = definitionSnapshot(definition, module(":arch"))
        definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))
    }

    private fun openedRealMarks(): List<DrawnDefinitionMark> {
        openDefinition(realSource())
        readRealDefinition()
        return definitionMarks()
    }

    fun `test sample の本物の ServiceRole kt では 役割の行と 補間つきの file entry に続く template の行に ◆ が出る`() {
        val marks = openedRealMarks()

        assertEquals(listOf(19, 53), marks.map { it.line })
        assertEquals(listOf("\"", "template"), marks.map { it.leaf })
        assertEquals("katachi: domain.Service — 2 files", marks[0].tooltip)
        assertEquals("katachi: generate from domain.Service", marks[1].tooltip)
    }

    fun `test 本物の template の ◆ は その template から生成する行と ツールウィンドウで見る行を持つ`() {
        val menu = openedRealMarks().last().menu

        assertEquals(listOf("+ Generate from this template…", "⊞ Show in the tool window"), menu.shown())
    }
}
