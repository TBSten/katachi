package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.DeclarationLineBuilder

/**
 * A class, interface or object line just written, which the calls chained after it change:
 * supertypes, members, what a prohibited line lets through.
 *
 * ## Example 1: a view model named after the file, with its supertype and members
 * ```kt
 * ktFileContent {
 *     `package`("com.example.feature.**")
 *     `import`("**")
 *
 *     required(annotated("HiltViewModel"), public, `class`, fileName).superclass("ViewModel") {
 *         required(public, `val`, "uiState", "StateFlow<*>")
 *         allow(private, anythingElse)
 *     }
 *     allow(`object`, "*Preview")
 *     allow(`class`, "*") { allow(anythingElse) }
 *     not.allow(`class`, "*Impl").except("LegacyImpl")
 * }
 * ```
 */
@ExperimentalKatachiApi
public class TypeLine internal constructor(
    internal val line: DeclarationLineBuilder,
) {
    /**
     * Requires each of [names] among the declaration's supertypes -- a class it extends or an
     * interface it implements. A name without `<...>` matches whatever type arguments the supertype
     * has. [body] is the allow list of the declaration's members; a line given no `{ }` at all
     * allows no member.
     *
     * A name is a glob in which `*` stands for zero or more characters, so `"*NavKey"` matches
     * `NavKey` itself as well as `StartupNavKey`. To list the candidates, put them in braces:
     * `"{StartupNavKey,*NavKey}"`, or for names that do not overlap, `"{NavKey,*Route}"`.
     *
     * ## Example 1: the default implementation of the interface the file is named after
     * ```kt
     * allow(`class`, "Default$fileName").superclass(fileName) { allow(override, `fun`, "invoke") }
     * ```
     *
     * @throws KatachiKtFileContentLineException when no name is given, or when [body] is given a
     *   second time.
     * @throws KatachiKtFileContentPatternException when a name is not a pattern katachi reads.
     */
    public fun superclass(vararg names: String, body: (ClassBodyScope.() -> Unit)? = null): TypeLine {
        line.addSuperclasses(names.toList())
        if (body != null) line.writeBody(body)
        return this
    }

    /**
     * Lets the names matching [names] through this prohibited line.
     *
     * ## Example 1: no `*Impl` class but one
     * ```kt
     * not.allow(`class`, "*Impl").except("LegacyImpl")
     * ```
     *
     * @throws KatachiKtFileContentLineException on a line that does not start with `not.`, or when
     *   no name is given.
     */
    public fun except(vararg names: String): TypeLine {
        line.addExcept(names.toList())
        return this
    }

    /** The line as written so far. */
    override fun toString(): String = line.text
}

/**
 * A function line just written, which the calls chained after it change: parameters, the return
 * type, what a prohibited line lets through.
 *
 * A parameter is written the way Kotlin writes it, `"name: Type"`: a name pattern and a type
 * pattern, split at the first `:`. `"*: String"` accepts any name and `"id: *"` any type.
 *
 * ## Example 1: parameters and return types
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     allow(`fun`, "load")()                                // no parameter
 *     allow(`fun`, "save")("name: String", "count: Int")    // these parameters, in order
 *     allow(`fun`, "find*")("*Id: String")                  // a name pattern too
 *     allow(`fun`, "on*")("*: String")                      // any name, a String
 *     allow(`fun`, "on*")(anyArgs())                        // any parameters, the same as writing none
 *     allow(public, `fun`, "on*")().returns("Unit")
 * }
 * ```
 */
