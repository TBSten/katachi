@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konture

import io.github.baole.konture.Konture
import io.github.baole.konture.OutputFormat
import io.github.baole.konture.core.KontureLogger
import io.github.baole.konture.core.LogLevel
import io.github.baole.konture.core.model.Severity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UncheckedFileConstraint
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.konture.internal.audited

/**
 * `audited { }` switches Konture into audit mode for exactly one block on one thread, and
 * leaves no trace afterwards.
 *
 * The three switches are thread local in Konture, so the two things to prove are that they are
 * back where they were after the block — also when the block throws — and that another thread
 * never sees them flipped.
 */
class KontureAuditSpec : FreeSpec({
    "ブロックの中では failOnSeverity が null になり、終わると戻る" {
        val before = Konture.failOnSeverity
        var inside: Severity? = Severity.ERROR

        audited { inside = Konture.failOnSeverity }

        inside shouldBe null
        Konture.failOnSeverity shouldBe before
    }

    "generateBaseline と logger も戻る" {
        val logged = mutableListOf<String>()
        val previous = KontureLogger.logger
        val mine: (LogLevel, String, Throwable?) -> Unit = { level, message, _ -> logged += "$level $message" }
        KontureLogger.logger = mine
        try {
            var insideLogger: ((LogLevel, String, Throwable?) -> Unit)? = null
            var insideBaseline = true
            audited {
                insideLogger = KontureLogger.logger
                insideBaseline = Konture.generateBaseline
                KontureLogger.log(LogLevel.ERROR, "swallowed")
            }

            insideLogger shouldNotBe mine
            insideBaseline shouldBe false
            KontureLogger.logger shouldBeSameInstanceAs mine
            logged shouldBe emptyList()
        } finally {
            KontureLogger.logger = previous
        }
    }

    "reportPath・outputFormat・各レポートのパスも切り替わり、戻る" {
        // Konture's default reportPath ends in `.html`, which alone makes it write an HTML report on
        // every check(), and an explicit jsonReportPath or an outputFormat does the same for JSON.
        // Inside the block none of them is on; afterwards the reader's own values are back. The
        // values are restored by hand too, because `Konture.reportPath = x.json` also moves
        // jsonReportPath and the two are put back separately.
        val previousPath = Konture.reportPath
        val previousFormat = Konture.outputFormat
        val previousJsonPath = Konture.jsonReportPath
        Konture.reportPath = "mine/konture-report.txt"
        Konture.outputFormat = OutputFormat.JSON
        Konture.jsonReportPath = "mine/konture-report.json"
        try {
            var insidePath = ""
            var insideFormat = OutputFormat.JSON
            var insideJsonPath = ""
            audited {
                insidePath = Konture.reportPath
                insideFormat = Konture.outputFormat
                insideJsonPath = Konture.jsonReportPath
            }

            insidePath.endsWith(".html") shouldBe false
            insidePath.endsWith(".json") shouldBe false
            insidePath.endsWith(".sarif") shouldBe false
            insideFormat shouldBe OutputFormat.HUMAN
            insideJsonPath shouldBe Konture.DEFAULT_JSON_REPORT_PATH
            Konture.reportPath shouldBe "mine/konture-report.txt"
            Konture.outputFormat shouldBe OutputFormat.JSON
            Konture.jsonReportPath shouldBe "mine/konture-report.json"
        } finally {
            Konture.jsonReportPath = previousJsonPath
            Konture.outputFormat = previousFormat
            Konture.reportPath = previousPath
        }
    }

    "ブロックが投げても戻る" {
        val before = Konture.failOnSeverity
        val previousLogger = KontureLogger.logger

        shouldThrow<IllegalStateException> {
            audited { throw IllegalStateException("boom") }
        }

        Konture.failOnSeverity shouldBe before
        KontureLogger.logger shouldBeSameInstanceAs previousLogger
    }

    "別スレッドには漏れない" {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val seenElsewhere = AtomicReference<Severity?>(null)

        val auditing = Thread {
            audited {
                entered.countDown()
                release.await()
            }
        }
        auditing.start()
        entered.await()
        try {
            val observer = Thread { seenElsewhere.set(Konture.failOnSeverity) }
            observer.start()
            observer.join()
        } finally {
            release.countDown()
            auditing.join()
        }

        // Konture's default on a fresh thread, untouched by the thread that is auditing.
        seenElsewhere.get() shouldBe Severity.ERROR
    }

    "違反があっても stdout に Konture の行が出ない" {
        // In audit mode Konture logs every violation as an ERROR line through its default logger,
        // which prints to stdout. `audited { }` swallows the logger, so a run with violations has
        // to leave stdout clean; the report is katachi's to print.
        val captured = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(captured, true, "UTF-8"))
        val violations = try {
            kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
                classes { should().beInternal() }
            }
        } finally {
            System.setOut(original)
        }

        violations.filterIsInstance<UnsatisfiedFileConstraint>().size shouldBe 1
        captured.toString("UTF-8") shouldNotContain "[Konture]"
    }

    "ブロックの中で Konture.reset() や failOnSeverity を書いても、規則は audit で評価される" {
        // Both are members of the `Konture` object, which no shadow can reach, and both put the
        // thread's failOnSeverity back to ERROR. Audit mode is entered again after the block has
        // declared its rules, so the violation below is still read back rather than thrown.
        val violations = kontureRun("src/PublicThing.kt" to PUBLIC_THING_KT) {
            Konture.reset()
            Konture.failOnSeverity = Severity.ERROR
            classes { should().beInternal() }
        }

        violations.filterIsInstance<UncheckedFileConstraint>().shouldBeEmpty()
        violations.filterIsInstance<UnsatisfiedFileConstraint>().single().path shouldBe "src/PublicThing.kt"
    }
})
