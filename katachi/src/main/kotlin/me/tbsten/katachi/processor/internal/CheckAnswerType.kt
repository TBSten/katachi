package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * Whether the class of [processor] declares its answer as a `List` of [Violation] -- which is
 * what makes a processor a check -- or `null` when its declaration does not say, because the
 * answer is a type parameter.
 *
 * Read off the declaration rather than the answer, because an empty list is a list of anything:
 * a processor answering with no names would otherwise be compared with the baseline, and fail
 * for want of a baseline file it never needed.
 */
internal fun declaresViolationList(processor: ArchitectureProcessor<*, *>): Boolean? {
    val answer = answerTypeOf(processor::class.java) ?: return null
    val list = answer as? ParameterizedType ?: return false
    val raw = list.rawType as? Class<*> ?: return false
    if (!List::class.java.isAssignableFrom(raw)) return false
    val element = list.actualTypeArguments.singleOrNull() ?: return false
    val bound = (element as? WildcardType)?.upperBounds?.singleOrNull() ?: element
    val elementClass = (bound as? ParameterizedType)?.rawType ?: bound
    return (elementClass as? Class<*>)?.let { Violation::class.java.isAssignableFrom(it) } ?: false
}

/** The `R` of `ArchitectureProcessor<Args, R>` as [type] declares it, or `null` if it is not a class. */
private fun answerTypeOf(type: Type): Type? {
    var current: Class<*>? = type as? Class<*> ?: return null
    while (current != null) {
        for (supertype in current.genericInterfaces) answerOfInterface(supertype)?.let { return it.takeUnlessVariable() }
        val superclass = current.genericSuperclass
        answerOfInterface(superclass)?.let { return it.takeUnlessVariable() }
        current = current.superclass
    }
    return null
}

/** The answer type [supertype] fixes, looking through interfaces that extend the processor ones. */
private fun answerOfInterface(supertype: Type?): Type? {
    val parameterized = supertype as? ParameterizedType
    val raw = (parameterized?.rawType ?: supertype) as? Class<*> ?: return null
    when (raw) {
        ArchitectureProcessorNoArg::class.java -> return parameterized?.actualTypeArguments?.getOrNull(0)
        ArchitectureProcessor::class.java -> return parameterized?.actualTypeArguments?.getOrNull(1)
    }
    if (!raw.isInterface) return null
    return raw.genericInterfaces.firstNotNullOfOrNull(::answerOfInterface)
}

/** `null` for a type parameter, which says nothing about what the answer is. */
private fun Type.takeUnlessVariable(): Type? = takeUnless { it is java.lang.reflect.TypeVariable<*> }
