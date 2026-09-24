package me.tbsten.katachi.processor

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import me.tbsten.katachi.ExperimentalKatachiApi

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

/** Every `--arg` name some processor of [processors] knows about. */
internal fun knownArgNames(processors: List<ArchitectureProcessor<*, *>>): Set<String> =
    processors.flatMapTo(mutableSetOf()) { processor ->
        val descriptor = processor.argsSerializer.descriptor
        (0 until descriptor.elementsCount).map(descriptor::getElementName)
    }

/**
 * Refuses a run in which some `--arg` key belongs to none of the chosen processors.
 *
 * The union, and once for the whole run: judging per processor would reject
 * `--processor=docs,template --arg roleName=X`, because `docs` takes no arguments and would
 * call `roleName` unknown before `template` was ever reached.
 */
internal fun checkNoUnknownArgs(
    processors: List<ArchitectureProcessor<*, *>>,
    values: Map<String, String>,
) {
    val known = knownArgNames(processors)
    val unknown = values.keys - known
    if (unknown.isNotEmpty()) {
        throw KatachiUnknownProcessorArgException(unknown = unknown, known = known)
    }
}
