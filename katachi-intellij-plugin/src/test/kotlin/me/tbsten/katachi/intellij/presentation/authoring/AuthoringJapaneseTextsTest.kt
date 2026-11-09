package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.TemplateRef
import me.tbsten.katachi.intellij.model.declarations
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.declaredAt
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.englishAuthoring
import me.tbsten.katachi.intellij.testing.japaneseAuthoring
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths
import java.time.Instant

/**
 * The definition file under the Japanese language pack: the ◆'s tooltip and menu, the Code Vision and its popup, the hover of a
 * mistake and the kind of a completion say their own words in Japanese. The English texts are the ones the other tests of this
 * package compare with.
 */
class AuthoringJapaneseTextsTest {
    private val arch = module(":arch").id
    private val file = "ServiceRole.kt"
    private val root = Paths.get("/w/repo")
    private val definitionFile = root.resolve("arch/src/test/kotlin/ServiceRole.kt")
    private val both = DefinitionMenuAvailability(runCheck = true, refresh = true)

    private fun files(n: Int) = (1..n).map { "core/service/Service$it.kt" }

    private fun role(matched: List<String> = files(3), count: Int? = null): RoleModel = roleModel(
        "domain.Service",
        layoutEntry("core/service/*.kt", matchedFiles = matched).declaredAt(file, 3).let { entry ->
            entry.copy(matchedCount = count ?: entry.matchedCount, template = TemplateRef("domain.Service.service", "service", "Service", DeclarationSiteRef(file, 4)))
        },
    ).declaredAt(file, 2)

    private fun mark(kind: AnchorKind = AnchorKind.Role, role: RoleModel = role(), stale: Boolean = false, strings: AuthoringStrings = japaneseAuthoring): DefinitionGutterMark {
        val (name, line) = if (kind == AnchorKind.Role) "Service" to 2 else "*.kt" to 4
        val bound = AnchorBinding.bind(listOf(Anchor(kind, name, line)), definitionModel(role).declarations(arch)).single()
        return DefinitionGutterMarks.of(bound, definitionFile, root, stale, strings)!!
    }

    private fun texts(menu: DefinitionMenuModel) = menu.items.map {
        when (it) {
            is DefinitionMenuItem.Row -> it.text
            is DefinitionMenuItem.Section -> "== ${it.title}"
            is DefinitionMenuItem.Submenu -> "${it.text} >"
            DefinitionMenuItem.Separator -> "---"
        }
    }

    private fun entry(files: Int?, required: Boolean = false, cut: Boolean = false): LayoutEntryModel {
        val listed = List(files?.coerceAtMost(5) ?: 0) { "a/F$it.kt" }
        return layoutEntry("a/*.kt", required = required, matchedFiles = listed).copy(matchedCount = files, truncated = cut)
    }

    @Test
    fun `日本語の Code Vision は ファイルの数を N ファイルと言い 必須 切られた一覧 modules 古さ も日本語で言う`() {
        val rows = listOf(
            entry(0) to "ファイルなし",
            entry(0, required = true) to "ファイルなし（必須）",
            entry(1) to "1 ファイル",
            entry(3) to "3 ファイル",
            entry(null) to "? ファイル",
            entry(900, cut = true) to "5+ ファイル",
        )
        for ((line, expected) in rows) assertEquals(expected, CountText.of(listOf(line), stale = false, japaneseAuthoring))
        val two = listOf(entry(2), entry(1).copy(matchedFiles = listOf("b/G.kt")))
        assertEquals("3 ファイル（2 モジュール）（変更の前の結果）", CountText.of(two, stale = true, japaneseAuthoring, modules = 2))
        assertEquals("3 files (2 modules) (stale)", CountText.of(two, stale = true, englishAuthoring, modules = 2))
    }

