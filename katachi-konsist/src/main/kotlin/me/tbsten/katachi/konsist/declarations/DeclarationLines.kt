package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.konsist.declarations.internal.BodyKind
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

    /**
     * Requires exactly these type parameters, in order: a name pattern, after the `reified`, `in` or `out` it has to carry,
     * and a bound after `:` that one of its bounds (after `:` or in a `where` clause) has to match. `"T"` matches `reified T`
     * and `T : Any` too, as what is not written is not asked about; `.typeParameters()` asks for none. A line without
     * `.typeParameters(...)` asks nothing about them.
     *
     * ## Example 1: a class with one type parameter bounded by Any
     * ```kt
     * allow(`class`, "*Holder").typeParameters("T : Any")
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice, or when a type parameter has no name, or nothing after its `:`.
     * @throws KatachiKtFileContentPatternException when a name or a bound is not a pattern katachi reads.
     */
    public fun typeParameters(vararg parameters: String): TypeLine {
        line.writeTypeParameters(parameters.toList())
        return this
    }

    /**
     * Requires a KDoc (`/** ... */`) on the declaration. A line without `.kdoc()` asks nothing about it.
     *
     * ## Example 1: documented declarations
     * ```kt
     * allow(public, `class`, "*").kdoc()
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice.
     */
    public fun kdoc(): TypeLine {
        line.writeKDoc()
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
     * type the part after it; spaces around either part are ignored. `vararg`, `noinline` and
     * `crossinline` in front (`"vararg xs: Int"`) are asked for when written, and so is a default value after
     * `=` (`"force: Boolean = *"`); what is not written is not looked at. Context parameters are asked with
     * [context]. The receiver of an extension is not a
     * parameter: it is written before the name, as in `` allow(`fun`, "User", "to*") ``.
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

    /**
     * Requires exactly these context parameters, in order, each written `"name: Type"` as with the parameters: the
     * declaration `context(logger: Logger) fun log()` matches `.context("logger: Logger")`, and `.context()` asks for none.
     * A context receiver (`context(Logger)`) is named `_`, so `"_: Logger"` matches it. A line without `.context(...)`
     * asks nothing about them.
     *
     * ## Example 1: declarations that need a logger in their context
     * ```kt
     * allow(`fun`, "log*")(anyArgs()).context("*: Logger")
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice, or when a parameter is not `"name: Type"`.
     * @throws KatachiKtFileContentPatternException when a name or a type is not a pattern katachi reads.
     */
    public fun context(vararg parameters: String): FunctionLine {
        line.writeContext(parameters.toList())
        return this
    }

    /**
     * Requires exactly these type parameters, in order: a name pattern, after the `reified`, `in` or `out` it has to carry,
     * and a bound after `:` that one of its bounds (after `:` or in a `where` clause) has to match. `"T"` matches `reified T`
     * and `T : Any` too, as what is not written is not asked about; `.typeParameters()` asks for none. A line without
     * `.typeParameters(...)` asks nothing about them.
     *
     * ## Example 1: a reified function
     * ```kt
     * allow(inline, `fun`, "get*")(anyArgs()).typeParameters("reified T")
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice, or when a type parameter has no name, or nothing after its `:`.
     * @throws KatachiKtFileContentPatternException when a name or a bound is not a pattern katachi reads.
     */
    public fun typeParameters(vararg parameters: String): FunctionLine {
        line.writeTypeParameters(parameters.toList())
        return this
    }

    /**
     * Requires a KDoc (`/** ... */`) on the declaration. A line without `.kdoc()` asks nothing about it.
     *
     * ## Example 1: documented declarations
     * ```kt
     * allow(public, `fun`, "*")(anyArgs()).kdoc()
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice.
     */
    public fun kdoc(): FunctionLine {
        line.writeKDoc()
        return this
    }

    /**
     * Requires an expression body (`fun f() = ...`). A line without `.expressionBody()`, `.blockBody()` or `.noBody()` asks nothing about the body.
     *
     * ## Example 1
     * ```kt
     * allow(`fun`, "is*")(anyArgs()).expressionBody()
     * ```
     *
     * @throws KatachiKtFileContentLineException when one of the three is already written.
     */
    public fun expressionBody(): FunctionLine {
        line.writeBodyKind(BodyKind.Expression)
        return this
    }

    /**
     * Requires a block body (`fun f() { ... }`). A line without `.expressionBody()`, `.blockBody()` or `.noBody()` asks nothing about the body.
     *
     * ## Example 1
     * ```kt
     * allow(`fun`, "run*")(anyArgs()).blockBody()
     * ```
     *
     * @throws KatachiKtFileContentLineException when one of the three is already written.
     */
    public fun blockBody(): FunctionLine {
        line.writeBodyKind(BodyKind.Block)
        return this
    }

    /**
     * Requires no body, as an abstract or `expect` function has. A line without `.expressionBody()`, `.blockBody()` or `.noBody()` asks nothing about the body.
     *
     * ## Example 1
     * ```kt
     * allow(abstract, `fun`, "*")(anyArgs()).noBody()
     * ```
     *
     * @throws KatachiKtFileContentLineException when one of the three is already written.
     */
    public fun noBody(): FunctionLine {
        line.writeBodyKind(BodyKind.None)
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

    /**
     * Requires exactly these context parameters, in order, each written `"name: Type"` as with the parameters: the
     * declaration `context(logger: Logger) fun log()` matches `.context("logger: Logger")`, and `.context()` asks for none.
     * A context receiver (`context(Logger)`) is named `_`, so `"_: Logger"` matches it. A line without `.context(...)`
     * asks nothing about them.
     *
     * ## Example 1: declarations that need a logger in their context
     * ```kt
     * allow(`fun`, "log*")(anyArgs()).context("*: Logger")
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice, or when a parameter is not `"name: Type"`.
     * @throws KatachiKtFileContentPatternException when a name or a type is not a pattern katachi reads.
     */
    public fun context(vararg parameters: String): PropertyLine {
        line.writeContext(parameters.toList())
        return this
    }

    /**
     * Requires exactly these type parameters, in order: a name pattern, after the `reified`, `in` or `out` it has to carry,
     * and a bound after `:` that one of its bounds (after `:` or in a `where` clause) has to match. `"T"` matches `reified T`
     * and `T : Any` too, as what is not written is not asked about; `.typeParameters()` asks for none. A line without
     * `.typeParameters(...)` asks nothing about them.
     *
     * ## Example 1: a generic extension property
     * ```kt
     * allow(`val`, "List<T>", "second", "T").typeParameters("T")
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice, or when a type parameter has no name, or nothing after its `:`.
     * @throws KatachiKtFileContentPatternException when a name or a bound is not a pattern katachi reads.
     */
    public fun typeParameters(vararg parameters: String): PropertyLine {
        line.writeTypeParameters(parameters.toList())
        return this
    }

    /**
     * Requires a KDoc (`/** ... */`) on the declaration. A line without `.kdoc()` asks nothing about it.
     *
     * ## Example 1: documented declarations
     * ```kt
     * allow(public, `val`, "*").kdoc()
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice.
     */
    public fun kdoc(): PropertyLine {
        line.writeKDoc()
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

    /**
     * Requires exactly these type parameters, in order: a name pattern, after the `reified`, `in` or `out` it has to carry,
     * and a bound after `:` that one of its bounds (after `:` or in a `where` clause) has to match. `"T"` matches `reified T`
     * and `T : Any` too, as what is not written is not asked about; `.typeParameters()` asks for none. A line without
     * `.typeParameters(...)` asks nothing about them.
     *
     * ## Example 1: a generic alias
     * ```kt
     * allow(`typealias`, "Handler", "(T) -> Unit").typeParameters("T")
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice, or when a type parameter has no name, or nothing after its `:`.
     * @throws KatachiKtFileContentPatternException when a name or a bound is not a pattern katachi reads.
     */
    public fun typeParameters(vararg parameters: String): TypeAliasLine {
        line.writeTypeParameters(parameters.toList())
        return this
    }

    /**
     * Requires a KDoc (`/** ... */`) on the declaration. A line without `.kdoc()` asks nothing about it.
     *
     * ## Example 1: documented declarations
     * ```kt
     * allow(public, `typealias`, "*").kdoc()
     * ```
     *
     * @throws KatachiKtFileContentLineException when written twice.
     */
    public fun kdoc(): TypeAliasLine {
        line.writeKDoc()
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
