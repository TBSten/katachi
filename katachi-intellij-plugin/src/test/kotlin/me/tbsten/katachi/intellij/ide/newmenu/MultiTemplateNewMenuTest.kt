package me.tbsten.katachi.intellij.ide.newmenu

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.indexOf
import me.tbsten.katachi.intellij.testing.patternTemplate
import me.tbsten.katachi.intellij.testing.snapshotOf
import java.io.File

/**
 * Verification of New > katachi when many templates fit one directory: the real output of
 * sample/android (one role with four ids, two roles sharing the title `settings`), and synthetic
 * overlaps (same pattern in two roles, two definitions).
 */
internal class MultiTemplateNewMenuTest : NewMenuTestBase() {
    private val android = ContractFixtures.templates("sample-android-with-captures")
    private val dump = File(System.getProperty("katachi.verify.dump", "/Users/tbsten/dev/katachi/.local/tmp/verify-multi-template/newmenu-android.txt"))

    private fun problemsOf(actions: Array<AnAction>, path: String): List<String> {
        val problems = mutableListOf<String>()
        val labels = actions.map { it.templatePresentation.text.orEmpty() }
        if (labels.any { it.isBlank() }) problems += "$path: blank label in $labels"
        labels.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { problems += "$path: duplicate sibling '$it' in $labels" }
        actions.forEach { action ->
            if (action is ActionGroup) {
                val children = action.getChildren(null)
                val name = action.templatePresentation.text
                if (children.isEmpty()) problems += "$path/$name: empty submenu"
                problems += problemsOf(children, "$path/$name")
            }
        }
        return problems
    }

    private fun directoriesOf(templates: List<me.tbsten.katachi.intellij.model.TemplateModel>): List<String> =
        templates.flatMap { t ->
            val pattern = t.detail!!.files.first().pattern
            val segments = pattern.split('/').dropLast(1)
            (0..segments.size).map { segments.take(it).joinToString("/") }
        }.filter { it.isNotEmpty() }.distinct().sorted()

    fun `test sample-android の実データで全ディレクトリの New メニューに重複・空の階層・空ラベルが無い`() {
        installMenuService { indexOf(snapshotOf(moduleA, android)) }
        loadAndSettle()
        val problems = mutableListOf<String>()
        val out = StringBuilder()
        for (relative in directoriesOf(android)) {
            val dir = directory(relative)
            val event = eventOn(dir)
            group.update(event)
            if (!event.presentation.isEnabledAndVisible) continue
            val children = group.getChildren(event)
            out.appendLine("## $relative")
            outline(children).forEach { out.appendLine(it) }
            problems += problemsOf(children, relative)
        }
        dump.writeText(out.toString())
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    fun `test 同じパターンに当たる2つの役割と id 違いは階層ごとに1回ずつ並ぶ`() {
        installReadyMenu(
            patternTemplate("data.Repository", "data/\${name}Repository.kt", id = "user"),
            patternTemplate("data.Repository", "data/\${name}Repository.kt", id = "settings"),
            patternTemplate("data.Cache", "data/\${name}Repository.kt"),
            patternTemplate("Loose", "data/\${name}Repository.kt"),
        )
        val lines = menuOn(directory("data"))
        assertEquals(
            listOf(
                "data", "  Repository", "    user — <name>Repository.kt", "    settings — <name>Repository.kt",
                "  Cache — <name>Repository.kt", "Loose — <name>Repository.kt",
            ),
            lines,
        )
    }

    fun `test 定義が2つで同じ役割の同じテンプレートは定義ごとの段に1回ずつ出る`() {
        installMenuService { indexOf(snapshotOf(moduleA, android), snapshotOf(moduleB, android)) }
        loadAndSettle()
        val lines = menuOn(directory("data/src/main/kotlin/com/example/sample/data/user"))
        val problems = problemsOf(eventOn(directory("data/src/main/kotlin/com/example/sample/data/user")).let { e -> group.update(e); group.getChildren(e) }, "user")
        File(dump.path + ".defs").writeText(lines.joinToString("\n"))
        assertEquals(problems.joinToString("\n"), emptyList<String>(), problems)
        assertEquals(1, lines.count { it == ":arch-a" })
        assertEquals(1, lines.count { it == ":arch-b" })
    }
}
