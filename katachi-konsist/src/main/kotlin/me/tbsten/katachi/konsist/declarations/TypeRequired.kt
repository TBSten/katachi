package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.requiredWriter

/**
 * The `required(...)` lines for a class, an interface or an object that has to be there.
 *
 * They are written as `allow(...)` is: modifiers and annotations (none to ten of them), then the
 * kind, then the name. A name that is not a pattern asks for that name; a pattern asks for
 * at least one declaration it matches. A required line is an allow list entry too.
 *
 * ## Example 1: a view model named after the file, and its actions
 * ```kt
 * ktFileContent {
 *     `package`("com.example.feature.**")
 *     `import`("**")
 *
 *     required(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
 *         required(public, `val`, "uiState", "StateFlow<*>")
 *         allow(private, anythingElse)
 *     }
 *     required(sealed, type, "*Action") { allow(data, `object`, "*") }
 *     allow(data, `class`, "*") { allow(`val`, "*") }
 * }
 * ```
 */
@ExperimentalKatachiApi
public sealed interface TypeRequired {
    /**
     * Writes the required line for a class, interface or object named [name], carrying no modifier (`public` and `final` aside).
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(emptyList(), kind, name, body)

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(listOf(modifier1), kind, name, body)

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] and [modifier2] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(listOf(modifier1, modifier2), kind, name, body)

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] to [modifier3] in front.
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers here.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     * @throws KatachiKtFileContentLineException when a value comes from another block.
     */
    public fun required(
        modifier1: Modifiers,
        modifier2: Modifiers,
        modifier3: Modifiers,
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(listOf(modifier1, modifier2, modifier3), kind, name, body)

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] to [modifier4] in front.
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
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(listOf(modifier1, modifier2, modifier3, modifier4), kind, name, body)

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] to [modifier5] in front.
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
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(listOf(modifier1, modifier2, modifier3, modifier4, modifier5), kind, name, body)

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] to [modifier6] in front.
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
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6),
        kind, name, body,
    )

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] to [modifier7] in front.
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
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7),
        kind, name, body,
    )

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] to [modifier8] in front.
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
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8),
        kind, name, body,
    )

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] to [modifier9] in front.
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
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9),
        kind, name, body,
    )

    /**
     * Writes the required line for a class, interface or object named [name], with [modifier1] to [modifier10] in front.
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
        kind: TypeKind,
        name: String,
        body: (ClassBodyScope.() -> Unit)? = null,
    ): TypeLine = requiredWriter.type(
        listOf(modifier1, modifier2, modifier3, modifier4, modifier5, modifier6, modifier7, modifier8, modifier9, modifier10),
        kind, name, body,
    )
}
