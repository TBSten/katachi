package me.tbsten.katachi.processor

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.internal.StringMapDecoder

/**
 * Decodes [values] into the type [deserializer] describes.
 *
 * **Keys the type does not declare are ignored here**, since [values] may hold keys meant for
 * another processor of the same run. A key no processor of the run declares is reported
 * separately, before any processor starts.
 *
 * **A comma splits only when the receiving field is a `List<T>` or a `Set<T>`.** `--arg
 * tags=a,b` read into a `String` field is the string `"a,b"`. There is no escape syntax.
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
