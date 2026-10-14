package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.data.baseline.BaselineLedger
import me.tbsten.katachi.intellij.data.baseline.BaselineReading
import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ide.notification.noRoleLines
import me.tbsten.katachi.intellij.testing.MISNAMED
import me.tbsten.katachi.intellij.testing.STRAY
import me.tbsten.katachi.intellij.testing.USE_CASE_DIRECTORY
import me.tbsten.katachi.intellij.testing.noRoleInput
import org.junit.Assert.assertEquals
import org.junit.Test

/** How the baseline changes the no-role decision: which file it holds, and which wording each reading of the baseline gets. */
class NoRoleBaselineDecisionTest {
    private fun ledger(vararg entries: Pair<String, String>) = BaselineReading.Read(BaselineLedger(entries.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }))

    private fun verdict(relative: String, reading: BaselineReading): BaselineVerdict =
        (decideNoRoleNotification(noRoleInput(relative, baseline = reading)) as NoRoleDecision.NoRole).baseline

    @Test
    fun `baseline の表 読みの種類ごとの判定`() {
        val held = ledger("UnexpectedFile" to MISNAMED)
        val cases = listOf(
            "baseline を設定していない" to (BaselineReading.NotConfigured to BaselineVerdict.NotHeld),
            "項目が無い" to (ledger() to BaselineVerdict.NotHeld),
            "項目がある" to (held to BaselineVerdict.Held("UnexpectedFile")),
            "別のファイルの項目" to (ledger("UnexpectedFile" to "other/File.kt") to BaselineVerdict.NotHeld),
            "rule が違う" to (ledger("UnexpectedDirectory" to MISNAMED) to BaselineVerdict.NotHeld),
            "ファイルが無い" to (BaselineReading.Missing to BaselineVerdict.Unknown),
            "知らない版" to (BaselineReading.UnknownVersion(2) to BaselineVerdict.Unknown),
            "壊れている" to (BaselineReading.Unreadable("x") to BaselineVerdict.Unknown),
        )
        for ((name, case) in cases) assertEquals(name, case.second, verdict(MISNAMED, case.first))
    }

    @Test
    fun `知らないディレクトリの下のファイルは UnexpectedDirectory の項目で許容され UnexpectedFile の項目では許容されない`() {
        assertEquals(BaselineVerdict.Held("UnexpectedDirectory"), verdict(STRAY, ledger("UnexpectedDirectory" to "$USE_CASE_DIRECTORY/helpers")))
        assertEquals(BaselineVerdict.Held("UnexpectedDirectory"), verdict(STRAY, ledger("UnexpectedDirectory" to USE_CASE_DIRECTORY)))
        assertEquals(BaselineVerdict.NotHeld, verdict(STRAY, ledger("UnexpectedFile" to STRAY)))
    }

    @Test
    fun `許容中は弱い印の一行 許容されていなければ断定 読めなければ条件付きの文言`() {
        fun first(reading: BaselineReading, untracked: Boolean = false) =
            noRoleLines((decideNoRoleNotification(noRoleInput(MISNAMED, baseline = reading, vcs = if (untracked) VcsTracking.Untracked else VcsTracking.Tracked)) as NoRoleDecision.NoRole)).first

        assertEquals(KatachiBundle.message("notification.rules.baselineHeld", "UnexpectedFile"), first(ledger("UnexpectedFile" to MISNAMED)))
        assertEquals(KatachiBundle.message("notification.rules.noRole.sure"), first(ledger()))
        assertEquals(KatachiBundle.message("notification.rules.noRole"), first(BaselineReading.UnknownVersion(9)))
        assertEquals(KatachiBundle.message("notification.rules.noRole.untracked"), first(BaselineReading.Missing, untracked = true))
    }

    @Test
    fun `許容中の通知は近い Role の行を持たない`() {
        val decision = decideNoRoleNotification(noRoleInput(MISNAMED, baseline = ledger("UnexpectedFile" to MISNAMED))) as NoRoleDecision.NoRole
        assertEquals(null, noRoleLines(decision).second)
    }
}
