package me.tbsten.katachi.intellij.presentation.entry

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.expectedNotification
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.notificationInput
import me.tbsten.katachi.intellij.testing.placementMatch
import me.tbsten.katachi.intellij.testing.template
import me.tbsten.katachi.intellij.testing.underRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor notification as tables: named rows first, then every combination of its inputs against [expectedNotification]. */
class NotificationDecisionTableTest {
    private val one = placementMatch()
    private val several = listOf(
        placementMatch(template("data.Repository", id = "impl")),
        placementMatch(template("data.Repository", id = "fake")),
        placementMatch(template("ui.Screen")),
    )
    private val twoDefinitions = listOf(one, placementMatch(template("data.Repository"), definition = module(":arch-b")))
    private val ledgerTemplate = TemplateId(module().id, "data.Repository")
    private val fileA = underRoot("src/A.kt")
    private val fileB = underRoot("src/B.kt")
    private val hidden = NotificationDecision.Hidden

    private fun decide(
        content: FileContentState = FileContentState.Empty,
        matches: List<PlacementMatch> = listOf(one),
        settings: EntrySettings = EntrySettings(),
        memory: EditorNotificationMemory = EditorNotificationMemory.EMPTY,
        ledger: LedgerEntry? = null,
        availability: EntryAvailability = EntryAvailability.Ready,
        commentable: Boolean = true,
        file: java.nio.file.Path = fileA,
    ) = decideNotification(notificationInput(file, content, availability, matches, settings, memory, ledger, commentable))

    // covers: 論点3
    @Test
    fun `空のファイルに当たれば作成を出し当たらなければ出さない`() {
        assertEquals(NotificationDecision.CreateFromTemplate(listOf(one)), decide())
        assertEquals(hidden, decide(matches = emptyList()))
    }

    // covers: 論点3
    @Test
    fun `中身のあるファイルに当たればテンプレートを見るを出し当たらなければ出さない`() {
        assertEquals(NotificationDecision.ViewTemplate(listOf(one)), decide(FileContentState.HasContent))
        assertEquals(hidden, decide(FileContentState.HasContent, matches = emptyList()))
    }

    // covers: 論点3
    @Test
    fun `全体がOFFなら詳細がONでも何も出さない`() {
        val off = EntrySettings(notificationsEnabled = false)
        assertEquals(hidden, decide(settings = off))
        assertEquals(hidden, decide(FileContentState.HasContent, settings = off))
        assertEquals(hidden, decide(settings = off, ledger = LedgerEntry.Generating(ledgerTemplate, "cmd"), commentable = false))
    }

    // covers: 論点3
    @Test
    fun `空と中身の通知は別々にOFFにできる`() {
        assertEquals(hidden, decide(settings = EntrySettings(emptyFileNotification = false)))
        assertTrue(decide(FileContentState.HasContent, settings = EntrySettings(emptyFileNotification = false)) is NotificationDecision.ViewTemplate)
        assertEquals(hidden, decide(FileContentState.HasContent, settings = EntrySettings(contentFileNotification = false)))
        assertTrue(decide(settings = EntrySettings(contentFileNotification = false)) is NotificationDecision.CreateFromTemplate)
    }

    // covers: 論点15
    @Test
    fun `中身の通知は最初の1回だけの設定なら2回目から出さず設定がOFFなら何度でも出す`() {
        val shown = EditorNotificationMemory.EMPTY.markContentNoticeShown(fileA)
        assertEquals(hidden, decide(FileContentState.HasContent, memory = shown))
        assertTrue(decide(FileContentState.HasContent, memory = shown, settings = EntrySettings(contentFirstTimeOnly = false)) is NotificationDecision.ViewTemplate)
    }

    // covers: 論点15
    @Test
    fun `バツで閉じたファイルは空でも中身ありでも出さない`() {
        val closed = EditorNotificationMemory.EMPTY.dismiss(fileA)
        assertEquals(hidden, decide(memory = closed))
        assertEquals(hidden, decide(FileContentState.HasContent, memory = closed, settings = EntrySettings(contentFirstTimeOnly = false)))
    }

    // covers: 論点22
    @Test
    fun `ファイルAのバツと2回目はファイルBに影響しない`() {
        val memory = EditorNotificationMemory.EMPTY.dismiss(fileA).markContentNoticeShown(fileA)
        assertTrue(decide(memory = memory, file = fileB) is NotificationDecision.CreateFromTemplate)
        assertTrue(decide(FileContentState.HasContent, memory = memory, file = fileB) is NotificationDecision.ViewTemplate)
        assertEquals(hidden, decide(memory = memory, file = fileA))
    }

    // covers: 論点8
    @Test
    fun `生成が成功した直後はバツで閉じたのと同じで設定の組み合わせによらず出さない`() {
        val succeeded = LedgerEntry.Succeeded(ledgerTemplate)
        for (content in FileContentState.entries) for (contentOn in listOf(true, false)) for (firstOnly in listOf(true, false)) {
            val settings = EntrySettings(contentFileNotification = contentOn, contentFirstTimeOnly = firstOnly)
            assertEquals("$content content=$contentOn first=$firstOnly", hidden, decide(content, settings = settings, ledger = succeeded))
        }
    }

