package me.tbsten.katachi.konsist.declarations

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * Why a line of `ktFileContent { }` cannot match anything because of its modifiers: see
 * [KatachiKtFileContentModifierException].
 *
 * ## Example 1: tell which modifier mistake a definition made
 * ```kt
 * val error = shouldThrow<KatachiKtFileContentModifierException> {
 *     architecture { "UiState" { layout { "*".ktFile() }; ktFileContent { data.`interface`(fileName) } } }
 * }
 * error.problem shouldBe KtFileContentModifierProblem.NotApplicable
 * ```
 */
@ExperimentalKatachiApi
public enum class KtFileContentModifierProblem {
    /** Two modifiers no declaration carries together, such as `public.internal` or `data.sealed`. */
    Conflicting,

    /** A modifier only a member can carry (`override`, `protected`, `inner`, `companion`), or `abstract` / `open` on a fun, val or var, written directly in the file. */
    OnlyInClassBody,

    /** A modifier the kind cannot carry, such as `` data.`interface` `` or `` const.`fun` ``, leaving no candidate. */
    NotApplicable,
}

/**
 * Which part of a `ktFileContent { }` line a pattern was written for: see
 * [KatachiKtFileContentPatternException].
 *
 * ## Example 1: tell which pattern of a definition could not be read
 * ```kt
 * val error = shouldThrow<KatachiKtFileContentPatternException> {
 *     architecture { "Screen" { layout { "*".ktFile() }; ktFileContent { `import`("a..b") } } }
 * }
 * error.what shouldBe KtFileContentPatternKind.Import
 * ```
 */
@ExperimentalKatachiApi
public enum class KtFileContentPatternKind(internal val label: String) {
    /** The name of a declaration line: `` `class`("*ViewModel") ``. */
    Name("name"),

    /** A package line: `` `package`("com.example.**") ``. */
    Package("package"),

    /** An import line: `` `import`("kotlinx.coroutines.**") ``. */
    Import("import"),

    /** A property type, a parameter type or a return type: `` `val`("state", "StateFlow<*>") ``. */
    Type("type"),

    /** A `.superclass("...")`. */
    Superclass("superclass"),

    /** A name of `annotated(...)`. */
    Annotation("annotation"),

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
 *     architecture { "Screen" { layout { "*".ktFile() }; ktFileContent { `import`("kotlin.**").except("kotlin.io.**") } } }
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

    /** `.optional()` on a prohibited line, which is never required anyway. */
    OptionalOnProhibited,

    /** A `{ }` given both to the kind and to `.superclass(...)`. */
    BodyWrittenTwice,

    /** A `{ }` on a prohibited line, whose members are never looked at. */
    BodyOnProhibited,

    /** The parameters of a `fun` line written twice. */
    ParametersWrittenTwice,

    /** `.returns(...)` written twice. */
    ReturnTypeWrittenTwice,

    /** `.superclass()` with no name. */
    SuperclassWithoutNames,

    /** `or` between something that starts with `not.` and something that does not. */
    NotInsideOr,

    /** A `typealias` line inside a class's `{ }`, where Kotlin has no typealias. */
    TypeAliasInClassBody,

    /** `annotated()` with no name. */
    AnnotatedWithoutNames,

    /** `anythingElse()` after `not.`, which names nothing to prohibit. */
    AnythingElseAfterNot,

    /** A value of one block's vocabulary used to write a line in another block, or after its block ended. */
    OutsideItsBlock,
}
