package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.internal.ArgValueParsing

internal sealed interface TemplateParameterType<out T> {
    val declaredWith: String

    val label: String

    val acceptedValues: List<String>

    val acceptedDescription: String?

    fun parse(raw: String): ParsedArgValue<T>

    fun standIn(name: String): T

    fun exampleValue(default: Any?): String

    data object StringType : TemplateParameterType<String> {
        override val declaredWith: String = "stringParameter()"
        override val label: String = "String"
        override val acceptedValues: List<String> = emptyList()
        override val acceptedDescription: String? = null
        override fun parse(raw: String): ParsedArgValue<String> = ParsedArgValue.Parsed(raw)

        // The name rather than an empty string, so that a body doing something with the value
        // keeps working long enough for every other missing name to be collected too.
        override fun standIn(name: String): String = name
        override fun exampleValue(default: Any?): String = default?.toString() ?: "<value>"
    }

    data object BooleanType : TemplateParameterType<Boolean> {
        override val declaredWith: String = "booleanParameter()"
        override val label: String = "Boolean"
        override val acceptedValues: List<String> = listOf("true", "false")
        override val acceptedDescription: String = "true or false"
        override fun parse(raw: String): ParsedArgValue<Boolean> =
            ArgValueParsing.boolean(raw)?.let { ParsedArgValue.Parsed(it) }
                ?: ParsedArgValue.Invalid(InvalidArgReason.NotAValue)
        override fun standIn(name: String): Boolean = false
        override fun exampleValue(default: Any?): String = acceptedValues.first()
    }

    data object IntType : TemplateParameterType<Int> {
        override val declaredWith: String = "intParameter()"
        override val label: String = "Int"
        override val acceptedValues: List<String> = emptyList()
        override val acceptedDescription: String = "a whole number"
        override fun parse(raw: String): ParsedArgValue<Int> {
            ArgValueParsing.int(raw)?.let { return ParsedArgValue.Parsed(it) }
            val reason = if (ArgValueParsing.isOutOfIntRange(raw)) {
                InvalidArgReason.OutOfRange
            } else {
                InvalidArgReason.NotAValue
            }
            return ParsedArgValue.Invalid(reason)
        }
        override fun standIn(name: String): Int = 0
        override fun exampleValue(default: Any?): String = default?.toString() ?: "0"
    }

    // entries is never empty: enumParameter() refuses an empty enum earlier.
    class EnumType<E : Enum<E>>(val entries: List<E>) : TemplateParameterType<E> {
        override val declaredWith: String = "enumParameter()"
        override val label: String = entries.first().declaringJavaClass.simpleName
        override val acceptedValues: List<String> = entries.map { it.name }
        override val acceptedDescription: String = acceptedValues.joinAsAlternatives()
        override fun parse(raw: String): ParsedArgValue<E> =
            ArgValueParsing.enumIndex(acceptedValues, raw)?.let { ParsedArgValue.Parsed(entries[it]) }
                ?: ParsedArgValue.Invalid(InvalidArgReason.NotAValue)
        override fun standIn(name: String): E = entries.first()
        override fun exampleValue(default: Any?): String = acceptedValues.first()
        override fun toString(): String = "EnumType($label)"
    }
}

private fun List<String>.joinAsAlternatives(): String =
    if (size <= 1) joinToString() else dropLast(1).joinToString(", ") + " or " + last()

internal sealed interface ParsedArgValue<out T> {
    data class Parsed<out T>(val value: T) : ParsedArgValue<T>
    data class Invalid(val reason: InvalidArgReason) : ParsedArgValue<Nothing>
}

internal enum class InvalidArgReason {
    NotAValue,

    OutOfRange,
}

internal class InvalidTemplateValue(
    val name: String,
    val raw: String,
    val type: TemplateParameterType<*>,
    val reason: InvalidArgReason,
    val default: Any?,
    val parameterDeclaredAt: DeclarationSite,
) {
    override fun toString(): String = "InvalidTemplateValue($name=\"$raw\")"
}
