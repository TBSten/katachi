package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Why a line of `ktFileContent { }` cannot match anything because of its modifiers: see
 * [KatachiKtFileContentModifierException].
 *
 * ## Example 1: tell which modifier mistake a definition made
 * ```kt
 * val error = shouldThrow<KatachiKtFileContentModifierException> {
 *     architecture { "UiState".role { layout { "*".ktFile() }; ktFileContent { allow(data, `interface`, fileName) } } }
 * }
 * error.problem shouldBe KtFileContentModifierProblem.NotApplicable
 * ```
 */
@ExperimentalKatachiApi
public enum class KtFileContentModifierProblem {
    /**
     * Two modifiers no declaration carries together, such as `allow(public, internal, ...)` or `allow(data, sealed, ...)`.
     *
     * It is refused only when every candidate of the line conflicts: `allow(public or internal, internal, ...)`
     * keeps the internal one and is not refused.
     */
    Conflicting,

    /**
     * A modifier only a class member can carry, written directly in the file.
     *
     * That is `override`, `protected`, `inner`, `companion`, or `abstract` / `open` on a fun, val or var.
     */
    OnlyInClassBody,

    /** A modifier the kind cannot carry, such as `` allow(data, `interface`, ...) `` or `` allow(const, `fun`, ...) ``, leaving no candidate. */
    NotApplicable,
}

/**
 * Which part of a `ktFileContent { }` line a pattern was written for: see
 * [KatachiKtFileContentPatternException].
 *
 * ## Example 1: tell which pattern of a definition could not be read
 * ```kt
 * val error = shouldThrow<KatachiKtFileContentPatternException> {
 *     architecture { "Screen".role { layout { "*".ktFile() }; ktFileContent { `import`("a..b") } } }
 * }
 * error.what shouldBe KtFileContentPatternKind.Import
 * ```
 */
@ExperimentalKatachiApi
public enum class KtFileContentPatternKind(internal val label: String) {
    /** The name of a declaration line: `` allow(`class`, "*ViewModel") ``. */
    Name("name"),

    /** A package line: `` `package`("com.example.**") ``. */
    Package("package"),

    /** An import line: `` `import`("kotlinx.coroutines.**") ``. */
    Import("import"),

    /** A property type, a parameter type or a return type: `` allow(`val`, "state").type("StateFlow<*>") ``. */
    Type("type"),

    /** The name of a parameter of a `fun` line, before its `:`: `` allow(`fun`, "find")("*Id: String") ``. */
    ParameterName("parameter name"),

    /** A `.superclass("...")`. */
    Superclass("superclass"),

    /** The receiver of an extension, between the kind and the name: `` allow(`fun`, "User", "to*") ``. */
    Receiver("receiver"),

    /** A name of `annotated(...)`, with its use-site target and arguments when written: `annotated("get:JvmName(*)")`. */
    Annotation("annotation"),

    /** What is between the parentheses of `annotated("Name(...)")` or `fileAnnotation("Name(...)")`. */
    AnnotationArguments("annotation arguments"),

    /** The default value of a parameter, after its `=`: `` allow(`fun`, "load")("force: Boolean = *") ``. */
    DefaultValue("default value"),

    /** A type parameter of `.typeParameters(...)`: `"reified T : Any"`. */
    TypeParameter("type parameter"),

    /** A context parameter of `.context(...)`: `"logger: Logger"`. */
    ContextParameter("context parameter"),

    /** The name of what a property delegates to, in `.delegatedBy("lazy")`. */
    Delegate("delegate"),

    /** A file annotation line: `fileAnnotation("Suppress")`. */
    FileAnnotation("file annotation"),

    /** A pattern of `.except(...)`. */
    Except("except"),
}

