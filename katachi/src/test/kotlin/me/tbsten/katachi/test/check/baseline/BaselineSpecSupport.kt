package me.tbsten.katachi.test.check.baseline

import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.BaselineRuns
import me.tbsten.katachi.check.internal.BaselineStore
import me.tbsten.katachi.check.internal.assertNoErrors
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

/** Where the fake repositories of these specs keep their baseline, as the store sees it. */
internal const val BASELINE_FILE: String = "/repo/katachi-baseline.json"

/** The same file as a report prints it. */
internal const val BASELINE_URI: String = "file:///repo/katachi-baseline.json"

/**
 * A baseline store that lives in memory, so a spec can seed a file, run, and read back what
 * was written without touching the disk.
 */
internal class MemoryBaselineStore(vararg initial: Pair<String, String>) : BaselineStore {
    val files: MutableMap<String, String> = mutableMapOf(*initial)

    /** How many times anything was written, which is how a spec says "nothing was written". */
    var writes: Int = 0
        private set

    override fun read(file: String): String? = files[file]

    override fun write(file: String, text: String) {
        writes++
        files[file] = text
    }
}

/**
 * A [BaselineEnvironment] whose system properties and environment variables are given here.
 *
 * Each call remembers the runs of its own JVM afresh unless [runs] is shared: a spec that
 * changes "the project" between two runs is not the situation `BaselineRuns` guards against.
 */
internal fun environmentOf(
    store: BaselineStore,
    update: Boolean = false,
    prune: Boolean = false,
    ci: Boolean = false,
    standardError: MutableList<String> = mutableListOf(),
    runs: BaselineRuns = BaselineRuns(),
): BaselineEnvironment = BaselineEnvironment(
    store = store,
    systemProperty = { key ->
        when (key) {
            "katachi.baseline.update" -> if (update) "true" else null
            "katachi.baseline.prune" -> if (prune) "true" else null
            else -> null
        }
    },
    environmentVariable = { key -> if (key == "CI" && ci) "true" else null },
    standardError = { standardError += it },
    runs = runs,
)

/** A violation of a check outside katachi, keyed by its label and path only. */
internal class ForeignViolation(
    override val path: String,
    override val label: String = "TodoRule",
    override val severity: Severity = Severity.Error,
) : Violation {
    override val kind: ViolationKind get() = ViolationKind.FileConstraint
    override fun toString(): String = "[$label] $path"
}

/**
 * A check outside katachi that answers with whatever [violations] holds at the moment it runs,
 * so a spec can change what "the project" breaks between two runs.
 */
internal class ScriptedCheck(var violations: List<Violation> = emptyList()) :
    ArchitectureProcessorNoArg<List<Violation>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<Violation>> =
        runCatching { violations.assertNoErrors(null) }
}

/** A second check class, so a spec can tell two checks' entries apart. */
internal class OtherScriptedCheck(var violations: List<Violation> = emptyList()) :
    ArchitectureProcessorNoArg<List<Violation>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<Violation>> =
        runCatching { violations.assertNoErrors(null) }
}

/** A check that throws instead of answering: the run becomes a partial result. */
internal class ThrowingScriptedCheck : ArchitectureProcessorNoArg<List<Violation>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<Violation>> =
        throw IllegalStateException("broken check")
}

/** The fully qualified name a check's entries are filed under. */
internal val SCRIPTED: String = ScriptedCheck::class.qualifiedName!!
internal val OTHER_SCRIPTED: String = OtherScriptedCheck::class.qualifiedName!!
internal const val LAYOUT: String = "me.tbsten.katachi.check.LayoutCheck"
