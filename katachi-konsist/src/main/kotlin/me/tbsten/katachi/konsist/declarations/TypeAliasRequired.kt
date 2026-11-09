package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.requiredWriter

/**
 * The `required(...)` lines for a type alias: written as `allow(...)` is, and the type alias has to
 * be there. Directly in the file or, since Kotlin 2.2 (nested type aliases), inside the `{ }` of a class.
 *
 * ## Example 1: a type alias named after the file
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     required(`typealias`, fileName)
 *     allow(private, `typealias`, "*")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface TypeAliasRequired {
    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, carrying no modifier (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        kind: TypeAliasKind,
        name: String,
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(emptyList(), kind, name, type)

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        kind: TypeAliasKind,
        name: String,
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(listOf(modifier1), kind, name, type)

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: TypeAliasKind,
        name: String,
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(listOf(modifier1, modifier2), kind, name, type)

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: TypeAliasKind,
        name: String,
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(listOf(modifier1, modifier2, modifier3), kind, name, type)

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: TypeAliasKind,
        name: String,
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(listOf(modifier1, modifier2, modifier3, modifier4), kind, name, type)

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: TypeAliasKind,
        name: String,
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), kind, name, type)

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: TypeAliasKind,
        name: String,
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name, type,
    )

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        kind: TypeAliasKind,
        name: String,
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name, type,
    )

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
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
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name, type,
    )

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
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
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name, type,
    )

    /**
     * Writes the required line for a type alias named [name], aliasing [type] when given, with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
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
        type: String? = null,
    ): TypeAliasLine = requiredWriter.typeAlias(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name, type,
    )
}
