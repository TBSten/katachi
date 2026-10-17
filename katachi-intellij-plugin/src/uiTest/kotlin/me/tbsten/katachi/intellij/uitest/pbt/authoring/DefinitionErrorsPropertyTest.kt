package me.tbsten.katachi.intellij.uitest.pbt.authoring

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.json.parseDefinitionErrorsJson
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionErrorsEvent
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.after
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.DefinitionStateSource
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.LoadingState
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import me.tbsten.katachi.intellij.presentation.applyLoadCancelled
import me.tbsten.katachi.intellij.presentation.applyLoadFailed
import me.tbsten.katachi.intellij.presentation.applyLoaded
import me.tbsten.katachi.intellij.presentation.definitionErrorsEventOf
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.module
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths
import java.time.Instant
import kotlin.random.Random

/**
 * The mistakes of the definition over sequences of what happens to a load (ide-authoring design 2.3, P-1), and the reading
 * of `definition-errors.json` over damaged copies of katachi's golden (P-6).
 *
 * - a mistake is present only when the last load that finished was rejected, and then it is exactly that load's
 * - a load starting, a cancel and a failure of another kind change nothing
 * - it is stale only when the definition changed after the rejected load, and a load that reads the definition clears it
 * - the same holds when the events are derived from the states the ViewModel's real transitions give, with the loading
 * state sometimes never seen and the definition changing between loads; a cancel after a save stays stale
 * - no damaged file makes the parser throw
 */
class DefinitionErrorsPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261018L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val arch = module(":arch")
    private val epoch = Instant.parse("2026-10-18T00:00:00Z")

    private fun mistake(n: Int) = DefinitionErrorModel(arch.id, DefinitionErrorPhase.Processor, "internalDefinitionJson", "me.tbsten.katachi.E$n", "m$n", DeclarationSiteRef("A.kt", n + 1))

    /** What happened, in a form that does not know how [after] is written. */
    private sealed interface Step {
        data object Start : Step
        data object Ok : Step
        data class Rejected(val count: Int) : Step
        data object FailedOther : Step
        data object Changed : Step
        data object Cancelled : Step
    }

    private val stepArb: Arb<Step> = arbitrary { rs ->
        when (rs.random.nextInt(6)) {
            0 -> Step.Start
            1 -> Step.Ok
            2 -> Step.Rejected(rs.random.nextInt(1, 4))
            3 -> Step.FailedOther
            4 -> Step.Changed
            else -> Step.Cancelled
        }
    }

    private fun eventOf(step: Step, at: Instant): DefinitionErrorsEvent = when (step) {
        Step.Start -> DefinitionErrorsEvent.LoadStarted
        Step.Ok -> DefinitionErrorsEvent.LoadOk(at)
        is Step.Rejected -> DefinitionErrorsEvent.LoadRejected(List(step.count, ::mistake), at)
        Step.FailedOther -> DefinitionErrorsEvent.LoadFailedOther
        Step.Changed -> DefinitionErrorsEvent.DefinitionChanged
        Step.Cancelled -> DefinitionErrorsEvent.Cancelled
    }

    /** The answer read off the whole history at once: the last step that finished a load decides, then whether a change followed. */
    private fun expected(steps: List<Step>): Pair<List<DefinitionErrorModel>, Boolean> {
        val deciding = steps.indexOfLast { it is Step.Ok || it is Step.Rejected }
        val last = steps.getOrNull(deciding) as? Step.Rejected ?: return emptyList<DefinitionErrorModel>() to false
        val changedAfter = steps.drop(deciding + 1).any { it == Step.Changed }
        return List(last.count, ::mistake) to changedAfter
    }

    // covers: 定義の誤りの遷移 (P-1)
    @Test
    fun `誤りは最後に終わった読み込みが拒否だったときだけ その読み込みの分があり 変更があれば古いと印が付く`() = runBlocking<Unit> {
        checkAll(config, Arb.list(stepArb, 0..30)) { steps ->
            var state = DefinitionErrors.None
            steps.forEachIndexed { index, step ->
                state = state.after(eventOf(step, epoch.plusSeconds(index.toLong())))
                val (errors, stale) = expected(steps.take(index + 1))
                assertEquals("$steps at $index", errors, state.errors)
                assertEquals("$steps at $index", stale, state.stale)
            }
        }
    }

    // covers: 定義の誤りの遷移 (P-1)
    @Test
    fun `読み込みの開始と取り消しと別の失敗は 値を変えない`() = runBlocking<Unit> {
        checkAll(config, Arb.list(stepArb, 0..20)) { steps ->
            val state = steps.foldIndexed(DefinitionErrors.None) { index, acc, step -> acc.after(eventOf(step, epoch.plusSeconds(index.toLong()))) }
            for (neutral in listOf(DefinitionErrorsEvent.LoadStarted, DefinitionErrorsEvent.Cancelled, DefinitionErrorsEvent.LoadFailedOther)) {
                assertEquals("$steps $neutral", state, state.after(neutral))
            }
        }
    }

    // covers: 定義の誤りの遷移 (P-1)
    @Test
    fun `読み込みの終わりを Rejected が拒否した時刻で持ち 変更の合図は時刻を動かさない`() = runBlocking<Unit> {
        checkAll(config, Arb.int(1..3)) { count ->
            val rejected = DefinitionErrors.None.after(DefinitionErrorsEvent.LoadRejected(List(count, ::mistake), epoch))
            assertEquals(epoch, rejected.readAt)
            val changed = rejected.after(DefinitionErrorsEvent.DefinitionChanged)
            assertEquals(epoch, changed.readAt)
            assertEquals(true, changed.stale)
            assertEquals(rejected.errors, changed.errors)
        }
    }

    // --- derived from the sources the tool window goes through ---

    private fun snapshotList() = listOf(DescriptionSnapshot(arch, null, emptyList(), epoch))

    private fun rejectedFailure(n: Int) = LoadFailure.Gradle(GradleFailure.DefinitionRejected(listOf(mistake(n)), listOf("m$n")))

    /**
     * What watches the tool window's state as the publisher does: a source for each state it is shown (a state equal to the
     * last one is not shown again, as `distinctUntilChanged` has it), the mistakes moved by the event each pair gives. The
     * states come out of the ViewModel's real transitions, so the banner that a load leaves behind is as it is in the IDE.
     */
    private class Watcher(arch: KatachiModule, snapshots: List<DescriptionSnapshot>) {
        var state = KatachiScreenState(phase = ScreenPhase.Ready, modules = listOf(arch), snapshots = snapshots)
        var errors = DefinitionErrors.None
        private var last: DefinitionStateSource? = null
        private var tick = 0L

        init {
            see()
        }

        fun see() {
            val source = DefinitionStateSource.of(state, loadStarted = true, loadsWithoutUser = true)
            if (source == last) return
            definitionErrorsEventOf(last, source, Instant.EPOCH.plusSeconds(++tick))?.let { errors = errors.after(it) }
            last = source
        }

        /** The ViewModel's `load()` starts: the old banner stays. */
        fun loadStarts(seen: Boolean = true) {
            state = state.copy(loading = LoadingState(isInitial = false))
            if (seen) see()
        }

        fun loadEnds(newState: KatachiScreenState) {
            state = newState
            see()
        }

        /** The definition's files changed: the publisher marks the mistakes, with no change of the screen's state. */
        fun definitionChanged() {
            errors = errors.after(DefinitionErrorsEvent.DefinitionChanged)
        }
    }

    private fun Watcher.runLoad(step: Step, number: Int, seen: Boolean) {
        loadStarts(seen)
        loadEnds(
            when (step) {
                Step.Ok -> applyLoaded(state, snapshotList())
                is Step.Rejected -> applyLoadFailed(state, rejectedFailure(number), emptyList())
                Step.FailedOther -> applyLoadFailed(state, LoadFailure.Gradle(GradleFailure.CompilationFailed(listOf("e: x$number"))), emptyList())
                else -> applyLoadCancelled(state)
            },
        )
    }

    // covers: 定義の誤りの遷移 (P-1)
    @Test
    fun `ツールウィンドウの本物の遷移の列から導いた合図でも 誤りと古い印は 履歴から読んだ答えと一致する`() = runBlocking<Unit> {
        // A load (seen or merged into its end), or a change of the definition between loads.
        val opArb = arbitrary { rs ->
            val seen = rs.random.nextInt(4) != 0
            when (rs.random.nextInt(5)) {
                0 -> Step.Ok to seen
                1 -> Step.Rejected(1) to seen
                2 -> Step.FailedOther to seen
                3 -> Step.Cancelled to seen
                else -> Step.Changed to true
            }
        }
        checkAll(config, Arb.list(opArb, 1..14)) { ops ->
            val watcher = Watcher(arch, snapshotList())
            val history = mutableListOf<Step>()
            ops.forEachIndexed { index, (step, seen) ->
                // Each rejection differs from every other, so that a state equal to the last (not shown again) is never one that matters.
                val number = index + 1
                when (step) {
                    Step.Changed -> watcher.definitionChanged()
                    else -> watcher.runLoad(step, number, seen)
                }
                history += if (step is Step.Rejected) Step.Rejected(number) else step
                val rejectedNumber = (history.lastOrNull { it is Step.Ok || it is Step.Rejected } as? Step.Rejected)?.count
                val (expectedErrors, expectedStale) = expected(history).let { (errors, stale) ->
                    (if (rejectedNumber == null) emptyList() else listOf(mistake(rejectedNumber))) to stale
                }
                assertEquals("$ops at $index", expectedErrors, watcher.errors.errors)
                assertEquals("$ops at $index (stale)", expectedStale, watcher.errors.stale)
            }
        }
    }

    // covers: 定義の誤りの遷移 (P-1)
    @Test
    fun `拒否のあと定義を保存し 次の読み込みを取り消しても 誤りは古いままで 新しい誤りとして入り直さない`() {
        val watcher = Watcher(arch, snapshotList())
        watcher.runLoad(Step.Rejected(1), 1, seen = true)
        val rejected = watcher.errors
        assertEquals(false, rejected.stale)

        watcher.definitionChanged()
        watcher.runLoad(Step.Cancelled, 2, seen = true)

        assertEquals(rejected.errors, watcher.errors.errors)
        assertEquals(true, watcher.errors.stale)
    }

    // covers: 定義の誤りの遷移 (P-1)
    @Test
    fun `読み込み中の状態を見逃して拒否の失敗だけが現れても 1回だけ拒否として扱う`() {
        val watcher = Watcher(arch, snapshotList())
        watcher.runLoad(Step.Rejected(1), 1, seen = false)
        assertEquals(listOf(mistake(1)), watcher.errors.errors)
        val readAt = watcher.errors.readAt
        watcher.see()
        assertEquals(readAt, watcher.errors.readAt)
    }

    // covers: 定義の誤りの遷移 (P-1)
    @Test
    fun `読み込み中の状態を見逃して成功だけが現れても 前の誤りは消える`() {
        val watcher = Watcher(arch, snapshotList())
        watcher.runLoad(Step.Rejected(1), 1, seen = true)

        watcher.runLoad(Step.Ok, 2, seen = false)

        assertEquals(DefinitionErrors.None, watcher.errors)
    }

    // --- P-6: the parser never throws ---

    private val golden = ContractFixtures.text("authoring/definition-errors.json")

    private fun damaged(random: Random): String {
        val text = golden
        return when (random.nextInt(5)) {
            0 -> text.take(random.nextInt(text.length))
            1 -> text.drop(random.nextInt(text.length))
            2 -> StringBuilder(text).also { it.setCharAt(random.nextInt(it.length), "{}[]\":,\\\u0000x9".random(random)) }.toString()
            3 -> StringBuilder(text).also { it.insert(random.nextInt(it.length), "\"{[".random(random)) }.toString()
            else -> text.replace(Regex("\"[a-zA-Z]+\":").findAll(text).toList().random(random).value, "\"zzz\":")
        }
    }

    // covers: 誤りの JSON のパーサ (P-6)
    @Test
    fun `壊した golden をどう読ませても 例外は外に出ず 読めた場合は誤りの列か null`() = runBlocking<Unit> {
        checkAll(config, Arb.long()) { s ->
            val text = damaged(Random(s))
            // Reaching here is the property: the parser answered, with a list or with null.
            parseDefinitionErrorsJson(text, Paths.get("/fixture/definition-errors.json"), arch.id)
        }
    }

    // covers: 誤りの JSON のパーサ (P-6)
    @Test
    fun `golden はそのままなら必ず読める`() {
        assertEquals(2, parseDefinitionErrorsJson(golden, Paths.get("/fixture/definition-errors.json"), arch.id)?.size)
    }
}
