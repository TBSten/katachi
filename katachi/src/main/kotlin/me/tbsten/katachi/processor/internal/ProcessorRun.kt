package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.check.DEFAULT_MAX_VIOLATIONS
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.BaselineMode
import me.tbsten.katachi.check.internal.CheckedViolations
import me.tbsten.katachi.check.internal.applyBaseline
import me.tbsten.katachi.check.internal.checkNameOf
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.internal.displayPath
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.KatachiProcessorNotFoundException
import me.tbsten.katachi.processor.KatachiProcessorNotInstantiableException
import me.tbsten.katachi.processor.KatachiProcessorTypeException
import me.tbsten.katachi.processor.decodeFromStringMap

/**
 * How many of a `runProcessors` call's processors succeeded and how many failed.
 *
 * `main()` reads [failed] to decide the process's exit status; a spec reads both to assert on
 * the run as a whole without re-parsing the printed summary line.
 */
internal data class ProcessorRunSummary(val succeeded: Int, val failed: Int)

/**
 * Turns a registered processor class into a running [ArchitectureProcessor].
 *
 * Tries a Kotlin `object`'s singleton `INSTANCE` field first, then a no-argument constructor.
 * Both are read reflectively and made accessible, since a generated caller outside the class's
 * own module cannot otherwise reach a non-public declaration.
 *
 * @throws KatachiProcessorNotInstantiableException when neither an `INSTANCE` field nor a
 *   no-argument constructor is there.
 * @throws KatachiProcessorTypeException when [type] was instantiated but is not an
 *   [ArchitectureProcessor].
 */
internal fun instantiateProcessor(type: Class<*>): ArchitectureProcessor<*, *> {
    val instance = runCatching {
        type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
    }.recoverCatching {
        type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }.getOrElse { cause -> throw KatachiProcessorNotInstantiableException(type, cause) }

    return instance as? ArchitectureProcessor<*, *> ?: throw KatachiProcessorTypeException(type)
}

/**
 * Runs every processor of [processorKeys] against [architecture], each with its own slice of
 * [rawArgs], on one walk of [fileSystem], and prints a report through [out].
 *
 * Resolving every key and instantiating every processor happens before any of them runs, so a
 * mistake in the second key of three costs nothing the first already did. Likewise
 * `--arg` names are checked against the union of every selected processor's arguments before any
 * of them runs -- see `checkNoUnknownArgs`.
 *
 * One processor's failure does not stop the others: each result is collected independently, and
 * the summary at the end reports how many of each there were. A processor fails the run by
 * answering `Result.failure` or by throwing; both are reported as `[FAILED]` with the
 * exception's message, and a `success` is `[OK]` with its value.
 *
 * @param argsFor arguments for one processor only, by processor key: what `--arg-for` carries.
 *   A processor is given its own entry here with [rawArgs] laid over it, so a command-line
 *   `--arg` wins over a build-script value exactly as it does for a single-processor run. Each
 *   entry's names are checked against its own processor alone.
 * @param out where the report's lines go. Defaults to [println], but a spec passes
 *   `mutableListOf<String>::add` instead so the run can be asserted on without capturing standard
 *   output.
 * @param baselineEnvironment where the definition's baseline is read from, when it has one.
 */
internal fun runProcessors(
    architecture: Architecture,
    registry: Map<String, Class<*>>,
    processorKeys: List<String>,
    rawArgs: Map<String, String>,
    argsFor: Map<String, Map<String, String>> = emptyMap(),
    fileSystem: KatachiFileSystem = RealFileSystem(),
    out: (String) -> Unit = ::println,
    baselineEnvironment: BaselineEnvironment = BaselineEnvironment(),
): ProcessorRunSummary {
    out("[1/3] Processors: ${processorKeys.joinToString(", ")}")
    out("")

    out("[2/3] Processing...")

    // Resolved and instantiated before any of them runs: a KatachiProcessorNotFoundException or
    // KatachiProcessorNotInstantiableException on the third key must not leave the first two
    // having already run.
    val selected: List<Pair<String, ArchitectureProcessor<*, *>>> = processorKeys.map { key ->
        val type = registry[key] ?: throw KatachiProcessorNotFoundException(key, registry.keys)
        key to instantiateProcessor(type)
    }

    // `rawArgs` travels on the context as well as being decoded into each processor's own Args:
    // a processor whose vocabulary differs per role -- a template's parameters -- has no fixed
    // set of fields to declare them as. `withArgs` carries it to every processor of the run.
    //
    // Built before the check rather than after it, because the check asks the processors what
    // this run made legal and they read that off the context. Nothing is walked by building it:
    // the walk behind it is `by lazy` and no processor has run yet.
    val base = RealArchitectureProcessContext(architecture, Unit, fileSystem, rawArgs = rawArgs)
    // One context per processor, all on `base`'s walk: only the arguments differ, and only for a
    // processor that has an `argsFor` entry.
    val contexts: Map<String, ArchitectureProcessContext<*>> = selected.associate { (key, _) ->
        val own = argsFor[key]
        key to if (own.isNullOrEmpty()) base else RealArchitectureProcessContext(base.walk, Unit, base.onLog, own + rawArgs)
    }

    checkNoUnknownArgs(selected, contexts, rawArgs, argsFor)

    data class Entry(val key: String, val result: Result<Any?>)

    val outcomes = selected.map { (key, processor) ->
        val context = contexts.getValue(key)
        // A throw and a `Result.failure` land in the same place: on the command line, "could
        // not do the job" and "did the job, and the answer is no" both fail the run.
        val result = runCatching {
            erase(processor).run(context, context.rawArgs) { message -> out("  [$key] $message") }.getOrThrow()
        }
        Entry(key, result.againstBaseline(architecture, processor, base.walk, baselineEnvironment))
    }

    val succeeded = outcomes.count { it.result.isSuccess }
    val failed = outcomes.size - succeeded

    val separator = "=".repeat(40)
    out("")
    out(separator)
    out("[3/3] Katachi processor run: $succeeded succeeded, $failed failed")
    out("")

    // Only asked for when a violation is printed, and never by starting a walk: finding the root
    // is a few `exists` calls, and a root that cannot be found just leaves the paths relative.
    val projectRoot by lazy { catching { findProjectRoot(fileSystem).path.value }.getOrNull() }

    for (outcome in outcomes) {
        outcome.result.fold(
            onSuccess = { produced ->
                out("[OK] ${outcome.key}")
                if (produced is HeldBack) {
                    produced.violations.forEach { element -> out(lineOf(element, projectRoot)) }
                    produced.trailer.forEach { line -> out("  $line") }
                    return@fold
                }
                // One element per line. A collection printed through `toString` arrives as a
                // single bracketed line.
                when (produced) {
                    Unit -> Unit
                    is Collection<*> -> produced.forEach { element -> out(lineOf(element, projectRoot)) }
                    else -> out(produced.toString())
                }
            },
            onFailure = { cause ->
                out("[FAILED] ${outcome.key}")
                // Line by line, so that a multi-line message -- a check's whole report -- stays
                // indented under its processor instead of only its first line.
                (cause.message ?: cause.toString()).lines().forEach { line -> out("  $line") }
            },
        )
        out("")
    }
    out(separator)

    return ProcessorRunSummary(succeeded = succeeded, failed = failed)
}

