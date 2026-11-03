package me.tbsten.katachi.processor.internal

import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import me.tbsten.katachi.internal.ArgValueParsing
import me.tbsten.katachi.processor.KatachiUnsupportedProcessorArgException

// kotlinx-serialization-properties is not used: it ignores unknown keys, and noticing a mistyped --arg key is
// the point.
// A comma splits only when the receiving field is a List or a Set.
internal class StringMapDecoder(
    val values: Map<String, String>,
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

// Every failure must be a SerializationException (not NumberFormatException) so decodeFromStringMap can convert
// it in one place.
private object ArgValues {
    fun int(raw: String): Int = ArgValueParsing.int(raw) ?: fail(raw, "Int")
    fun long(raw: String): Long = ArgValueParsing.long(raw) ?: fail(raw, "Long")
    fun short(raw: String): Short = ArgValueParsing.short(raw) ?: fail(raw, "Short")
    fun byte(raw: String): Byte = ArgValueParsing.byte(raw) ?: fail(raw, "Byte")
    fun float(raw: String): Float = ArgValueParsing.float(raw) ?: fail(raw, "Float")
    fun double(raw: String): Double = ArgValueParsing.double(raw) ?: fail(raw, "Double")
    fun boolean(raw: String): Boolean = ArgValueParsing.boolean(raw) ?: fail(raw, "Boolean")
    fun char(raw: String): Char = ArgValueParsing.char(raw) ?: fail(raw, "Char")

    // An enum names its accepted spellings in its descriptor, so the message lists them.
    fun enumIndex(descriptor: SerialDescriptor, raw: String): Int {
        val names = (0 until descriptor.elementsCount).map(descriptor::getElementName)
        return ArgValueParsing.enumIndex(names, raw)
            ?: throw SerializationException(
                """Cannot read "$raw" as ${descriptor.serialName}. Accepted values: """ +
                    names.joinToString(", ", postfix = "."),
            )
    }

    private fun fail(raw: String, type: String): Nothing =
        throw SerializationException("Cannot read \"$raw\" as $type.")
}
