package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.ArchitectureScopeImpl
import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.GradleRoleScopeImpl
import me.tbsten.katachi.dsl.GradleScopeImpl
import me.tbsten.katachi.dsl.GroupScopeImpl
import me.tbsten.katachi.dsl.LayoutDeclarationScope
import me.tbsten.katachi.dsl.LayoutDeclarationScopeImpl
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.RoleScopeImpl

// An absent key means not written; readers decide what that means (`role[Title] ?: role.name`), so MetadataKey has
// no default.
internal class MetadataValues(
    // Internal so mergedWith can walk both sides key by key.
    internal val values: Map<MetadataKey<*>, Any>,
) {
    operator fun <T : Any> get(key: MetadataKey<T>): T? = values[key]?.let { key.valueOf(it) }

    // Write order is the order generated pages show document sections.
    fun writtenValues(): List<Any> = values.values.toList()

    override fun toString(): String = "MetadataValues(${values.size})"

    companion object {
        val EMPTY: MetadataValues = MetadataValues(emptyMap())
    }
}

// Writing null removes the key so that absent is the only way of saying not written.
internal class MetadataBuilder {
    private val values = mutableMapOf<MetadataKey<*>, Any>()

    operator fun <T : Any> get(key: MetadataKey<T>): T? = values[key]?.let { key.valueOf(it) }

    operator fun <T : Any> set(key: MetadataKey<T>, value: T?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    fun build(): MetadataValues = MetadataValues(values.toMap())
}

// Exhaustive match over the sealed MetadataScope: a new scope cannot be added without updating this.
internal fun MetadataScope.metadataBuilder(): MetadataBuilder = when (this) {
    is RoleScopeImpl -> metadata
    is GroupScopeImpl -> metadata
    is ArchitectureScopeImpl -> metadata
    is LayoutDeclarationScopeImpl -> metadata
    is GradleScopeImpl -> group.metadataBuilder()
    is GradleRoleScopeImpl -> role.metadataBuilder()
}

internal fun LayoutNode.attachMetadata(block: LayoutDeclarationScope.() -> Unit) {
    LayoutDeclarationScopeImpl(metadata).block()
}

// Present on both sides: kept once when `==`, otherwise onConflict decides the error.
internal inline fun MetadataValues.mergedWith(
    other: MetadataValues,
    onConflict: (key: MetadataKey<*>, first: Any, second: Any) -> Nothing,
): MetadataValues {
    if (other.values.isEmpty()) return this
    if (values.isEmpty()) return other
    val merged = LinkedHashMap<MetadataKey<*>, Any>(values)
    for ((key, secondValue) in other.values) {
        val firstValue = merged[key]
        when {
            firstValue == null -> merged[key] = secondValue
            firstValue == secondValue -> Unit
            else -> onConflict(key, firstValue, secondValue)
        }
    }
    return MetadataValues(merged)
}