/**
 * A check's answer after the baseline held part of it back: what is left, and the lines saying
 * how much was held back. Only ever a success -- anything left that fails is a failure instead.
 */
private class HeldBack(val violations: List<Violation>, val trailer: List<String>)

/**
 * [this] result of [processor], compared with the definition's baseline when there is one and
 * the processor is a check -- its class declares a list of violations as its answer (see
 * [declaresViolationList]), or it failed with a [KatachiArchitectureAssertionError] carrying them.
 *
 * Only compared, never updated: `-Dkatachi.baseline.update` belongs to the architecture test,
 * which runs the same checks together. A run here reads the ledger the definition names, so no
 * argument is needed for it.
 */
private fun Result<Any?>.againstBaseline(
    architecture: Architecture,
    processor: ArchitectureProcessor<*, *>,
    walk: ProjectWalk,
    environment: BaselineEnvironment,
): Result<Any?> {
    val baseline = architecture.baseline ?: return this
    val violations = fold(
        onSuccess = { produced ->
            val answer = (produced as? List<*>)?.takeIf { list -> list.all { it is Violation } }?.filterIsInstance<Violation>()
            when (declaresViolationList(processor)) {
                true -> answer
                false -> null
                // Undeclared: only a list that holds a violation says it is one.
                null -> answer?.takeIf { it.isNotEmpty() }
            }
        },
        onFailure = { cause -> (cause as? KatachiArchitectureAssertionError)?.violations },
    ) ?: return this
    return runCatching {
        val outcome = applyBaseline(
            baseline = baseline,
            projectRoot = walk.projectRoot,
            ran = listOf(CheckedViolations(checkNameOf(processor), violations)),
            unattributed = emptyList(),
            declared = true,
            mode = BaselineMode.Check,
            environment = environment,
        )
        if (outcome.violations.any { it.severity == Severity.Error }) {
            throw KatachiArchitectureAssertionError(outcome.violations, DEFAULT_MAX_VIOLATIONS, walk.projectRoot, outcome.trailer)
        }
        HeldBack(outcome.violations, outcome.trailer)
    }
}

/**
 * One element of a processor's answer, as a line. A [Violation] is written as its report's first
 * line, with the path as a `file:///...` URI; anything else through `toString`.
 */
private fun lineOf(element: Any?, projectRoot: String?): String =
    if (element is Violation) {
        "[${element.label}] ${displayPath(projectRoot, element.path)}"
    } else {
        element.toString()
    }

/**
 * Holds a processor together with its own `Args` type, after the type has been captured.
 *
 * The registry is a `Map<String, Class<*>>`, so what comes back out of it is an
 * `ArchitectureProcessor<*, *>` and the `Args` type is gone. Kotlin gives it back for the
 * length of one call: passing the star-projected processor to [erase] captures both type
 * parameters, and inside this class the serializer, the decoded arguments and `process` are
 * the same `Args` again -- checked by the compiler rather than asserted with a cast.
 */
private class ErasedProcessor<Args, R>(
    private val processor: ArchitectureProcessor<Args, R>,
) {
    fun run(
        base: ArchitectureProcessContext<*>,
        rawArgs: Map<String, String>,
        onLog: (String) -> Unit,
    ): Result<R> {
        val decodedArgs = decodeFromStringMap(processor.argsSerializer, rawArgs)
        return processor.process(base.withArgs(decodedArgs, onLog))
    }
}

/** Captures [processor]'s type parameters so its arguments can be decoded without a cast. */
private fun <Args, R> erase(
    processor: ArchitectureProcessor<Args, R>,
): ErasedProcessor<Args, R> = ErasedProcessor(processor)
