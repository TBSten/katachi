package me.tbsten.katachi.intellij.ide.notification

import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.KatachiSettings
import me.tbsten.katachi.intellij.model.TemplateId

/**
 * The editor notification of a file as a table (design section 1, decisions 3, 8, 14): the file's
 * content × whether a template fits × the four settings × the ledger, each row on its own file with
 * the real provider, index and memory. A row is "no panel" or its text key and number of links.
 */
internal class EditorNotificationTableTest : EditorNotificationTestBase() {

    private data class Expected(val textKey: String, val links: Int, val arg: String? = null)

    private class Row(
        val name: String,
        val file: String,
        val text: String,
        val expected: Expected?,
        val settings: KatachiSettings.() -> Unit = {},
        val ledger: (EditorNotificationTableTest, TemplateId) -> Unit = { _, _ -> },
    )

    private val create = Expected("notification.editor.empty", links = 1)
    private val view = Expected("notification.editor.matched", links = 1)

    private fun rows(): List<Row> = listOf(
        Row("空で当たる", "feature/empty/ui/Home.kt", EMPTY, create),
        Row("中身ありで当たる", "feature/content/ui/Home.kt", CONTENT, view),
        Row("空で当たらない", "other/Home.kt", EMPTY, null),
        Row("中身ありで当たらない", "other/Content.kt", CONTENT, null),
        Row("通知の全体が OFF なら空でも出ない", "feature/off/ui/Home.kt", EMPTY, null, settings = { editorNotificationEnabled = false }),
        Row("通知の全体が OFF なら中身ありでも出ない", "feature/off2/ui/Home.kt", CONTENT, null, settings = { editorNotificationEnabled = false }),
        Row("空の通知が OFF なら空には出ない", "feature/noempty/ui/Home.kt", EMPTY, null, settings = { notifyOnEmptyFile = false }),
        Row("空の通知が OFF でも中身ありには出る", "feature/noempty2/ui/Home.kt", CONTENT, view, settings = { notifyOnEmptyFile = false }),
        Row("中身の通知が OFF なら中身ありには出ない", "feature/nocontent/ui/Home.kt", CONTENT, null, settings = { notifyOnFileWithContent = false }),
        Row("中身の通知が OFF でも空には出る", "feature/nocontent2/ui/Home.kt", EMPTY, create, settings = { notifyOnFileWithContent = false }),
        Row("最初の1回だけが OFF でも最初は出る", "feature/always/ui/Home.kt", CONTENT, view, settings = { notifyOnContentOnce = false }),
        Row("package 行とコメントだけなら空", "feature/package/ui/Home.kt", "package com.example\n// note\n/* block */\n", create),
        Row("生成中の .kt は案内だけでコマンドもリンクも出さない", "feature/generating/ui/Home.kt", PROVISIONAL, Expected("notification.editor.generating", 0), ledger = { t, id ->
            t.service.ledger.markGenerating(t.path("feature/generating/ui/Home.kt"), id, COMMAND)
        }),
        Row("生成中の .json はコマンドを出す", "config/generating.json", "", Expected("notification.editor.generating.command", 0, COMMAND), ledger = { t, id ->
            t.service.ledger.markGenerating(t.path("config/generating.json"), id, COMMAND)
        }),
        Row("成功直後は中身ありで当たっても出ない", "feature/succeeded/ui/Home.kt", CONTENT, null, ledger = { t, id ->
            t.service.ledger.markSucceeded(t.path("feature/succeeded/ui/Home.kt"), id)
        }),
        Row("失敗後の .kt は空なので作成するに戻る", "feature/failed/ui/Home.kt", PROVISIONAL, create, ledger = { t, id ->
            t.service.ledger.markFailed(t.path("feature/failed/ui/Home.kt"), id, COMMAND)
        }),
        Row("失敗後の .json はコマンドを出す", "config/failed.json", "", Expected("notification.editor.failed", 0, COMMAND), ledger = { t, id ->
            t.service.ledger.markFailed(t.path("config/failed.json"), id, COMMAND)
        }),
        Row("失敗後の .json でも中身があれば当たりの通知ではなくコマンド", "config/failed2.json", "{}", Expected("notification.editor.failed", 0, COMMAND), ledger = { t, id ->
            t.service.ledger.markFailed(t.path("config/failed2.json"), id, COMMAND)
        }),
        Row("空の .json で当たる", "config/app.json", "", create),
        Row("複数のテンプレートが当たっても通知は1本", "feature/several/ui/HomeScreen.kt", EMPTY, create),
    )

