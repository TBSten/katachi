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
    fun `UnexpectedDirectory の項目は一番上の知らないディレクトリと等しいときだけ許容する`() {
        val top = "$USE_CASE_DIRECTORY/helpers"
        val deep = "$top/a/b/File.kt"
        val cases = listOf(
            "項目が一番上の知らないディレクトリと等しい" to (STRAY to top),
            "深い場所のファイルでも一番上の知らないディレクトリと等しい" to (deep to top),
            "項目がそれより深いディレクトリ" to (deep to "$top/a"),
            "項目がそれより深いディレクトリ 2 段" to (deep to "$top/a/b"),
            "項目が既知の祖先" to (STRAY to USE_CASE_DIRECTORY),
            "項目が名前だけ前方一致する別のディレクトリ" to (STRAY to "$USE_CASE_DIRECTORY/help"),
            "項目がファイル自身の path" to (STRAY to STRAY),
        )
        for ((name, case) in cases) {
            val expected = if (case.second == top) BaselineVerdict.Held("UnexpectedDirectory") else BaselineVerdict.NotHeld
            assertEquals(name, expected, verdict(case.first, ledger("UnexpectedDirectory" to case.second)))
        }
        assertEquals(BaselineVerdict.NotHeld, verdict(STRAY, ledger("UnexpectedFile" to STRAY)))
    }

    @Test
    fun `baseline ファイル自身への UnexpectedFile は項目があっても許容しない`() {
        val input = noRoleInput(MISNAMED, baseline = ledger("UnexpectedFile" to MISNAMED))
        val named = input.copy(architecture = input.architecture?.copy(baseline = MISNAMED))
        assertEquals(BaselineVerdict.NotHeld, (decideNoRoleNotification(named) as NoRoleDecision.NoRole).baseline)
        assertEquals(BaselineVerdict.Held("UnexpectedFile"), (decideNoRoleNotification(input) as NoRoleDecision.NoRole).baseline)
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
