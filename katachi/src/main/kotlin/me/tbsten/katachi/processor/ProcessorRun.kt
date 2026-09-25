package me.tbsten.katachi.processor

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.fs.internal.RealFileSystem

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

    // `rawArgs` travels on the context as well as being decoded into each processor's own Args:
    // a processor whose vocabulary differs per role -- a template's parameters -- has no fixed
    // set of fields to declare them as. `withArgs` carries it to every processor of the run.
    //
    // Built before the check rather than after it, because the check asks the processors what
    // this run made legal and they read that off the context. Nothing is walked by building it:
    // the walk behind it is `by lazy` and no processor has run yet.
    val base = RealArchitectureProcessContext(architecture, Unit, fileSystem, rawArgs = rawArgs)

    checkNoUnknownArgs(selected, base, rawArgs)

    data class Entry(val key: String, val result: Result<Any?>)

    val outcomes = selected.map { (key, processor) ->
        // A throw and a `Result.failure` land in the same place: on the command line, "could
        // not do the job" and "did the job, and the answer is no" both fail the run.
        val result = runCatching {
            erase(processor).run(base, rawArgs) { message -> out("  [$key] $message") }.getOrThrow()
        }
        Entry(key, result)
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
            onSuccess = { produced ->
                out("[OK] ${outcome.key}")
                // One element per line. A collection printed through `toString` arrives as a
                // single bracketed line.
                when (produced) {
                    Unit -> Unit
                    is Collection<*> -> produced.forEach { element -> out(element.toString()) }
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
