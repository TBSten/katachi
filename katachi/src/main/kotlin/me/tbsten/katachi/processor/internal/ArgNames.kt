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
): Set<String> = buildSet {
    for ((_, processor) in selected) {
        addAll(declaredArgNames(processor))
        addAll(processor.undeclaredArgNames(context))
    }
}

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
    val known = acceptedArgNames(selected, context)
    val unknown = values.keys - known
    if (unknown.isEmpty()) return

    throw KatachiUnknownProcessorArgException(unknown = unknown, known = known)
}
