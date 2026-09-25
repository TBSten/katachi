package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.ArchitectureScopeImpl
import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.GroupScopeImpl
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
internal class MetadataValues(private val values: Map<MetadataKey<*>, Any>) {
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
}