    @Test
    fun `日本語の役割の ◆ は ファイルの数を Code Vision と同じ言い方で言い メニューも日本語`() {
        assertEquals("katachi: domain.Service — 3 ファイル", mark().tooltip)
        assertEquals("katachi: domain.Service — 3 ファイル（変更の前の結果）", mark(stale = true).tooltip)
        assertEquals("katachi: domain.Service — ? ファイル", mark(role = role(emptyList(), count = null).let { r -> r.copy(layout = r.layout.map { it.copy(matchedCount = null) }) }).tooltip)
        assertEquals(CountText.of(role().layout, stale = false, japaneseAuthoring), mark().tooltip.substringAfter(" — "))

        assertEquals(
            listOf("Service1.kt", "Service2.kt", "Service3.kt", "+ このテンプレートから生成…", "---", "▶ 検査を実行（:arch:test）", "⟳ 定義を読み直す"),
            texts(mark().menu(both, japaneseAuthoring)),
        )
        assertEquals("⟳ 定義を読み直す", texts(mark(stale = true).menu(both, japaneseAuthoring)).first())
        assertEquals("domain.Service — 3 ファイル", mark().menu(both, japaneseAuthoring).title)
    }

    @Test
    fun `日本語の役割の ◆ のメニューは ファイルが無いときと 多すぎるときも日本語で言う`() {
        assertEquals("— ファイルはまだありません", texts(mark(role = role(emptyList())).menu(both, japaneseAuthoring)).first())
        val unknown = role(emptyList()).let { r -> r.copy(layout = r.layout.map { it.copy(matchedCount = null) }) }
        assertEquals("— ファイルの数は不明（検査がまだ走っていません）", texts(mark(role = unknown).menu(both, japaneseAuthoring)).first())
        val many = texts(mark(role = role(files(1250))).menu(both, japaneseAuthoring))
        assertTrue(many.toString(), many.contains("… ほか 1050 ファイル（ツールウィンドウで見られます）"))
        assertTrue(texts(mark(role = role(files(1250)), strings = englishAuthoring).menu(both, englishAuthoring)).contains("… and 1050 more (open the tool window)"))
    }

    @Test
    fun `日本語の template の ◆ は どの役割のテンプレートから生成するかを日本語で言い メニューも日本語`() {
        val template = mark(AnchorKind.Template)
        assertEquals("katachi: domain.Service のテンプレートから生成", template.tooltip)
        assertEquals(listOf("+ このテンプレートから生成…", "⊞ ツールウィンドウで表示"), texts(template.menu(both, japaneseAuthoring)))
        assertEquals("katachi: generate from domain.Service", mark(AnchorKind.Template, strings = englishAuthoring).tooltip)
    }

    @Test
    fun `日本語の Code Vision のポップアップは 当たるファイルが無い glob と そのディレクトリの中身を日本語で言う`() {
        val none = EntryFilesInput(listOf("src/main/kotlin/*Servise.kt", "*.md"), emptyList(), 0, root)
        val popup = EntryFilesPopups.of(none, japaneseAuthoring) { directory, limit -> if (directory.isEmpty()) emptyList() else List(limit) { "F$it.kt" } }
        val shown = popup.rows.map { it.text }
        assertEquals("— src/main/kotlin/*Servise.kt に一致するファイルはありません", shown[0])
        assertEquals("— src/main/kotlin にあるファイル:", shown[1])
        assertTrue(shown.toString(), shown.containsAll(listOf("… ほかにもあります", "— *.md に一致するファイルはありません", "— プロジェクトのルート にファイルはありません")))

        val unknown = EntryFilesPopups.of(none.copy(count = null), japaneseAuthoring) { _, _ -> emptyList() }
        assertEquals(listOf("— ファイルの数は不明（検査がまだ走っていません）"), unknown.rows.map { it.text })
        val more = EntryFilesPopups.of(EntryFilesInput(listOf("d/*.kt"), listOf("d/A.kt"), 9, root), japaneseAuthoring) { _, _ -> emptyList() }
        assertEquals("… ほか 8 ファイル（ツールウィンドウで見られます）", more.rows.last().text)
    }

