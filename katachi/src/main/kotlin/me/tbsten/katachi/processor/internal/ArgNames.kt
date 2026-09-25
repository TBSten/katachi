package me.tbsten.katachi.processor.internal

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
    context: ArchitectureProcessContext<*>,
): AcceptedArgNames {
    val names = mutableSetOf<String>()
    var dependsOnValues = false
    for ((_, processor) in selected) {
        names += declaredArgNames(processor)
        val undeclared = processor.undeclaredArgNames(context)
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
 * The union, and once for the whole run: judging per processor would reject
 * `--processor=docs,template --arg roleName=X`, because `docs` takes no arguments and would
 * call `roleName` unknown before `template` was ever reached.
 */
internal fun checkNoUnknownArgs(
    selected: List<Pair<String, ArchitectureProcessor<*, *>>>,
    context: ArchitectureProcessContext<*>,
    values: Map<String, String>,
) {
    val accepted = acceptedArgNames(selected, context)
    val unknown = values.keys - accepted.names
    if (unknown.isEmpty()) return

    throw KatachiUnknownProcessorArgException(
        unknown = unknown,
        known = accepted.names,
        knownDependsOnValues = accepted.dependsOnValues,
    )
}
