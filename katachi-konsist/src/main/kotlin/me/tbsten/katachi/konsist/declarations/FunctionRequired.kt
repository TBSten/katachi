package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.requiredWriter

/**
 * The `required(...)` lines for a function: written as `allow(...)` is, and the function has to be
 * there -- that name when it is not a pattern, at least one matching function when it is. The
 * parameters and the return type are chained after it.
 *
 * ## Example 1: a composable named after the file, and previews that may be there
 * ```kt
 * ktFileContent {
 *     required(annotated("Composable"), public, `fun`, fileName)
 *     allow(annotated("Preview"), private, `fun`, "*Preview")
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface FunctionRequired {
    /**
     * Writes the required line for a function named [name], asking for no modifier.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(emptyList(), kind, name)

    /**
     * Writes the required line for a function named [name], with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(listOf(modifier1), kind, name)

    /**
     * Writes the required line for a function named [name], with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(listOf(modifier1, modifier2), kind, name)

    /**
     * Writes the required line for a function named [name], with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(listOf(modifier1, modifier2, modifier3), kind, name)

    /**
     * Writes the required line for a function named [name], with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(listOf(modifier1, modifier2, modifier3, modifier4), kind, name)

    /**
     * Writes the required line for a function named [name], with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), kind, name)

    /**
     * Writes the required line for a function named [name], with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name,
    )

    /**
     * Writes the required line for a function named [name], with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name,
    )

    /**
     * Writes the required line for a function named [name], with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name,
    )

    /**
     * Writes the required line for a function named [name], with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name,
    )

    /**
     * Writes the required line for a function named [name], with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
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
        kind: FunctionKind,
        name: String,
    ): FunctionLine = requiredWriter.function(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name,
    )
}
