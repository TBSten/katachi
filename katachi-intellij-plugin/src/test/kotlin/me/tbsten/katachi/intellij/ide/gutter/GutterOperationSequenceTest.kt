package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.declarationModel
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel
import kotlin.random.Random

/**
 * The ◆ through sequences of what a user does with an open file: type above the declarations, add and rename
 * declarations, save, refresh the definition, have it go stale. After each step the daemon draws again and the ◆
 * must agree with what the user did, read off the editor (an oracle apart from the binding's own rules).
 *
 * The test world keeps the file's write time as the platform does: a save of edits writes the file after the definition was
 * made, so it stays `(stale)` until a refresh makes a newer definition. One of the two tests leaves the daemon's restart
 * to the real [HighlightingRefresher] (state changes reach the open file through its roots and path), the other restarts it
 * itself. `-Dkatachi.gutter.sequences=N` runs N sequences.
 */
internal class GutterOperationSequenceTest : GutterTestBase() {
    private val service = roleModel("domain.Service", layoutEntry(GutterFixtures.SERVICE_PATH))
    private val fileName = "HealthService.kt"
    private val classPattern = Regex("""^class (\w+)""", RegexOption.MULTILINE)

    /** What the test knows apart from the gutter: which classes the last refresh explained, whether the definition is stale, and whether the file was saved since. */
    private class World(var explained: Set<String>, var stale: Boolean, var savedSinceRefresh: Boolean = false, var counter: Int = 0)

    private fun unsaved() = FileDocumentManager.getInstance().isFileModified(myFixture.file.virtualFile)

    /** Saves the way the user does; edits written after the definition was made keep it stale. */
    private fun save(world: World) {
        val edited = unsaved()
        saveAll()
        if (edited) world.savedSinceRefresh = true
    }

    private val text get() = myFixture.editor.document.text

    private fun classesInEditor(): List<String> = classPattern.findAll(text).map { it.groupValues[1] }.toList()

    private fun lineOf(name: String): Int = Regex("""^class $name\b""").let { line -> text.lineSequence().indexOfFirst { line.containsMatchIn(it) } + 1 }

    /** What a refresh of the definition does: katachi explains the saved file again; the fake goes Loading, then Ready. */
    private fun refresh(world: World) {
        val declarations = classesInEditor().map { name ->
            declarationModel(SourceDeclarationKind.Class, name, lineOf(name), rules = listOf(RuleRef("class $name", DeclarationSiteRef("ServiceRole.kt", 80))))
        }
        definitions.emit(DefinitionState.Loading(definitions.state.value as? DefinitionState.Ready))
        definitions.explain(lightPath(fileName), listOf(roleFileDeclarations(service, fileDeclarations(GutterFixtures.SERVICE_PATH, *declarations.toTypedArray()))))
        definitions.emit(DefinitionState.Ready(listOf(refreshedSnapshot()), Freshness.Fresh))
        world.explained = declarations.map { it.name }.toSet()
        world.stale = false
        world.savedSinceRefresh = false
    }

    private fun edit(change: (String) -> String) {
        val before = text
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText(change(before)) }
    }

    private fun step(random: Random, world: World): String = when (random.nextInt(6)) {
        0 -> "type blank lines at the top".also { edit { "\n\n$it" } }
        1 -> "add class Added${world.counter}".also { val name = "Added${world.counter++}"; edit { "$it\nclass $name\n" } }
        2 -> {
            val target = classesInEditor().random(random)
            val renamed = "${target}Renamed${world.counter++}"
            "rename $target to $renamed".also { edit { it.replace(Regex("""^class $target\b""", RegexOption.MULTILINE), "class $renamed") } }
        }
        3 -> "save".also { save(world) }
        4 -> "save and refresh the definition".also { save(world); refresh(world) }
        else -> "the definition goes stale".also {
            definitions.emit(DefinitionState.Ready(listOf(snapshot), Freshness.Stale(DEFINITION_INSTANT, StaleReason.DefinitionChanged)))
            world.stale = true
        }
    }

    private fun check(world: World, where: String, redraw: () -> List<DrawnMark>) {
        val marks = redraw()
        val classes = classesInEditor()
        assertEquals("$where: one ◆ per declaration, on its name", classes, marks.map { it.leaf })
        assertEquals("$where: on the declaration's line", classes.map(::lineOf), marks.map { it.line })
        val unsaved = unsaved()
        val stale = world.stale || unsaved || world.savedSinceRefresh
        for ((name, mark) in classes.zip(marks)) {
            val tooltip = mark.tooltip.orEmpty()
            if (name in world.explained) {
                assertTrue("$where: $name was explained: $tooltip", tooltip.contains("allowed by class $name (ServiceRole.kt:80)"))
            } else {
                assertTrue("$where: $name was not explained: $tooltip", tooltip.contains("not computed yet"))
            }
            assertEquals("$where: $name stale=${world.stale} unsaved=$unsaved saved=${world.savedSinceRefresh}: $tooltip", stale, tooltip.endsWith("(stale)"))
        }
    }

    private fun runSequences(sequences: Int, localFiles: Boolean, redraw: () -> List<DrawnMark>) {
        if (localFiles) useLocalFiles()
        repeat(sequences) { index ->
            val sequenceSeed = 20261005L + index
            val random = Random(sequenceSeed)
            definitions.answer(lightPath(fileName), fileRoles(GutterFixtures.SERVICE_PATH, service))
            open(fileName, "package com.example.service\n\nclass HealthService\n")
            saveAll()
            val world = World(explained = emptySet(), stale = false)
            refresh(world)
            val log = mutableListOf("open")
            check(world, "seed=$sequenceSeed open", redraw)
            repeat(8) {
                log += step(random, world)
                check(world, "seed=$sequenceSeed ${log.joinToString(" → ")}", redraw)
            }
        }
    }

    fun `test 開く 編集 保存 再読み込み 古くなる の列を流しても ◆ は宣言に付き 説明と古さを正しく言う`() {
        runSequences(System.getProperty("katachi.gutter.sequences")?.toInt() ?: 12, localFiles = false, ::redrawn)
    }

    fun `test 同じ列を 描き直しを本物の HighlightingRefresher に任せて流しても ◆ は状態の変化に追いつく`() {
        // The test never restarts the daemon: only the refresher's own restart (open files under the definition's root) draws a new state.
        runSequences(System.getProperty("katachi.gutter.refresherSequences")?.toInt() ?: 4, localFiles = true, ::redrawnByRefresher)
    }
}