    @Test
    fun `日本語の定義の誤りのホバーは 古い答えの印だけが日本語で katachi の文はそのまま`() {
        val mistake = DefinitionErrorModel(arch, DefinitionErrorPhase.Processor, "internalDefinitionJson", "X", "boom", DeclarationSiteRef("ScreenRole.kt", 3))
        val errors = DefinitionErrors(listOf(mistake), Instant.parse("2026-10-18T00:00:00Z"), stale = true)
        val hover = { strings: AuthoringStrings -> DefinitionErrorTargets.marksOf(errors, emptyList(), false, { _, site -> site.line }, 20, strings).single().hover }
        assertEquals("（変更の前の結果）katachi: boom", hover(japaneseAuthoring))
        assertEquals("(stale) katachi: boom", hover(englishAuthoring))
    }

    @Test
    fun `日本語の補完の種類は 引数と capture を日本語で言う`() {
        val screen = template(
            "ui.Screen",
            parameters = listOf(stringParam("title")),
            captures = listOf(capture("feature"), capture("name", pattern = ":feature:*", module = true)),
        )
        val snapshots = listOf(DefinitionSnapshot(module(":arch"), null, listOf(screen), null, DEFINITION_INSTANT))
        val kinds = argCandidates("ui.Screen", snapshots, japaneseAuthoring).associate { it.text to it.typeText }
        assertEquals(mapOf("title" to "引数", "feature" to "パスの capture", "name" to "モジュールの capture"), kinds)
        val summaryOnly = listOf(DefinitionSnapshot(module(":arch"), null, listOf(screen.copy(detail = null)), null, DEFINITION_INSTANT))
        assertEquals(mapOf("title" to "引数", "feature" to "capture", "name" to "capture"), argCandidates("ui.Screen", summaryOnly, japaneseAuthoring).associate { it.text to it.typeText })
        assertEquals(mapOf("title" to "parameter", "feature" to "path capture", "name" to "module capture"), argCandidates("ui.Screen", snapshots, englishAuthoring).associate { it.text to it.typeText })
    }

    @Test
    fun `日本語の文言はどれも日本語の文字を含み 英語の文言は日本語を含まず どちらも引数を埋め残さない`() {
        val exceptions = setOf("capture") // The DSL's word in both languages.
        for ((name, text) in textsOf(japaneseAuthoring)) {
            assertFalse("ja $name: $text", PLACEHOLDER.containsMatchIn(text))
            if (name !in exceptions) assertTrue("ja $name: $text", JAPANESE.containsMatchIn(text))
        }
        for ((name, text) in textsOf(englishAuthoring)) assertFalse("en $name: $text", PLACEHOLDER.containsMatchIn(text) || JAPANESE.containsMatchIn(text))
    }

    private fun textsOf(s: AuthoringStrings): List<Pair<String, String>> = listOf(
        "staleSuffix" to s.staleSuffix, "stalePrefix" to s.stalePrefix, "filesNull" to s.files(null), "files0" to s.files(0), "files1" to s.files(1),
        "files5" to s.files(5), "filesAtLeast" to s.filesAtLeast(200), "missingRequired" to s.missingRequired, "modules" to s.modules(4),
        "generateFrom" to s.generateFrom("a.B"), "countUnknown" to s.countUnknown, "noFileYet" to s.noFileYet, "moreFiles" to s.moreFiles(3),
        "runCheck" to s.runCheck(":a:test"), "reload" to s.reload, "generateFromThis" to s.generateFromThis, "showInToolWindow" to s.showInToolWindow,
        "noFileMatches" to s.noFileMatches("*.kt"), "noFileIn" to s.noFileIn("d"), "filesIn" to s.filesIn("d"), "projectRoot" to s.projectRoot,
        "andMore" to s.andMore, "pathCapture" to s.pathCapture, "moduleCapture" to s.moduleCapture, "capture" to s.capture, "parameter" to s.parameter,
    )

    private companion object {
        val JAPANESE = Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]")

        /** An argument left in a text: `{0}`. */
        val PLACEHOLDER = Regex("\\{\\d")
    }
}
