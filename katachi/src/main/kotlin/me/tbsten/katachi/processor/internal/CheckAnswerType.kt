package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

// Read off the declaration, not the answer: an empty list is a list of anything, and would be compared with a
// baseline file it never needed.
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

private fun Type.takeUnlessVariable(): Type? = takeUnless { it is java.lang.reflect.TypeVariable<*> }
