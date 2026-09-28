package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.ArchitectureScopeImpl
import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.GroupScopeImpl
import me.tbsten.katachi.dsl.LayoutDeclarationScope
import me.tbsten.katachi.dsl.LayoutDeclarationScopeImpl
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.MetadataScope
import me.tbsten.katachi.dsl.RoleScopeImpl

/**
 * The metadata of one declaration, as it was written.
 *
 * Only what was set is here. A key nobody wrote is absent rather than holding some default,
 * so the reader decides what an absent key means — `role[Title] ?: role.name` and
 * `group[Documented] ?: true` are that decision, made where it belongs. Giving [MetadataKey]
 * a default would split keys into two kinds and move those decisions into the mechanism.
 */
internal class MetadataValues(
    /**
     * Internal, not private: [me.tbsten.katachi.dsl.internal.mergedWith] walks both sides of a
     * merge key by key, which `get` alone cannot do without knowing every key in advance.
     */
    internal val values: Map<MetadataKey<*>, Any>,
) {
    operator fun <T : Any> get(key: MetadataKey<T>): T? = values[key]?.let { key.valueOf(it) }

    /**
     * Everything written here, in the order it was written.
     *
     * The one reading [get] cannot do: a [DocumentSection] is found by what its value is rather
     * than through a key the reader already holds, and the order the DSL wrote the sections in is
     * the order the generated page shows them in.
     */
    fun writtenValues(): List<Any> = values.values.toList()

    override fun toString(): String = "MetadataValues(${values.size})"

    companion object {
        /** The empty instance, for a [me.tbsten.katachi.dsl.LayoutEntry] nothing was ever attached to. */
        val EMPTY: MetadataValues = MetadataValues(emptyMap())
    }
}

/**
 * Collects metadata while a `"Role" { }` or `"group".group { }` block runs.
 *
 * Writing `null` removes the key instead of storing it, which is what keeps "absent" the one
 * way of saying "not written": `summary = null` after a `summary = "..."` leaves the role
 * exactly as it would have been without either line.
 */
internal class MetadataBuilder {
    private val values = mutableMapOf<MetadataKey<*>, Any>()

    operator fun <T : Any> get(key: MetadataKey<T>): T? = values[key]?.let { key.valueOf(it) }

    operator fun <T : Any> set(key: MetadataKey<T>, value: T?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    fun build(): MetadataValues = MetadataValues(values.toMap())
}

/**
 * The scope's collector. Matched rather than cast: [MetadataScope] is sealed, so the compiler
 * checks that every scope katachi hands to a block is covered, and a new one cannot be added
 * without this being updated.
 */
internal fun MetadataScope.metadataBuilder(): MetadataBuilder = when (this) {
    is RoleScopeImpl -> metadata
    is GroupScopeImpl -> metadata
    is ArchitectureScopeImpl -> metadata
    is LayoutDeclarationScopeImpl -> metadata
}

/**
 * Runs [block] as `.metadata { }` on this node's own builder. Shared by [me.tbsten.katachi.dsl.LayoutFile],
 * [me.tbsten.katachi.dsl.LayoutDirectory] and [me.tbsten.katachi.dsl.LayoutModule], which all attach to a
 * node's [LayoutNode.metadata] the same way.
 */
internal fun LayoutNode.attachMetadata(block: LayoutDeclarationScope.() -> Unit) {
    LayoutDeclarationScopeImpl(metadata).block()
}

/**
 * Merges the metadata of two declarations that turned out to be the same [me.tbsten.katachi.dsl.LayoutEntry]:
 * a role that declared one path twice, whether as two `/` chains that happen to share it or as
 * the same key written in two `layout { }` blocks.
 *
 * A key present on only one side is kept as is. A key present on both is kept once when the two
 * values are `==`, and is a declaration error otherwise -- [onConflict] decides what that error
 * is, since the caller knows the path, the role and where each declaration was written and this
 * function does not.
 */
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
