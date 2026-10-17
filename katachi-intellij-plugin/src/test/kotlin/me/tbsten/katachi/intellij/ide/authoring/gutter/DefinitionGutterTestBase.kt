package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviders
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.ide.gutter.GutterTestBase
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.withTemplate
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.nio.file.Files
import java.nio.file.Path

/** A ◆ of the definition file as the daemon drew it, with the menu it opens. */
internal data class DrawnDefinitionMark(val line: Int, val leaf: String, val tooltip: String, val menu: DefinitionGutterMenu)

/**
 * The world of the platform tests of the definition file's ◆: a definition file `ServiceRole.kt` (a role at line 2, its file entry
 * at line 3, a `.template { }` at line 4) in the editor, the real PSI and daemon, and the foundation's fake service, whose answer
 * is the definition [serviceRole]. The files the role holds are real files under [root], so choosing one opens it.
 */
internal abstract class DefinitionGutterTestBase : GutterTestBase() {
    lateinit var definitionProvider: KatachiDefinitionLineMarkerProvider
    lateinit var root: Path

    val arch = module(":arch").id

    override fun setUp() {
        super.setUp()
        definitionProvider = KatachiDefinitionLineMarkerProvider { ports() }
        LineMarkerProviders.getInstance().addExplicitExtension(KotlinLanguage.INSTANCE, definitionProvider, testRootDisposable)
        root = Files.createTempDirectory("katachi-definition-gutter").toRealPath()
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
    }

    override fun tearDown() {
        try {
            FileUtil.delete(root.toFile())
        } finally {
            super.tearDown()
        }
    }

    /** The role the definition declares: three files, a `.template { }`, written at lines 2 (role), 3 (entry) and 4 (template). */
    fun serviceRole(files: List<String> = SERVICE_FILES, templated: Boolean = true): RoleModel = roleModel(
        "domain.Service",
        layoutEntry("core/service/*Service.kt", matchedFiles = files).declaredAt(ROLE_FILE, 3).let {
            if (templated) it.withTemplate("domain.Service.service", ROLE_FILE, 4, title = "Service") else it
        },
    ).declaredAt(ROLE_FILE, 2)

    /** The role of [serviceRole] with a second `.template { }` on another file entry, so that its Generate row is a sub menu. */
    fun twoTemplateRole(): RoleModel = roleModel(
        "domain.Service",
        layoutEntry("core/service/*Service.kt", matchedFiles = SERVICE_FILES, index = 0).declaredAt(ROLE_FILE, 3).withTemplate("domain.Service.service", ROLE_FILE, 4, title = "Service"),
        layoutEntry("core/fake/*Fake.kt", index = 1).declaredAt(ROLE_FILE, 3).withTemplate("domain.Service.fake", ROLE_FILE, 4, title = "Fake service"),
    ).declaredAt(ROLE_FILE, 2)

    /** Puts [files] on the disk under [root]. */
    fun writeFiles(files: List<String> = SERVICE_FILES) = files.forEach { relative ->
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, "class ${path.fileName.toString().removeSuffix(".kt")}\n")
    }

    /** Opens `ServiceRole.kt`, the definition file; every `ServiceRole.kt:n` the fake is asked about is a line of it. */
    fun openDefinition(text: String = DEFINITION_SOURCE): PsiFile {
        val file = open(ROLE_FILE, text)
        definitions.holdDefinitionFile(file.virtualFile, arch)
        for (line in 1..120) definitions.resolveWith(DeclarationSiteRef(ROLE_FILE, line), ResolvedSite.Found(file.virtualFile, line))
        return file
    }

    /** The definition is read and says [role]. */
    fun readDefinition(role: RoleModel = serviceRole(), freshness: Freshness = Freshness.Fresh) {
        snapshot = definitionSnapshot(definitionModel(role, projectRoot = root), module(":arch"))
        definitions.emit(DefinitionState.Ready(listOf(snapshot), freshness))
    }

    /** The ◆ of the definition file after the test restarted the daemon (which stands for the refresher). */
    fun definitionMarks(): List<DrawnDefinitionMark> {
        refresherWatch.reset()
        DaemonCodeAnalyzer.getInstance(project).restart(myFixture.file, "katachi test: the definition's answer changed")
        myFixture.doHighlighting()
        return DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
            .filter { it.navigationHandler is DefinitionGutterMenu }
            .sortedBy { it.startOffset }
            .map(::drawnOf)
    }

    private fun drawnOf(info: LineMarkerInfo<*>) = DrawnDefinitionMark(
        line = myFixture.editor.document.getLineNumber(info.startOffset) + 1,
        leaf = info.element?.text.orEmpty(),
        tooltip = info.lineMarkerTooltip.orEmpty(),
        menu = info.navigationHandler as DefinitionGutterMenu,
    )

    companion object {
        const val ROLE_FILE = "ServiceRole.kt"
        val SERVICE_FILES = listOf("core/service/HealthService.kt", "core/service/PingService.kt", "core/service/UserService.kt")

        /** A role at line 2, its file at line 3, its `.template { }` at line 4. */
        val DEFINITION_SOURCE: String = """
            fun definitions() {
                "Service".role {
                    "*Service".ktFile()
                        .template { }
                }
            }
        """.trimIndent() + "\n"
    }
}

/** What a user reads in the open menu: a text per row (`(disabled) ` before one that cannot be chosen, ` | ` before its right side). */
internal fun DefinitionGutterMenu.shown(): List<String> = shownOf(actionGroup(DataContext.EMPTY_CONTEXT).getChildren(null).toList())

private fun shownOf(children: List<AnAction>, indent: String = ""): List<String> = children.flatMap { child ->
    when {
        child is Separator -> listOf(child.text?.takeIf { it.isNotEmpty() }?.let { "== $it" } ?: "-----")
        child is ActionGroup -> listOf("$indent${child.templatePresentation.text} >") + shownOf(child.getChildren(null).toList(), "$indent  ")
        else -> listOf(indent + rowText(child))
    }
}

private fun rowText(action: AnAction): String {
    val event = TestActionEvent.createTestEvent(action)
    ActionUtil.updateAction(action, event)
    val trailing = event.presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)
    return (if (event.presentation.isEnabled) "" else "(disabled) ") + action.templatePresentation.text + (trailing?.let { " | $it" } ?: "")
}

/** The row whose text starts with [prefix]; a test that names none fails. */
internal fun DefinitionGutterMenu.row(prefix: String): DefinitionGutterMenu.RowAction {
    val all = flatRows(actionGroup(DataContext.EMPTY_CONTEXT).getChildren(null).toList())
    return all.firstOrNull { it.templatePresentation.text.startsWith(prefix) } ?: throw AssertionError("No row starts with \"$prefix\" in ${shown()}")
}

private fun flatRows(children: List<AnAction>): List<DefinitionGutterMenu.RowAction> = children.flatMap { child ->
    when (child) {
        is ActionGroup -> flatRows(child.getChildren(null).toList())
        is DefinitionGutterMenu.RowAction -> listOf(child)
        else -> emptyList()
    }
}
