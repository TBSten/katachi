package me.tbsten.katachi.processor

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.catching

/**
 * Decodes [values] into the type [deserializer] describes.
 *
 * **Unknown keys are not judged here.** [values] may hold keys meant for another processor of
 * the same run; [StringMapDecoder] only looks at the names its own descriptor carries, so the
 * rest goes past untouched. Deciding that a key belongs to nobody is `checkNoUnknownArgs`'s
 * job, and it is done once for the whole run.
 *
 * ## Example 1: read one processor's arguments out of the run's `--arg` map
 * ```kt
 * @Serializable
 * data class Args(val roleName: String, val dryRun: Boolean = false)
 *
 * decodeFromStringMap(
 *     Args.serializer(),
 *     mapOf("roleName" to "GetUser", "outDir" to "build/docs"),
 * ) shouldBe Args(roleName = "GetUser")
 * ```
 *
 * @throws KatachiInvalidProcessorArgException when a value cannot be read as the field's type,
 *   or a field with no default was not given one.
 * @throws KatachiUnsupportedProcessorArgException when the type holds a `Map` field or a
 *   nested `@Serializable` class.
 */
@ExperimentalKatachiApi
public fun <T> decodeFromStringMap(
    deserializer: DeserializationStrategy<T>,
    values: Map<String, String>,
): T = try {
    deserializer.deserialize(StringMapDecoder(values))
} catch (cause: SerializationException) {
    throw KatachiInvalidProcessorArgException(
        serialName = deserializer.descriptor.serialName,
        cause = cause,
    )
}

/**
 * Every `--arg` name [processor]'s own `argsSerializer` declares a field for.
 *
 * The whole of what a processor accepts by default: anything else has to be raised a hand for,
 * both in the build script and by the processor itself -- see [acceptedArgNames].
 */
internal fun declaredArgNames(processor: ArchitectureProcessor<*, *>): Set<String> {
    val descriptor = processor.argsSerializer.descriptor
    return (0 until descriptor.elementsCount).mapTo(mutableSetOf(), descriptor::getElementName)
}

/**
 * Every `--arg` name this run accepts: two sources, and both have to agree.
 *
 * The first is every selected processor's [declaredArgNames], which is fixed at compile time.
 * The second is what a processor answers [ArchitectureProcessor.undeclaredArgNames] with, and it
 * counts **only** for a key the build script named in `acceptsUndeclaredArgs` -- the flag is the
 * ceiling and the processor's answer is the set below it, so a processor that would take anything
 * still takes nothing until a build file said so.
 */
internal fun acceptedArgNames(
    selected: List<Pair<String, ArchitectureProcessor<*, *>>>,
    acceptsUndeclaredArgs: Set<String>,
    context: ArchitectureProcessContext<*>,
): Set<String> = buildSet {
    for ((key, processor) in selected) {
        addAll(declaredArgNames(processor))
        // Asked without a net, unlike the hint below: this processor's answer is what the run
        // accepts, so a processor that cannot answer has nothing to say about which `--arg` keys
        // are typos. A template asked about a role name no role answers to is exactly that, and
        // its own exception names the roles that do have one.
        if (key in acceptsUndeclaredArgs) addAll(processor.undeclaredArgNames(context))
    }
}

/**
 * Refuses a run in which some `--arg` key belongs to none of the chosen processors.
 *
 * The union, and once for the whole run: judging per processor would reject
 * `--processor=docs,template --arg roleName=X`, because `docs` takes no arguments and would
 * call `roleName` unknown before `template` was ever reached.
 *
 * A processor that did **not** raise its hand is still asked what it would have accepted, and the
 * answer goes into the message as a hint. It is never added to the accepted set: a run that would
 * have passed had the build script said so has to fail until the build script says so.
 */
internal fun checkNoUnknownArgs(
    selected: List<Pair<String, ArchitectureProcessor<*, *>>>,
    acceptsUndeclaredArgs: Set<String>,
    context: ArchitectureProcessContext<*>,
    values: Map<String, String>,
) {
    val known = acceptedArgNames(selected, acceptsUndeclaredArgs, context)
    val unknown = values.keys - known
    if (unknown.isEmpty()) return

    val notAllowed = selected
        .filterNot { (key, _) -> key in acceptsUndeclaredArgs }
        .associate { (key, processor) ->
            key to undeclaredArgNamesOrNone(processor, context).intersect(unknown)
        }
        .filterValues { it.isNotEmpty() }

    throw KatachiUnknownProcessorArgException(
        unknown = unknown,
        known = known,
        notAllowed = notAllowed,
    )
}

/**
 * Asks [processor] what it *would* have taken, and takes "nothing" for an answer.
 *
 * Only for the hint on an error that is already being thrown, and only of processors the build
 * script did **not** open up. Nothing depends on the answer, and the question is speculative --
 * the user may not have meant this processor at all -- so a processor that cannot answer simply
 * contributes no hint, rather than replacing the error the reader came for with its own.
 *
 * [acceptedArgNames] asks the same question of a processor that *was* opened up, and does not
 * catch: there the answer decides the run.
 *
 * `catching` rather than `runCatching`, so a broken classpath still ends the run.
 */
private fun undeclaredArgNamesOrNone(
    processor: ArchitectureProcessor<*, *>,
    context: ArchitectureProcessContext<*>,
): Set<String> = catching { processor.undeclaredArgNames(context) }.getOrDefault(emptySet())