    // covers: 論点3
    // covers: 論点13
    // covers: 論点16
    fun `test ファイルの状態と当たりと設定と台帳の表のとおりに通知が出る`() {
        loadIndex()
        val template = matchesOf("feature/x/ui/Home.kt").first().id
        val failures = mutableListOf<String>()
        for (row in rows()) {
            resetSettings()
            KatachiSettings.getInstance(project).apply(row.settings)
            row.ledger(this, template)
            val editor = open(row.file, row.text)
            val panel = panelOf(editor)
            val actual = panel?.let { Triple(it.text, it.links.size, it.links.map { link -> link.text }) }
            val expected = row.expected?.let { e ->
                val text = when {
                    e.textKey == "notification.editor.failed" ->
                        KatachiBundle.message(e.textKey, KatachiBundle.message("notification.editor.failed.command", e.arg!!))
                    e.arg != null -> KatachiBundle.message(e.textKey, e.arg)
                    else -> KatachiBundle.message(e.textKey)
                }
                text to e.links
            }
            if (actual?.let { it.first to it.second } != expected) failures += "${row.name}: expected $expected, was $actual"
            if (panel != null) {
                panel.button("notification.editor.settings.tooltip")
                panel.button("notification.editor.dismiss.tooltip")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // covers: 論点7
    fun `test 2つの定義に当たるファイルにも通知は1本だけ出る`() {
        synced = syncedWith(":arch-a", ":arch-b")
        loadIndex()
        assertEquals(listOf(":arch-a", ":arch-b"), matchesOf("feature/two/ui/Home.kt").map { it.definition.gradlePath })

        val panel = panelOf(open("feature/two/ui/Home.kt", EMPTY))

        assertEquals(KatachiBundle.message("notification.editor.empty"), panel?.text)
        assertEquals(1, panel!!.links.size)
    }

    // covers: 論点15
    fun `test 中身ありの通知は最初の1回だけで閉じて開き直すと出ない`() {
        loadIndex()
        val file = write("feature/once/ui/Home.kt", CONTENT)

        assertNotNull("first time", panelOf(open(file)))
        assertNotNull("recomputed while still open", run { updateAll(); panelOf(editorOf(file)) })
        assertNull("second time", panelOf(reopen(file)))
    }

    // covers: 論点15
    fun `test 最初の1回だけが OFF なら中身ありの通知は開き直しても出る`() {
        KatachiSettings.getInstance(project).notifyOnContentOnce = false
        loadIndex()
        val file = write("feature/every/ui/Home.kt", CONTENT)

        assertNotNull(panelOf(open(file)))
        assertNotNull(panelOf(reopen(file)))
    }

    // covers: 論点15
    fun `test ファイル A の × と2回目はファイル B の通知に影響しない`() {
        loadIndex()
        val a = write("feature/a/ui/Home.kt", EMPTY)
        val b = write("feature/b/ui/Home.kt", EMPTY)
        val contentA = write("feature/a/ui/Detail.kt", CONTENT)
        val contentB = write("feature/b/ui/Detail.kt", CONTENT)

        panelOf(open(a))!!.button("notification.editor.dismiss.tooltip").doClick()
        panelOf(open(contentA))
        panelOf(reopen(contentA))

        assertNull(panelOf(editorOf(a)))
        assertNotNull("B empty", panelOf(open(b)))
        assertNotNull("B content, first time", panelOf(open(contentB)))
    }

    private fun resetSettings() = KatachiSettings.getInstance(project).run {
        editorNotificationEnabled = true
        notifyOnEmptyFile = true
        notifyOnFileWithContent = true
        notifyOnContentOnce = true
    }

    private companion object {
        const val EMPTY = "package com.example\n"
        const val CONTENT = "package com.example\n\nclass Home\n"
        const val PROVISIONAL = "// katachi: generating\n"
        const val COMMAND = "./gradlew :arch-a:katachiTemplate --arg template=feature.AnyUi"
    }
}
