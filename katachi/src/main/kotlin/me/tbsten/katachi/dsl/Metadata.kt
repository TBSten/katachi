package me.tbsten.katachi.dsl

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * A key a declaration can carry one value under.
 *
 * A key is an object, not a name: two keys are the same key only when they are the same
 * instance. That is what keeps reading type safe. [MetadataKey] carries `T` from the place
 * the key is declared to the processor that reads it, so a value written under a key can
 * only come back out as the type that key was declared with. A key spelled as a string or
 * as a property name would collide the moment two processors picked the same word for two
 * different types, and the mismatch would surface as a runtime failure far from either
 * declaration.
 *
 * Declare one key per word of vocabulary, in the processor that reads it, and give the
 * people writing the definition a property to set it through.
 *
 * ## Example 1: declare a key, write it in the DSL, read it off the role
 * ```kt
 * val Owner: MetadataKey<String> = metadata()
 * var RoleScope.owner: String? by Owner
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" { owner = "platform" }
 *     }
 * }
 * arch.allRoles.single()[Owner] shouldBe "platform"
 * ```
 *
 * ## Example 2: a key holding something other than a string
 * ```kt
 * val Since: MetadataKey<Int> = metadata()
 * var GroupScope.since: Int? by Since
 *
 * val arch = architecture {
 *     "domain".group { since = 2 }
 * }
 * arch.groups.single()[Since] shouldBe 2
 * ```
 */
@ExperimentalKatachiApi
public class MetadataKey<T : Any> internal constructor(
    private val type: Class<T>,
) {
    /**
     * Reads this key out of [thisRef], so that a key can be the delegate of a property.
     *
     * A member rather than an extension: an extension `getValue` would have to be imported
     * by name in every file declaring such a property, which is a strange thing to ask of
     * someone who only wrote `by Owner`.
     *
     * ## Example 1: give a key a property to be written through
     * ```kt
     * val Owner: MetadataKey<String> = metadata()
     * var RoleScope.owner: String? by Owner
     * ```
     */
    public operator fun getValue(thisRef: MetadataScope, property: KProperty<*>): T? =
        thisRef.metadataBuilder()[this]

    /**
     * Writes [value] under this key in [thisRef]. Writing `null` clears the key.
     *
     * ## Example 1: write a key from the DSL through its property
     * ```kt
     * val Owner: MetadataKey<String> = metadata()
     * var RoleScope.owner: String? by Owner
     *
     * val arch = architecture {
     *     "domain".group { "UseCase" { owner = "platform" } }
     * }
     * arch.allRoles.single()[Owner] shouldBe "platform"
     * ```
     */
    public operator fun setValue(thisRef: MetadataScope, property: KProperty<*>, value: T?) {
        thisRef.metadataBuilder()[this] = value
    }

    /**
     * Puts the type back on a value read out of untyped storage.
     *
     * Metadata is held as `Map<MetadataKey<*>, Any>`, so something has to turn `Any` back
     * into [T]. Going through the class this key was built from makes that a checked
     * conversion rather than an unchecked cast: a wrong value is caught here, at the key
     * that knows what it should be, instead of being handed out and failing later inside
     * whichever processor read it.
     *
     * It cannot actually fail, because every write goes through a [MetadataKey] too. That is
     * the whole argument for keys being objects rather than names, and this line is where it
     * is either true or not.
     *
     * Only the erased class is checked, so a `MetadataKey<List<String>>` verifies `List` and
     * not its element type. A processor would have to hand a key of its own to someone else
     * for that to matter.
     */
    internal fun valueOf(stored: Any): T = type.cast(stored)

    override fun toString(): String = "MetadataKey<${type.simpleName}>"
}

/**
 * A new key for values of the reified type.
 *
 * Every call returns a distinct key: identity is what tells two keys apart, so a key is
 * meant to be held in one `val` that everyone reading and writing it shares.
 *
 * ## Example 1: declare a key next to the processor that reads it
 * ```kt
 * val Owner: MetadataKey<String> = metadata()
 * ```
 */
@ExperimentalKatachiApi
public inline fun <reified T : Any> metadata(): MetadataKey<T> = metadata(T::class)

/**
 * A new key for values of [type], for the rare caller that has a class rather than a type
 * argument in hand.
 *
 * ## Example 1: build a key from a class token
 * ```kt
 * val Owner: MetadataKey<String> = metadata(String::class)
 * ```
 */
@ExperimentalKatachiApi
public fun <T : Any> metadata(type: KClass<T>): MetadataKey<T> =
    // The boxed class, not the primitive one: `Boolean::class.java` is `boolean.class`, whose
    // `cast` rejects the boxed value that is actually stored in the map.
    MetadataKey(type.javaObjectType)

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
