package me.tbsten.katachi.dsl

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.metadataBuilder

/**
 * A key a declaration can carry one value under.
 *
 * A key is an object, not a name: two keys are the same key only when they are the same
 * instance, so two processors that pick the same word never collide, and a value written under
 * a key only comes back out as the type that key was declared with.
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
 *         "UseCase".role { owner = "platform" }
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
     */
    public operator fun getValue(thisRef: MetadataScope, property: KProperty<*>): T? =
        thisRef.metadataBuilder()[this]

    /**
     * Writes [value] under this key in [thisRef]. Writing `null` clears the key.
     */
    public operator fun setValue(thisRef: MetadataScope, property: KProperty<*>, value: T?) {
        thisRef.metadataBuilder()[this] = value
    }

    // Checked cast through the key's class instead of an unchecked cast, so a wrong value fails at the key that
    // knows its type.
    internal fun valueOf(stored: Any): T = type.cast(stored)

    /**
     * `MetadataKey<<type>>`. Two keys of the same type print the same, but are still different
     * keys.
     */
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
