package me.tbsten.katachi.dsl

/**
 * A DSL receiver that can carry metadata: a role block and a group block.
 *
 * It exists so that a processor can offer one property for both. `var MetadataScope.owner by
 * Owner` can be written on a group and on a role; `var RoleScope.owner by Owner` only on a
 * role, which is the right choice for a word that means nothing on a group.
 *
 * Metadata written on a group is not inherited by the roles inside it. That is the same rule
 * `documented` has always followed: a declared value is kept as written, and working out an
 * effective value is the reader's job, where the rule for combining them is actually known.
 *
 * ## Example 1: one property usable on both groups and roles
 * ```kt
 * val Owner: MetadataKey<String> = metadata()
 * var MetadataScope.owner: String? by Owner
 *
 * val arch = architecture {
 *     "domain".group {
 *         owner = "platform"
 *         "UseCase" { owner = "product" }
 *     }
 * }
 * arch.groups.single()[Owner] shouldBe "platform"
 * arch.allRoles.single()[Owner] shouldBe "product"
 * ```
 */
@KatachiDsl
public sealed interface MetadataScope

/**
 * The scope's collector. Matched rather than cast: [MetadataScope] is sealed, so the compiler
 * checks that every scope katachi hands to a block is covered, and a new one cannot be added
 * without this being updated.
 */
internal fun MetadataScope.metadataBuilder(): MetadataBuilder = when (this) {
    is RoleScopeImpl -> metadata
    is GroupScopeImpl -> metadata
}
