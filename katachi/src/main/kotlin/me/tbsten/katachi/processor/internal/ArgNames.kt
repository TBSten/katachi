package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.KatachiUnknownProcessorArgException

/**
 * Every `--arg` name [processor]'s own `argsSerializer` declares a field for.
 *
 * The whole of what a processor accepts by fixed field; a processor may add to this by answering
 * [ArchitectureProcessor.undeclaredArgNames] -- see [acceptedArgNames].
 */
internal fun declaredArgNames(processor: ArchitectureProcessor<*, *>): Set<String> {
    val descriptor = processor.argsSerializer.descriptor
    return (0 until descriptor.elementsCount).mapTo(mutableSetOf(), descriptor::getElementName)
}

/**
 * Every `--arg` name this run accepts: every selected processor's [declaredArgNames], plus
 * whatever each one answers [ArchitectureProcessor.undeclaredArgNames] with.
 *
 * Every processor is asked, with no build-script step in between: a processor that has
 * something to say about its own `--arg` vocabulary says it here, and the run accepts it.
 * Nothing catches what [ArchitectureProcessor.undeclaredArgNames] throws -- see that member's
 * own KDoc for why a processor that cannot answer should throw rather than answer "nothing".
 */
private fun acceptedArgNames(
    selected: List<Pair<String, ArchitectureProcessor<*, *>>>,
    contexts: Map<String, ArchitectureProcessContext<*>>,
    answered: Map<String, Set<String>>?,
): AcceptedArgNames {
    val names = mutableSetOf<String>()
    var dependsOnValues = false
    for ((key, processor) in selected) {
        names += declaredArgNames(processor)
        val undeclared = answered?.get(key) ?: processor.undeclaredArgNames(contexts.getValue(key))
        if (undeclared.isNotEmpty()) dependsOnValues = true
        names += undeclared
    }
    return AcceptedArgNames(names, dependsOnValues)
}

/**
 * [names] a run accepts, and whether any of them came from
 * [ArchitectureProcessor.undeclaredArgNames] -- which answers for this run's values, so other
 * values might have accepted more.
 */
private class AcceptedArgNames(val names: Set<String>, val dependsOnValues: Boolean)

/**
 * Refuses a run in which some `--arg` key belongs to none of the chosen processors.
 *
 * [values] -- the `--arg`s every processor is given -- against the union, and once for the whole
 * run: judging per processor would reject `--processor=docs,template --arg roleName=X`, because
 * `docs` takes no arguments and would call `roleName` unknown before `template` was ever
 * reached. Each entry of [argsFor] against its own processor alone, since only that processor is
 * given it -- the same answer a run of that processor by itself would give.
 *
 * @param contexts each selected processor's context, by key, carrying the values it is given.
 * @param answered each processor's [ArchitectureProcessor.undeclaredArgNames], by key, when the
 *   caller has already asked -- see [undeclaredArgNamesOf]. Asked here when `null`.
 */
internal fun checkNoUnknownArgs(
    selected: List<Pair<String, ArchitectureProcessor<*, *>>>,
    contexts: Map<String, ArchitectureProcessContext<*>>,
    values: Map<String, String>,
    argsFor: Map<String, Map<String, String>> = emptyMap(),
    answered: Map<String, Set<String>>? = null,
) {
    val accepted = acceptedArgNames(selected, contexts, answered)
    throwIfUnknown(values.keys - accepted.names, accepted)

    for (entry in selected) {
        val own = argsFor[entry.first] ?: continue
        val acceptedByOne = acceptedArgNames(listOf(entry), contexts, answered)
        throwIfUnknown(own.keys - values.keys - acceptedByOne.names, acceptedByOne)
    }
}

/**
 * Asks every processor of [selected] for its [ArchitectureProcessor.undeclaredArgNames] once, by key.
 *
 * A processor that throws answers with its failure rather than failing the run from here: what it
 * threw is the processor's own problem -- a template that cannot be read, a capture named like a
 * parameter -- and the run reports it as that processor's `[FAILED]`, not as a stack trace.
 */
internal fun undeclaredArgNamesOf(
    selected: List<Pair<String, ArchitectureProcessor<*, *>>>,
    contexts: Map<String, ArchitectureProcessContext<*>>,
): Map<String, Result<Set<String>>> =
    selected.associate { (key, processor) ->
        key to catching { processor.undeclaredArgNames(contexts.getValue(key)) }
    }

/** [checkNoUnknownArgs] for a run in which every processor reads the same [context]. */
internal fun checkNoUnknownArgs(
    selected: List<Pair<String, ArchitectureProcessor<*, *>>>,
    context: ArchitectureProcessContext<*>,
    values: Map<String, String>,
): Unit = checkNoUnknownArgs(selected, selected.associate { (key, _) -> key to context }, values)

private fun throwIfUnknown(unknown: Set<String>, accepted: AcceptedArgNames) {
    if (unknown.isEmpty()) return

    throw KatachiUnknownProcessorArgException(
        unknown = unknown,
        known = accepted.names,
        knownDependsOnValues = accepted.dependsOnValues,
    )
}
