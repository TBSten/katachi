package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.allowWriter

/**
 * The `allow(..., anythingElse)` lines that let through any declaration no other line matches.
 *
 * The modifiers and annotations (none to ten of them) it has to carry come before `anythingElse`.
 * Unlike a declaration line, it does not ask for the modifiers it does not write: `allow(private, anythingElse)` lets a
 * `private suspend fun` through, and a `.optional()` modifier on it is refused as meaningless.
 * The members of what it lets through are not looked at.
 *
 * Lines are judged in this order: a prohibited line (`not.allow(...)`) wins, then an allowed line,
 * then `allow(..., anythingElse)`, and anything left is a violation. `not.allow(..., anythingElse)`
 * is refused.
 *
 * ## Example 1: one class named after the file, plus any private helper
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *
 *     allow(`class`, fileName) { allow(anythingElse) }
 *     allow(private, anythingElse)
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface AnythingElseAllow {
    /**
     * Allows what no other line matches, whatever its kind and name, asking for no modifier.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
     */
    public fun allow(
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(emptyList(), anything)

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
     */
    public fun allow(
        modifier1: Modifiers,
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(listOf(modifier1), anything)

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(listOf(modifier1, modifier2), anything)

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(listOf(modifier1, modifier2, modifier3), anything)

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] to [modifier4] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(listOf(modifier1, modifier2, modifier3, modifier4), anything)

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] to [modifier5] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), anything)

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] to [modifier6] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        anything,
    )

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] to [modifier7] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
     */
    public fun allow(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        modifier4: Modifiers,
        modifier5: Modifiers,
        modifier6: Modifiers,
        modifier7: Modifiers,
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        anything,
    )

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] to [modifier8] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
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
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        anything,
    )

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] to [modifier9] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
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
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        anything,
    )

    /**
     * Allows what no other line matches, whatever its kind and name, with [modifier1] to [modifier10] in front.
     *
     * @throws KatachiKtFileContentModifierException when the modifiers cannot be on one declaration, or not here.
     * @throws KatachiKtFileContentLineException when a value comes from another block, or when written after
     *   `not.`, where it names nothing in particular to prohibit.
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
        anything: AnythingElse,
    ): Unit = allowWriter.anythingElse(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        anything,
    )
}
