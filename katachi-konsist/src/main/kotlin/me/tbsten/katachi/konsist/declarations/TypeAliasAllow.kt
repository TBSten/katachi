package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.allowWriter

/**
 * The `allow(...)` lines for a type alias: the modifiers and annotations (none to ten of them),
 * then `` `typealias` ``, then the name. Only directly in the file: Kotlin has none inside a class.
 *
 * ## Example 1: private type aliases, and one named after the file
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     allow(private, `typealias`, "*")
 *     allow(`typealias`, fileName)
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface TypeAliasAllow {
    /**
     * Writes the line for a type alias named [name], carrying no modifier (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(emptyList(), kind, name)

    /**
     * Writes the line for a type alias named [name], with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(listOf(modifier1), kind, name)

    /**
     * Writes the line for a type alias named [name], with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(listOf(modifier1, modifier2), kind, name)

    /**
     * Writes the line for a type alias named [name], with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(listOf(modifier1, modifier2, modifier3), kind, name)

    /**
     * Writes the line for a type alias named [name], with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(listOf(modifier1, modifier2, modifier3, modifier4), kind, name)

    /**
     * Writes the line for a type alias named [name], with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), kind, name)

    /**
     * Writes the line for a type alias named [name], with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name,
    )

    /**
     * Writes the line for a type alias named [name], with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name,
    )

    /**
     * Writes the line for a type alias named [name], with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        modifier8: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name,
    )

    /**
     * Writes the line for a type alias named [name], with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        modifier8: Modifiers,
        modifier9: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name,
    )

    /**
     * Writes the line for a type alias named [name], with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written inside
     *   the `{ }` of a class, where Kotlin has no type alias.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        modifier8: Modifiers,
        modifier9: Modifiers,
        modifier10: Modifiers,
        kind: TypeAliasKind,
        name: String,
    ): TypeAliasLine = allowWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name,
    )
}
