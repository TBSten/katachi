package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName
import me.tbsten.katachi.konsist.declarations.internal.DeclarationListBuilder
import me.tbsten.katachi.konsist.declarations.internal.ModifierName
import me.tbsten.katachi.konsist.declarations.internal.ModifierSet
import me.tbsten.katachi.konsist.declarations.internal.Shape
import me.tbsten.katachi.konsist.declarations.internal.renderModifierAlternatives
import me.tbsten.katachi.konsist.declarations.internal.renderShapes

/**
 * A class, an interface, an object, or any mix of them made with `or`, with the modifiers written
 * in front of it. Called with a name, it writes a line.
 *
 * ## Example 1: kinds called with a name, combined with `or`, and given members
 * ```kt
 * ktFileContent {
 *     (`class` or `interface`)(fileName)
 *     sealed.type("*Action")
 *     `class`("Default$fileName").superclass(fileName) {
 *         override.`fun`("invoke")
 *     }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class TypeKind internal constructor(
    internal val shapes: List<Shape>,
    internal val prohibited: Boolean,
    internal val sink: DeclarationListBuilder,
) {
    /**
     * Writes the line for a declaration named [name] (a pattern: `*` matches any text). Required
     * when [name] has no `*`. With [body], the declaration's members are an allow list too.
     *
     * ## Example 1: a class named after the file, holding one property and private helpers
     * ```kt
     * `class`(fileName) {
     *     `val`("uiState")
     *     private.anythingElse()
     * }
     * ```
     *
     * @throws KatachiKtFileContentModifierException when no declaration can have the modifiers.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public operator fun invoke(name: String, body: (ClassBodyScope.() -> Unit)? = null): TypeLine {
        val line = sink.declare(shapes, prohibited, name)
        if (body != null) line.writeBody(body)
        return TypeLine(line)
    }

    /**
     * Either this kind or [other].
     *
     * ## Example 1: a class or an interface, not an object
     * ```kt
     * (`class` or `interface`)(fileName)
     * ```
     *
     * @throws KatachiKtFileContentLineException when only one side starts with `not.`.
     */
    public infix fun or(other: TypeKind): TypeKind {
        checkJoinable(prohibited, sink, other.prohibited, other.sink, "$this or $other")
        return TypeKind((shapes + other.shapes).distinct(), prohibited, sink)
    }

    /** `class`, `(class or interface)`, `not sealed (class or interface)`. */
    override fun toString(): String = notOf(prohibited) + renderShapes(shapes)
}

/**
 * A function, with the modifiers written in front of it. Called with a name, it writes a line;
 * `` `fun`.`interface` `` is a fun interface instead.
 *
 * ## Example 1: functions, and a fun interface
 * ```kt
 * ktFileContent {
 *     annotated("Composable").public.`fun`(fileName)
 *     `fun`.`interface`("*Listener")
 * }
 * ```
 */
@ExperimentalKatachiApi
public class FunctionKind internal constructor(
    internal val alternatives: List<ModifierSet>,
    internal val prohibited: Boolean,
    internal val sink: DeclarationListBuilder,
) {
    /**
     * Writes the line for a function named [name]. Chain the parameter types and the return type
     * after it; neither is asked about when not written.
     *
     * ## Example 1: functions with no parameter that return Unit
     * ```kt
     * public.`fun`("on*")().returns("Unit")
     * ```
     *
     * @throws KatachiKtFileContentModifierException when no function can have the modifiers.
     * @throws KatachiKtFileContentPatternException when [name] is not a pattern katachi reads.
     */
    public operator fun invoke(name: String): FunctionLine =
        FunctionLine(sink.declare(alternatives.map { Shape(it, DeclarationKindName.Function) }, prohibited, name))

    /**
     * A fun interface: an interface with the modifier `fun`.
     *
     * ## Example 1: listeners declared as fun interfaces
     * ```kt
     * `fun`.`interface`("*Listener")
     * ```
     */
    public val `interface`: TypeKind
        get() = TypeKind(
            alternatives.map { Shape(it + ModifierName.Fun, DeclarationKindName.Interface) },
            prohibited,
            sink,
        )

    /** `fun`, `private fun`. */
    override fun toString(): String =
        notOf(prohibited) + listOf(renderModifierAlternatives(alternatives), "fun").filter { it.isNotEmpty() }.joinToString(" ")
}

