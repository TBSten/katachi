package me.tbsten.katachi.intellij.ide.newmenu

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.ide.EntryServiceTestBase
import me.tbsten.katachi.intellij.ide.IdeEffectsImpl
import me.tbsten.katachi.intellij.ide.KatachiPorts
import me.tbsten.katachi.intellij.ide.KatachiProjectService
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.testing.FakeEntryEffects
import me.tbsten.katachi.intellij.testing.indexOf
import me.tbsten.katachi.intellij.testing.patternTemplate
import me.tbsten.katachi.intellij.testing.snapshotOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Platform tests of New › katachi over a real project service whose index is the one given (the
 * fake Gradle only "loads" a list, which makes the index Ready), a real VFS with `PsiDirectory`s and
 * `TestActionEvent`s carrying an `IDE_VIEW`.
 */
internal abstract class NewMenuTestBase : EntryServiceTestBase() {
    protected val entryEffects = FakeEntryEffects()
    protected val group = KatachiNewGroup()

    protected val moduleA: KatachiModule get() = KatachiModule(":arch-a", moduleDirOf(":arch-a"), root, "project")
    protected val moduleB: KatachiModule get() = KatachiModule(":arch-b", moduleDirOf(":arch-b"), root, "project")

    protected val screen: TemplateModel = patternTemplate("feature.Screen", "feature/src/main/com/example/feature/\${feature}/ui/\${screen}Screen.kt")
    protected val viewModel: TemplateModel = patternTemplate("feature.ViewModel", "feature/src/main/com/example/feature/\${feature}/\${screen}ViewModel.kt")
    protected val repository: TemplateModel = patternTemplate("domain.Repository", "domain/src/main/com/example/\${name}Repository.kt")

    /** A service whose index is [index] once the list is loaded, in place of the one built from the loaded JSON. */
    protected fun installMenuService(index: () -> TemplatePlacementIndex) {
        val ports = KatachiPorts(
            syncedProject = { synced },
            runner = gradle,
            fileSystem = NioProjectFileSystem,
            effects = IdeEffectsImpl(project),
            entryEffects = entryEffects,
            importInProgress = { false },
            refreshNotifications = {},
            buildIndex = { index() },
        )
        service = KatachiProjectService(project, scope, ports)
        project.replaceService(KatachiProjectService::class.java, service, testRootDisposable)
    }

    protected fun installReadyMenu(vararg templates: TemplateModel) {
        installMenuService { indexOf(snapshotOf(moduleA, templates.toList())) }
        loadAndSettle()
    }

    protected fun loadAndSettle() {
        service.ensureLoaded()
        settle()
        // The index is published from a background flow: wait until it has left "not loaded" / "loading".
        waitForIndex("decided") { it !is PlacementIndexState.NotLoaded && it !is PlacementIndexState.Loading }
    }

    protected fun directory(relative: String): PsiDirectory {
        val path = root.resolve(relative)
        Files.createDirectories(path)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("not in the VFS: $path")
        return PsiManager.getInstance(project).findDirectory(file) ?: throw AssertionError("no PsiDirectory for $path")
    }

    protected fun pathOf(directory: PsiDirectory): Path = directory.virtualFile.toNioPath()

    protected fun eventOn(vararg directories: PsiDirectory, action: AnAction = group): AnActionEvent = eventWith(
        object : IdeView {
            override fun getDirectories(): Array<PsiDirectory> = arrayOf(*directories)
            override fun getOrChooseDirectory(): PsiDirectory? = throw AssertionError("New › katachi must not ask the IdeView to choose a directory")
        },
        action,
    )

    protected fun eventWith(view: IdeView?, action: AnAction = group): AnActionEvent {
        val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).also { if (view != null) it.add(LangDataKeys.IDE_VIEW, view) }.build()
        return TestActionEvent.createTestEvent(action, context)
    }

    /** `update` on [event], then whether the group shows. */
    protected fun visibleAfterUpdate(event: AnActionEvent): Boolean {
        group.update(event)
        return event.presentation.isEnabledAndVisible
    }

    /** The menu as indented lines: submenus by name, items by label. */
    protected fun outline(actions: Array<AnAction>, depth: Int = 0): List<String> = actions.flatMap { action ->
        val line = "  ".repeat(depth) + action.templatePresentation.text
        if (action is ActionGroup) listOf(line) + outline(action.getChildren(null), depth + 1) else listOf(line)
    }

    protected fun menuOn(vararg directories: PsiDirectory): List<String> {
        val event = eventOn(*directories)
        group.update(event)
        return if (event.presentation.isEnabledAndVisible) outline(group.getChildren(event)) else emptyList()
    }
}
