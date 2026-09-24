package me.tbsten.katachi.processor

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.RealFileSystem

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
 * the summary at the end reports how many of each there were.
 *
 * @param out where the report's lines go. Defaults to [println], but a spec passes
 *   `mutableListOf<String>::add` instead so the run can be asserted on without capturing standard
 *   output.
 */
internal fun runProcessors(
    architecture: Architecture,
    registry: Map<String, Class<*>>,
    processorKeys: List<String>,
    rawArgs: Map<String, String>,
    fileSystem: KatachiFileSystem = RealFileSystem(),
    out: (String) -> Unit = ::println,
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

    checkNoUnknownArgs(selected.map { it.second }, rawArgs)

    val base = RealArchitectureProcessContext(architecture, Unit, fileSystem)

    data class Outcome(val key: String, val result: Result<Any?>)

    val outcomes = selected.map { (key, processor) ->
        val result = runCatching {
            erase(processor).run(base, rawArgs) { message -> out("  [$key] $message") }
        }
        Outcome(key, result)
    }

    val succeeded = outcomes.count { it.result.isSuccess }
    val failed = outcomes.size - succeeded

    val separator = "=".repeat(40)
    out("")
    out(separator)
    out("[3/3] Katachi processor run: $succeeded succeeded, $failed failed")
    out("")

    for (outcome in outcomes) {
        outcome.result.fold(
            onSuccess = { result ->
                out("[OK] ${outcome.key}")
                if (result != Unit) out(result.toString())
            },
            onFailure = { cause ->
                out("[FAILED] ${outcome.key}")
                out("  ${cause.message}")
            },
        )
        out("")
    }
    out(separator)

    return ProcessorRunSummary(succeeded = succeeded, failed = failed)
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
private class ErasedProcessor<Args, Result>(
    private val processor: ArchitectureProcessor<Args, Result>,
) {
    fun run(
        base: ArchitectureProcessContext<*>,
        rawArgs: Map<String, String>,
        onLog: (String) -> Unit,
    ): Any? {
        val decodedArgs = decodeFromStringMap(processor.argsSerializer, rawArgs)
        return processor.process(base.withArgs(decodedArgs, onLog))
    }
}

/** Captures [processor]'s type parameters so its arguments can be decoded without a cast. */
private fun <Args, Result> erase(
    processor: ArchitectureProcessor<Args, Result>,
): ErasedProcessor<Args, Result> = ErasedProcessor(processor)