    // covers: 論点9
    @Test
    fun `複数のテンプレートが当たっても通知は1つで種類は1つのときと同じでどれも入る`() {
        val single = decide()
        val many = decide(matches = several)
        assertEquals(single::class, many::class)
        assertEquals(several, (many as NotificationDecision.CreateFromTemplate).matches)
        assertEquals(one, (decide(FileContentState.HasContent) as NotificationDecision.ViewTemplate).matches.single())
        assertEquals(NotificationDecision.ViewTemplate::class, decide(FileContentState.HasContent, matches = several)::class)
    }

    // covers: 論点10
    @Test
    fun `2つの定義に当たっても通知は1つで両方の当たりを持つ`() {
        val decision = decide(matches = twoDefinitions)
        assertEquals(NotificationDecision.CreateFromTemplate(twoDefinitions), decision)
        assertEquals(setOf(module().id, module(":arch-b").id), (decision as NotificationDecision.CreateFromTemplate).matches.map { it.definition.id }.toSet())
    }

    // covers: 論点3
    @Test
    fun `生成中はコメントを書ける拡張子なら案内だけコメントを書けなければコマンドを出す`() {
        val generating = LedgerEntry.Generating(ledgerTemplate, "./gradlew katachiTemplate")
        assertEquals(NotificationDecision.Generating(null), decide(ledger = generating, commentable = true))
        assertEquals(NotificationDecision.Generating("./gradlew katachiTemplate"), decide(ledger = generating, commentable = false))
    }

    // covers: 論点14
    @Test
    fun `jsonで失敗した後はコマンドの案内を残しコメントを書ける拡張子では作成に戻る`() {
        val failed = LedgerEntry.Failed(ledgerTemplate, "./gradlew katachiTemplate")
        assertEquals(NotificationDecision.FailedWithCommand("./gradlew katachiTemplate"), decide(ledger = failed, commentable = false))
        assertEquals(NotificationDecision.CreateFromTemplate(listOf(one)), decide(ledger = failed, commentable = true))
        // The user's own edit after a failure on a commentable file is an ordinary file with content.
        assertEquals(NotificationDecision.ViewTemplate(listOf(one)), decide(FileContentState.HasContent, ledger = failed, commentable = true))
    }

    // covers: 論点16
    @Test
    fun `一覧が使えない間は当たりがあっても通知を出さない`() {
        for (availability in listOf(EntryAvailability.NotLoaded, EntryAvailability.Loading, EntryAvailability.Unavailable)) {
            assertEquals("$availability", hidden, decide(availability = availability))
            assertEquals("$availability", hidden, decide(FileContentState.HasContent, availability = availability))
        }
    }

    // covers: 論点16
    @Test
    fun `自動読み込みがOFFでキャッシュも無いときは何も出さず設定そのものは判定を変えない`() {
        assertEquals(hidden, decide(availability = EntryAvailability.Unavailable, settings = EntrySettings(loadWithoutUser = false)))
        assertEquals(decide(), decide(settings = EntrySettings(loadWithoutUser = false)))
    }

    // covers: 論点3
    @Test
    fun `入力の全ての組み合わせで規則を1行ずつ書いた期待と一致する`() {
        val contents = FileContentState.entries
        val availabilities = EntryAvailability.entries
        val matchSets = listOf(emptyList(), listOf(one), several, twoDefinitions)
        val flags = listOf(true, false)
        val settingsAll = flags.flatMap { all -> flags.flatMap { e -> flags.flatMap { c -> flags.flatMap { f -> flags.map { l -> EntrySettings(all, e, c, f, l) } } } } }
        val memories = listOf(
            EditorNotificationMemory.EMPTY,
            EditorNotificationMemory.EMPTY.dismiss(fileA),
            EditorNotificationMemory.EMPTY.markContentNoticeShown(fileA),
            EditorNotificationMemory.EMPTY.dismiss(fileA).markContentNoticeShown(fileA),
        )
        val ledgers = listOf(
            null,
            LedgerEntry.Generating(ledgerTemplate, "run"),
            LedgerEntry.Failed(ledgerTemplate, "run"),
            LedgerEntry.Succeeded(ledgerTemplate),
        )
        var count = 0
        for (content in contents) for (availability in availabilities) for (matches in matchSets) for (settings in settingsAll)
            for (memory in memories) for (ledger in ledgers) for (commentable in flags) {
                val input = notificationInput(fileA, content, availability, matches, settings, memory, ledger, commentable)
                assertEquals(input.toString(), expectedNotification(input), decideNotification(input))
                count++
            }
        assertEquals(2 * 4 * 4 * 32 * 4 * 4 * 2, count)
    }

    @Test
    fun `Hiddenは同じ値を返す`() {
        assertSame(NotificationDecision.Hidden, decide(matches = emptyList()))
    }
}