/**
 * What is wrong with how a line of `ktFileContent { }` is written: see
 * [KatachiKtFileContentLineException].
 *
 * ## Example 1: tell which mistake a line made
 * ```kt
 * val error = shouldThrow<KatachiKtFileContentLineException> {
 *     architecture { "Screen".role { layout { "*".ktFile() }; ktFileContent { `import`("kotlin.**").except("kotlin.io.**") } } }
 * }
 * error.problem shouldBe KtFileContentLineProblem.ExceptWithoutNot
 * ```
 */
@ExperimentalKatachiApi
public enum class KtFileContentLineProblem {
    /** `.except(...)` on an allowed line, where it has no effect. */
    ExceptWithoutNot,

    /** `.except()` with no pattern. */
    ExceptWithoutNames,

    /** A `{ }` given both to `allow(...)` and to `.superclass(...)`. */
    BodyWrittenTwice,

    /** A `{ }` on a prohibited line, whose members are never looked at. */
    BodyOnProhibited,

    /** The parameters of a `fun` line written twice. */
    ParametersWrittenTwice,

    /**
     * A parameter of a `fun` line with no name: `("String")`, which has no `:`, or `(": String")`. A
     * parameter is written `"name: Type"`, and `"*: String"` accepts any name.
     */
    ParameterWithoutName,

    /** A parameter of a `fun` line with no type after its `:`: `("id:")`. `"id: *"` accepts any type. */
    ParameterWithoutType,

    /** A parameter with nothing after its `=`: `("force: Boolean =")`. `"force: Boolean = *"` accepts any default value. */
    ParameterWithoutDefaultValue,

    /** `.returns(...)` written twice. */
    ReturnTypeWrittenTwice,

    /** `actualsIn()` with no source set, or one that is blank or holds a `/`: name a source set, such as `"androidMain"`. */
    ActualsWithoutSourceSets,

    /** A detail such as `.context(...)`, `.kdoc()` or `.setter(...)` written twice on one line. */
    DetailWrittenTwice,

    /**
     * A detail no declaration of the line's kinds has: `.setter()` on a `val` line, `.primaryConstructor(...)` on a line of
     * `interface` or `object` only.
     */
    DetailNotForKind,

    /**
     * Modifiers a detail does not take: `.setter(...)` and `.primaryConstructor(...)` take one visibility, not an `or`,
     * `.optional()` or `annotated(...)`.
     */
    DetailModifiers,

    /** `.superclass()` with no name. */
    SuperclassWithoutNames,

    /** `annotated()` with no name. */
    AnnotatedWithoutNames,

    /**
     * A value written on a line of its own and never passed to `allow(...)` or `required(...)`.
     *
     * The value is a modifier or annotations (`annotated("Inject")` or `private` alone), a kind
     * (`` `class` `` alone), or `anythingElse`. A value made and not used, as in
     * `` mapOf("a" to `class`) ``, is refused too: make only the values a line is written with.
     */
    DanglingModifiers,

    /** `not.allow(..., anythingElse)`, which names nothing to prohibit. */
    AnythingElseAfterNot,

    /**
     * `annotated("X").optional()`: an annotation not written is not asked about already, so an
     * optional one would mean nothing.
     */
    OptionalAnnotation,

    /** `.optional()` on a value that is `.optional()` already: `suspend.optional().optional()`. */
    OptionalTwice,

    /**
     * `.optional()` on one side of an `or`, as in `internal.optional() or private`.
     *
     * Put `.optional()` on the whole `or` instead: `(internal or private).optional()`.
     */
    OptionalInsideOr,

    /**
     * A `.optional()` modifier on an `allow(..., anythingElse)` line, which lets any modifier it
     * does not write be there already.
     */
    OptionalOnAnythingElse,

    /**
     * A `.optional()` modifier on a `not.allow(...)` line, where it changes nothing.
     *
     * A `not.allow(...)` line prohibits what carries the modifiers it writes whatever else it
     * carries, so an optional one adds nothing.
     */
    OptionalAfterNot,

    /** A value of one block's vocabulary used to write a line in another block, or after its block ended. */
    OutsideItsBlock,
}
