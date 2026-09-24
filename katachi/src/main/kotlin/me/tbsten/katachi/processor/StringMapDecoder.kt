package me.tbsten.katachi.processor

import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi

/**
 * Reads a `@Serializable` class out of the flat `key=value` map a `--arg` line produces.
 *
 * `kotlinx-serialization-properties` would have done most of this, and was not used: it
 * ignores keys it does not know, and noticing a mistyped `--arg` key is the point (see
 * `checkNoUnknownArgs`).
 *
 * **A comma splits only when the receiving field is a `List<T>` or a `Set<T>`.** `--arg
 * tags=a,b` read into a `String` field is the two-character-longer string `"a,b"`, which is
 * also the way out when a value has to contain a comma. There is no escape syntax.
 *
 * `Map` fields and nested `@Serializable` classes are refused rather than half-read: one
 * `--arg` line has no way to express nesting.
 *
 * ## Example 1: decode one processor's arguments
 * ```kt
 * @Serializable
 * data class Args(val roleName: String, val tags: List<String> = emptyList())
 *
 * decodeFromStringMap(Args.serializer(), mapOf("roleName" to "GetUser", "tags" to "a,b")) shouldBe
 *     Args(roleName = "GetUser", tags = listOf("a", "b"))
 * ```
 */
@ExperimentalKatachiApi
public class StringMapDecoder(
    @InternalKatachiApi
    public val values: Map<String, String>,
) : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private val decodedIndices = mutableSetOf<Int>()
    private var currentValue: String? = null

    // The Args class itself is one structure; a second one is a nested @Serializable, which
    // would otherwise be read out of the parent's flat namespace without anyone noticing.
    private var depth = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        for (index in 0 until descriptor.elementsCount) {
            if (index in decodedIndices) continue
            val name = descriptor.getElementName(index)
            if (name in values) {
                decodedIndices += index
                currentValue = values.getValue(name)
                return index
            }
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        when (descriptor.kind) {
            StructureKind.LIST -> SplitDecoder(splitArgValue(value()))
            StructureKind.MAP -> throw KatachiUnsupportedProcessorArgException(
                serialName = descriptor.serialName,
                kind = descriptor.kind.toString(),
            )

            else ->
                if (depth > 0) {
                    throw KatachiUnsupportedProcessorArgException(
                        serialName = descriptor.serialName,
                        kind = descriptor.kind.toString(),
                    )
                } else {
                    depth++
                    this
                }
        }

    override fun endStructure(descriptor: SerialDescriptor) {
        depth--
    }

    private fun value(): String = currentValue
        ?: throw SerializationException("No processor argument is being decoded.")

    override fun decodeString(): String = value()
    override fun decodeInt(): Int = ArgValues.int(value())
    override fun decodeLong(): Long = ArgValues.long(value())
    override fun decodeShort(): Short = ArgValues.short(value())
    override fun decodeByte(): Byte = ArgValues.byte(value())
    override fun decodeFloat(): Float = ArgValues.float(value())
    override fun decodeDouble(): Double = ArgValues.double(value())
    override fun decodeBoolean(): Boolean = ArgValues.boolean(value())
    override fun decodeChar(): Char = ArgValues.char(value())

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        ArgValues.enumIndex(enumDescriptor, value())

    // Leaving a field out is how a default value is asked for, so nothing is ever read as null.
    override fun decodeNotNullMark(): Boolean = true
}

/** `a,b,c` handed out one element at a time. An empty string is an empty collection. */
private class SplitDecoder(private val parts: List<String>) : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    private var index = 0

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = parts.size

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
        if (index < parts.size) index++ else CompositeDecoder.DECODE_DONE

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        throw KatachiUnsupportedProcessorArgException(
            serialName = descriptor.serialName,
            kind = descriptor.kind.toString(),
        )

    private fun value(): String = parts[index - 1]

    override fun decodeString(): String = value()
    override fun decodeInt(): Int = ArgValues.int(value())
    override fun decodeLong(): Long = ArgValues.long(value())
    override fun decodeShort(): Short = ArgValues.short(value())
    override fun decodeByte(): Byte = ArgValues.byte(value())
    override fun decodeFloat(): Float = ArgValues.float(value())
    override fun decodeDouble(): Double = ArgValues.double(value())
    override fun decodeBoolean(): Boolean = ArgValues.boolean(value())
    override fun decodeChar(): Char = ArgValues.char(value())

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        ArgValues.enumIndex(enumDescriptor, value())

    override fun decodeNotNullMark(): Boolean = true
}

private fun splitArgValue(raw: String): List<String> =
    if (raw.isEmpty()) emptyList() else raw.split(',')

/**
 * The one place a `--arg` string becomes a value.
 *
 * Shared between the two decoders through a function rather than a common base class: a
 * `public` [StringMapDecoder] cannot extend an `internal` type, and making the base public
 * would put a class nobody needs on the published surface.
 *
 * Every failure is a `SerializationException` so that `decodeFromStringMap` can turn it into a
 * katachi exception in one place. `"abc".toInt()` would throw `NumberFormatException`, which
 * is not one and would escape uncaught.
 */
internal object ArgValues {
    fun int(raw: String): Int = raw.toIntOrNull() ?: fail(raw, "Int")
    fun long(raw: String): Long = raw.toLongOrNull() ?: fail(raw, "Long")
    fun short(raw: String): Short = raw.toShortOrNull() ?: fail(raw, "Short")
    fun byte(raw: String): Byte = raw.toByteOrNull() ?: fail(raw, "Byte")
    fun float(raw: String): Float = raw.toFloatOrNull() ?: fail(raw, "Float")
    fun double(raw: String): Double = raw.toDoubleOrNull() ?: fail(raw, "Double")
    fun boolean(raw: String): Boolean = raw.toBooleanStrictOrNull() ?: fail(raw, "Boolean")
    fun char(raw: String): Char = raw.singleOrNull() ?: fail(raw, "Char")

    fun enumIndex(descriptor: SerialDescriptor, raw: String): Int =
        descriptor.getElementIndex(raw).takeIf { it != CompositeDecoder.UNKNOWN_NAME }
            ?: fail(raw, descriptor.serialName)

    private fun fail(raw: String, type: String): Nothing =
        throw SerializationException("Cannot read \"$raw\" as $type.")
}
