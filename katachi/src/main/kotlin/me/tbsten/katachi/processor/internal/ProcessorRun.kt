package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.BaselineEnvironment
import me.tbsten.katachi.check.internal.BaselineMode
import me.tbsten.katachi.check.internal.CheckedViolations
import me.tbsten.katachi.check.internal.applyBaseline
import me.tbsten.katachi.check.internal.checkNameOf
import me.tbsten.katachi.check.internal.resolveMaxViolations
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
import me.tbsten.katachi.processor.KatachiProcessorNotRunException
import me.tbsten.katachi.processor.KatachiProcessorTypeException
import me.tbsten.katachi.processor.decodeFromStringMap

internal data class ProcessorRunSummary(
    val succeeded: Int,
    val failed: Int,
    val failures: List<ProcessorFailure> = emptyList(),
)

internal class ProcessorFailure(val key: String, val cause: Throwable) {
    override fun toString(): String = "ProcessorFailure($key, $cause)"
}

// INSTANCE and the constructor are made accessible: a generated caller outside the class's module cannot reach
// non-public declarations.
internal fun instantiateProcessor(type: Class<*>): ArchitectureProcessor<*, *> {
    val instance = runCatching {
        type.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
    }.recoverCatching {
        type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
    }.getOrElse { cause -> throw KatachiProcessorNotInstantiableException(type, cause) }

    return instance as? ArchitectureProcessor<*, *> ?: throw KatachiProcessorTypeException(type)
}

// Every key is resolved, every processor instantiated and --arg names checked before any of them runs.
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

    // Asked once, and a processor that cannot answer is that processor's failure: its message is
    // printed under `[FAILED]` like any other, rather than ending the JVM with a stack trace. The
    // run's `--arg` names cannot be judged without every answer, so nothing runs then.
    val answers = undeclaredArgNamesOf(selected, contexts)
    val unanswered = answers.filterValues { it.isFailure }.keys
    if (unanswered.isEmpty()) {
        checkNoUnknownArgs(selected, contexts, rawArgs, argsFor, answers.mapValues { it.value.getOrThrow() })
    }

    data class Entry(val key: String, val result: Result<Any?>)

    val outcomes = selected.map { (key, processor) ->
        if (unanswered.isNotEmpty()) {
            val failure = answers.getValue(key).exceptionOrNull()
                ?: KatachiProcessorNotRunException(key, unanswered.toList())
            return@map Entry(key, Result.failure(failure))
        }
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

    val failures = outcomes.mapNotNull { outcome ->
        outcome.result.exceptionOrNull()?.let { cause -> ProcessorFailure(outcome.key, cause) }
    }
    return ProcessorRunSummary(succeeded = succeeded, failed = failed, failures = failures)
}

private class HeldBack(val violations: List<Violation>, val trailer: List<String>)

// Only compared, never updated: -Dkatachi.baseline.update belongs to the architecture test.
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
            // What a `katachi<Key>` task prints is what the user reads, so the budget is resolved
            // here the way `assert()` resolves it: the Gradle plugin hands `-Dkatachi.maxViolations`
            // on to this JVM too.
            val budget = resolveMaxViolations(explicit = null, environment.systemProperty, environment.standardError)
            throw KatachiArchitectureAssertionError(outcome.violations, budget, walk.projectRoot, outcome.trailer)
        }
        HeldBack(outcome.violations, outcome.trailer)
    }
}

private fun lineOf(element: Any?, projectRoot: String?): String =
    if (element is Violation) {
        "[${element.label}] ${displayPath(projectRoot, element.path)}"
    } else {
        element.toString()
    }

// Passing the star-projected processor to erase captures both type parameters, so no cast is needed.
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

private fun <Args, R> erase(
    processor: ArchitectureProcessor<Args, R>,
): ErasedProcessor<Args, R> = ErasedProcessor(processor)