/**
 * A `val`, a `var`, or either of them made with `or`, with the modifiers written in front of it.
 * Called with a name, and optionally a type, it writes a line.
 *
 * ## Example 1: a property with its type, and constants
 * ```kt
 * ktFileContent {
 *     `class`(fileName) { public.`val`("uiState", "StateFlow<*>") }
 *     const.`val`("*")
 * }
 * ```
 */
@ExperimentalKatachiApi
public class PropertyKind internal constructor(
    internal val shapes: List<Shape>,
    internal val prohibited: Boolean,
    internal val sink: DeclarationListBuilder,
) {
    /**
     * Writes the line for a property named [name]. With [type], the type written in the source has
     * to match it (package names and spaces aside); an inferred type does not.
     *
     * ## Example 1: a state holder typed after the file
     * ```kt
     * public.`val`("uiState", "StateFlow<${fileName.replace("ViewModel", "UiState")}>")
     * ```
     *
     * @throws KatachiKtFileContentModifierException when no property can have the modifiers.
     * @throws KatachiKtFileContentPatternException when [name] or [type] is not a pattern katachi reads.
     */
    public operator fun invoke(name: String, type: String? = null): PropertyLine {
        val line = sink.declare(shapes, prohibited, name)
        if (type != null) line.writePropertyType(type)
        return PropertyLine(line)
    }

    /**
     * Either this kind or [other].
     *
     * ## Example 1: any property, read-only or not
     * ```kt
     * (`val` or `var`)("*")
     * ```
     *
     * @throws KatachiKtFileContentLineException when only one side starts with `not.`.
     */
    public infix fun or(other: PropertyKind): PropertyKind {
        checkJoinable(prohibited, sink, other.prohibited, other.sink, "$this or $other")
        return PropertyKind((shapes + other.shapes).distinct(), prohibited, sink)
    }

    /** `val`, `(val or var)`, `const val`. */
    override fun toString(): String = notOf(prohibited) + renderShapes(shapes)
}

/**
 * A type alias, with the modifiers written in front of it. Called with a name, it writes a line.
 *
 * ## Example 1: a type alias named after the file
 * ```kt
 * ktFileContent { `typealias`(fileName) }
 * ```
 */
@ExperimentalKatachiApi
public class TypeAliasKind internal constructor(
    internal val alternatives: List<ModifierSet>,
    internal val prohibited: Boolean,
    internal val sink: DeclarationListBuilder,
) {
    /**
     * Writes the line for a type alias named [name].
     *
     * ## Example 1: private type aliases
     * ```kt
     * private.`typealias`("*")
     * ```
     *
     * @throws KatachiKtFileContentLineException when written inside the `{ }` of a class.
     * @throws KatachiKtFileContentModifierException when no type alias can have the modifiers.
     */
    public operator fun invoke(name: String): TypeAliasLine =
        TypeAliasLine(sink.declare(alternatives.map { Shape(it, DeclarationKindName.TypeAlias) }, prohibited, name))

    /** `typealias`, `private typealias`. */
    override fun toString(): String =
        notOf(prohibited) + listOf(renderModifierAlternatives(alternatives), "typealias").filter { it.isNotEmpty() }.joinToString(" ")
}

private fun notOf(prohibited: Boolean): String = if (prohibited) "not " else ""

/** Refuses an `or` across two blocks, or between a prohibited and an allowed side. */
private fun checkJoinable(
    prohibited: Boolean,
    sink: DeclarationListBuilder,
    otherProhibited: Boolean,
    otherSink: DeclarationListBuilder,
    line: String,
) {
    if (otherSink !== sink) {
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.OutsideItsBlock, line, DeclarationSite.capture())
    }
    if (otherProhibited != prohibited) {
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.NotInsideOr, line, DeclarationSite.capture())
    }
}