@ExperimentalKatachiApi
public class FunctionLine internal constructor(
    internal val line: DeclarationLineBuilder,
) {
    /**
     * Requires exactly these parameters, in order, each written `"name: Type"`; `()` is no
     * parameter. A parameter matches when its name matches the part before the first `:` and its
     * type the part after it; spaces around either part are ignored. Default values and `vararg`
     * are not looked at, nor are a receiver and context parameters.
     *
     * @throws KatachiKtFileContentLineException when the parameters were already written, or when a
     *   parameter is not `"name: Type"`: no `:`, nothing before it, or nothing after it.
     * @throws KatachiKtFileContentPatternException when a name or a type is not a pattern katachi reads.
     */
    public operator fun invoke(vararg parameters: String): FunctionLine {
        line.writeParameters(parameters.toList())
        return this
    }

    /**
     * Asks nothing about the parameters, the same as writing none, for a line that reads better
     * with it.
     *
     * ## Example 1: any parameters
     * ```kt
     * allow(`fun`, "on*")(anyArgs())
     * ```
     *
     * @throws KatachiKtFileContentLineException when the parameters were already written.
     */
    public operator fun invoke(args: AnyArgs): FunctionLine {
        line.writeParameters(null)
        return this
    }

    /**
     * Requires the return type [type]. A block body that writes none returns `Unit`; an expression
     * body that writes none matches no return type.
     *
     * ## Example 1: functions returning Unit
     * ```kt
     * allow(`fun`, "on*")().returns("Unit")
     * ```
     *
     * @throws KatachiKtFileContentLineException when the return type was already written.
     * @throws KatachiKtFileContentPatternException when [type] is not a pattern katachi reads.
     */
    public fun returns(type: String): FunctionLine {
        line.writeReturnType(type)
        return this
    }

    /**
     * Lets the names matching [names] through this prohibited line.
     *
     * ## Example 1: no public function but `invoke`
     * ```kt
     * not.allow(public, `fun`, "*").except("invoke")
     * ```
     *
     * @throws KatachiKtFileContentLineException on a line that does not start with `not.`, or when
     *   no name is given.
     */
    public fun except(vararg names: String): FunctionLine {
        line.addExcept(names.toList())
        return this
    }

    /** The line as written so far. */
    override fun toString(): String = line.text
}

/**
 * A property line just written, which the calls chained after it change.
 *
 * ## Example 1: a property that may be there, and a prohibited one
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     allow(`class`, fileName) {
 *         allow(`val`, "isLoading", "Boolean")
 *         not.allow(public, `var`, "*")
 *     }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class PropertyLine internal constructor(
    internal val line: DeclarationLineBuilder,
) {
    /**
     * Lets the names matching [names] through this prohibited line.
     *
     * ## Example 1: no public mutable property but one
     * ```kt
     * not.allow(public, `var`, "*").except("isEnabled")
     * ```
     *
     * @throws KatachiKtFileContentLineException on a line that does not start with `not.`, or when
     *   no name is given.
     */
    public fun except(vararg names: String): PropertyLine {
        line.addExcept(names.toList())
        return this
    }

    /** The line as written so far. */
    override fun toString(): String = line.text
}

/**
 * A type alias line just written, which the calls chained after it change.
 *
 * ## Example 1: type aliases that may be there
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     allow(`typealias`, "*Key")
 * }
 * ```
 */
@ExperimentalKatachiApi
public class TypeAliasLine internal constructor(
    internal val line: DeclarationLineBuilder,
) {
    /**
     * Lets the names matching [names] through this prohibited line.
     *
     * ## Example 1: no type alias but one
     * ```kt
     * not.allow(`typealias`, "*").except("Callback")
     * ```
     *
     * @throws KatachiKtFileContentLineException on a line that does not start with `not.`, or when
     *   no name is given.
     */
    public fun except(vararg names: String): TypeAliasLine {
        line.addExcept(names.toList())
        return this
    }

    /** The line as written so far. */
    override fun toString(): String = line.text
}

/**
 * What `anyArgs()` returns: "any parameters", written where a function line's parameters go.
 *
 * ## Example 1: any parameters
 * ```kt
 * ktFileContent {
 *     `package`("com.example.**")
 *     allow(`fun`, "on*")(anyArgs())
 * }
 * ```
 */
@ExperimentalKatachiApi
public class AnyArgs internal constructor() {
    /** `anyArgs()`. */
    override fun toString(): String = "anyArgs()"
}
