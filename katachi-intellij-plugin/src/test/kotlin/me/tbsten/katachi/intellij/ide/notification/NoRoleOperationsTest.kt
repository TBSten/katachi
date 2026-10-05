package me.tbsten.katachi.intellij.ide.notification

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.presentation.rules.NoRoleDecision
import me.tbsten.katachi.intellij.presentation.rules.RuleSettings
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import me.tbsten.katachi.intellij.presentation.rules.decideNoRoleNotification
import me.tbsten.katachi.intellij.testing.ARCHITECTURE_SITE
import me.tbsten.katachi.intellij.testing.DefinitionStateScripts
import me.tbsten.katachi.intellij.testing.MISNAMED
import me.tbsten.katachi.intellij.testing.STRAY
import me.tbsten.katachi.intellij.testing.USE_CASE_DIRECTORY
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.noRoleInput
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.rolesFor
import kotlin.random.Random

/**
 * A property of the whole notification, stateful part included: whatever the user, the VCS, the settings, the files and the
 * definition do, in whatever order, what the editor shows once things have settled is what the pure decision says of the
 * inputs *as they are then*. The operations are the ones a session has: the definition's state moves (loading, stale, failed,
 * recovered), the file is renamed or moved, the × is pressed, the VCS status changes, the settings switch is flipped. The
 * decision is the oracle for what to show; this test is about the refresher, the provider and the platform between a change
 * and the screen (the redraws that are skipped, delayed or never asked for). A wrong redraw that arrives late is looked for too:
 * every check is made again after the delayed redraws had time to happen.
 */
internal class NoRoleOperationsTest : NoRoleTestBase() {
    private class World(
        var state: DefinitionState,
        var relative: String,
        var enabled: Boolean = true,
        var vcs: VcsTracking = VcsTracking.Tracked,
        val dismissed: MutableSet<String> = mutableSetOf(),
    )

    private val held = "$USE_CASE_DIRECTORY/GetPagingUseCase.kt"
    private val places = listOf(STRAY, MISNAMED, held)

    private val settings get() = KatachiSettings.getInstance(project)

    override fun tearDown() {
        try {
            settings.notifyOnNoRole = true
        } finally {
            super.tearDown()
        }
    }

    fun `test 乱数の操作の列の後でも通知は入力が今そうであるときの判定と一致する 種1`() = walk(20261005)

    fun `test 乱数の操作の列の後でも通知は入力が今そうであるときの判定と一致する 種2`() = walk(7)

    fun `test 乱数の操作の列の後でも通知は入力が今そうであるときの判定と一致する 種3`() = walk(424242)

    fun `test 乱数の操作の列の後でも通知は入力が今そうであるときの判定と一致する 種4`() = walk(1)

    private fun walk(seed: Long) {
        val random = Random(seed)
        val states = DefinitionStateScripts.random(random, 40, listOf(snapshot, other())).drop(1).iterator()
        places.forEach(::answer)
        val world = World(DefinitionState.NotLoaded, STRAY)
        vcsOf = { world.vcs }
        val file = write(STRAY, "")
        val editor = open(file)
        check("open", world, editor)

        repeat(24) { step ->
            val what = when (random.nextInt(6)) {
                0, 1 -> if (states.hasNext()) emit(world, states.next()) else "no more states"
                2 -> relocate(world, file, places[random.nextInt(places.size)])
                3 -> dismiss(world, editor)
                4 -> changeVcs(world, VcsTracking.entries[random.nextInt(VcsTracking.entries.size)])
                else -> toggle(world)
            }
            check("seed $seed step $step: $what", world, editor)
        }
    }

    private fun emit(world: World, state: DefinitionState): String {
        world.state = state
        fake.emit(state)
        return "definition -> $state"
    }

    private fun relocate(world: World, file: VirtualFile, relative: String): String {
        val target = path(relative)
        WriteAction.runAndWait<Throwable> {
            val directory = VfsUtil.createDirectories(target.parent.toString())
            if (file.parent != directory) file.move(this, directory)
            if (file.name != target.fileName.toString()) file.rename(this, target.fileName.toString())
        }
        world.relative = relative
        return "file -> $relative"
    }

    private fun dismiss(world: World, editor: FileEditor): String {
        val panel = peekNoRolePanel(editor) ?: return "x (nothing to close)"
        panel.button("notification.rules.dismiss.tooltip").doClick()
        world.dismissed += world.relative
        return "x on ${world.relative}"
    }

    private fun changeVcs(world: World, vcs: VcsTracking): String {
        world.vcs = vcs
        FileStatusManager.getInstance(project).fileStatusesChanged()
        return "vcs -> $vcs"
    }

    private fun toggle(world: World): String {
        world.enabled = !world.enabled
        settings.notifyOnNoRole = world.enabled
        // What Apply on the settings page does.
        EditorNotifications.getInstance(project).updateAllNotifications()
        return "notifyOnNoRole -> ${world.enabled}"
    }

    /** What the pure decision says of the inputs [world] has now, as the text the panel must have; `null`: no panel. */
    private fun expected(world: World): String? {
        val freshness = world.state.answer?.freshness ?: Freshness.Fresh
        val memory = world.dismissed.fold(EditorNotificationMemory.EMPTY) { memory, relative -> memory.dismissNoRole(path(relative)) }
        val decision = decideNoRoleNotification(
            noRoleInput(
                relative = world.relative,
                snapshot = snapshot,
                file = path(world.relative),
                state = world.state,
                roles = rolesFor(world.relative, snapshot, freshness),
                vcs = world.vcs,
                settings = RuleSettings(notifyOnNoRole = world.enabled),
                memory = memory,
            ),
        )
        return (decision as? NoRoleDecision.NoRole)?.let { expectedText(world.relative, untracked = it.untracked, stale = it.stale) }
    }

    /** The panel says [expected] once the redraws settle, and still says it after the delayed ones had time to come. */
    private fun check(what: String, world: World, editor: FileEditor) {
        awaitNoRoleText(what, editor, expected(world))
        pump(LATE_REDRAW_MILLIS)
        assertEquals("$what (late)", expected(world), peekNoRolePanel(editor)?.text)
    }

    /** [snapshot] with one more role: another definition to read, the same architecture (`gitTracked()`, declared in the same place). */
    private fun other() = definitionSnapshot(
        definitionModel(
            roleModel("domain.UseCase", layoutEntry("$USE_CASE_DIRECTORY/*UseCase.kt")),
            roleModel("Extra", layoutEntry("extra/*.kt")),
            files = ArchitectureModel.FILES_GIT_TRACKED,
        ).let { it.copy(architecture = it.architecture.copy(declaredAt = ARCHITECTURE_SITE)) },
    )

    private companion object {
        /** Longer than the refresher's delay for a definition change (200 ms). */
        const val LATE_REDRAW_MILLIS = 350L
    }
}
