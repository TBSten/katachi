package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.KatachiUnknownProcessorArgException

internal fun declaredArgNames(processor: ArchitectureProcessor<*, *>): Set<String> {
    val descriptor = processor.argsSerializer.descriptor
    return (0 until descriptor.elementsCount).mapTo(mutableSetOf(), descriptor::getElementName)
}

// Nothing catches what undeclaredArgNames throws: a processor that cannot answer should throw, not answer "nothing".
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

private class AcceptedArgNames(val names: Set<String>, val dependsOnValues: Boolean)

// Judged once against the union of all processors: per processor, docs (no args) would call template's names unknown.
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

// A processor that throws answers with its failure instead of failing the run; it is reported as that
// processor's [FAILED].
internal fun undeclaredArgNamesOf(
    selected: List<Pair<String, ArchitectureProcessor<*, *>>>,
    contexts: Map<String, ArchitectureProcessContext<*>>,
): Map<String, Result<Set<String>>> =
    selected.associate { (key, processor) ->
        key to catching { processor.undeclaredArgNames(contexts.getValue(key)) }
    }

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
