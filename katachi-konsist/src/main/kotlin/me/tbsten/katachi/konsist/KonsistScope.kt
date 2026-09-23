package me.tbsten.katachi.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.provider.KoBaseProvider
import me.tbsten.katachi.dsl.KatachiDsl

/**
 * Receiver of a `konsist { }` block: Konsist's own query vocabulary, plus the three words
 * katachi answers with.
 *
 * Everything on the left of the dot is Konsist's — `classes()`, `functions()`,
 * `declarations()`, `withNameEndingWith(...)` and the rest arrive through [KoScope] and
 * Konsist's list extensions, unchanged. What katachi replaces is the right of the dot: where
 * Konsist would end a query with its own `assertTrue`, a `konsist { }` block ends it with
 * [must], [mustNot] or [mustBeEmpty].
 *
 * The reason is the report, not taste. `assertTrue` throws on the first rejection and says
 * so in prose; katachi needs the rejected declarations themselves, because the first line of
 * an `[UnsatisfiedConstraint]` block is a real path a reader can open. Collecting them is the
 * only way a block that rejects four classes can produce four blocks pointing at four files.
 * All twelve of Konsist's assertions are therefore shadowed below as compile errors — one
 * declaration per spelling, so that `classes().first().assertTrue { }` and
 * `classes().asSequence().assertTrue { }` cannot slip past a shadow written only for lists.
 *
 * ## What this scope cannot do
 *
 * - **`KoFileDeclaration.projectPath` and `moduleName` are not usable here.** The scope is
 *   built with `scopeFromExternalDirectories`, which Konsist documents as not resolving its
 *   own project root, so both are relative to whatever root Konsist inferred rather than to
 *   the project katachi walked. Use [KoScope] queries and the paths in the report instead.
 * - **`.kts` files are never in the scope.** Konsist 0.17.3 parses a file only when its name
 *   ends in `.kt` (`File.isKotlinFile`), so a `build.gradle.kts` or any other script inside
 *   what a constraint covers is invisible here however the layout was written. A rule about
 *   build scripts has to be written as a plain `constraint { }` reading the text itself.
 * - **`assertArchitecture` is not shadowed.** It is a member extension of
 *   `KoArchitectureAssertion`, so it does not resolve from inside a `konsist { }` block at
 *   all — unless a block writes `with(KoArchitectureCreator) { }` to bring the dispatch
 *   receiver in. That spelling is the same escape as building a list outside the block, and
 *   it is not one katachi can close.
 *
 * ## Example 1: every class in the covered files is internal
 * ```kt
 * "is not exposed outside its feature".konsist {
 *     classes().must { it.hasInternalModifier }
 * }
 * ```
 *
 * ## Example 2: a query over every declaration, not just classes
 * ```kt
 * konsist {
 *     declarations().mustNot { it.toString().contains("TODO") }
 * }
 * ```
 */
@KatachiDsl
public interface KonsistScope : KoScope {
    /**
     * Rejects the elements of this list that [predicate] answers `false` for.
     *
     * Every rejected element becomes one entry in the report, named and located when Konsist
     * can say where it is. An empty list rejects nothing and is not an error: a place that
     * fills up over time holds no declarations until it does.
     *
     * There is no `Sequence` overload. Konsist's sequence queries end with `.toList()`, and a
     * constraint has to hold every rejection at once anyway to report them in walk order.
     *
     * @param predicate what has to hold. `true` keeps the element, `false` rejects it.
     *
     * ## Example 1: every use case exposes `invoke`
     * ```kt
     * "has an invoke function".konsist {
     *     classes().withNameEndingWith("UseCase")
     *         .must { klass -> klass.hasFunction { it.name == "invoke" } }
     * }
     * ```
     *
     * ## Example 2: a query that is not about classes
     * ```kt
     * konsist {
     *     functions().must { it.hasPublicOrDefaultModifier }
     * }
     * ```
     */
    public fun <T : KoBaseProvider> List<T>.must(predicate: (T) -> Boolean)

    /**
     * Rejects the elements of this list that [predicate] answers `true` for.
     *
     * The exact inverse of [must], written separately because `must { !it.hasX }` reads as a
     * double negative at the call site and the report cannot tell the two apart.
     *
     * @param predicate what must not hold. `true` rejects the element, `false` keeps it.
     *
     * ## Example 1: no use case depends on the Android framework
     * ```kt
     * "does not depend on Android".konsist {
     *     files.mustNot { file -> file.imports.any { it.name.startsWith("android.") } }
     * }
     * ```
     */
    public fun <T : KoBaseProvider> List<T>.mustNot(predicate: (T) -> Boolean)

    /**
     * Rejects every element of this list, so that a query which finds anything at all fails.
     *
     * This is how "there must be none of these" is written: narrow with Konsist's query
     * vocabulary until the list holds exactly what is forbidden, then say so.
     *
     * ## Example 1: no public class is left in an internal layer
     * ```kt
     * "is not usable from outside".konsist {
     *     classes().filter { it.hasPublicOrDefaultModifier }.mustBeEmpty()
     * }
     * ```
     */
    public fun <T : KoBaseProvider> List<T>.mustBeEmpty()

    // ---------------------------------------------------------------------------------------
    // Konsist's own twelve assertions, shadowed.
    //
    // Each one is declared here with the same name, the same receiver shape, the same
    // parameter list and the same type bound as the top level extension in
    // `com.lemonappdev.konsist.api.verify`. A member of the receiver's own scope beats an
    // imported top level extension, so writing one inside a `konsist { }` block resolves to
    // these -- and these are compile errors.
    //
    // It is one declaration per *spelling*, not one per idea: shadowing only
    // `List<E?>.assertTrue` would leave `classes().first().assertTrue { }` and
    // `classes().asSequence().assertTrue { }` resolving straight back to Konsist.
    //
    // Each carries a body rather than being abstract, for two separate reasons. `KonsistScopeImpl`
    // must not have to override a deprecated member (that override is itself a warning, and this
    // module holds itself to zero). And the body throws rather than doing nothing, so that a call
    // written under `@Suppress("DEPRECATION_ERROR")` fails loudly instead of passing silently --
    // `Nothing` rather than `Unit` says so in the type.
    // ---------------------------------------------------------------------------------------

    /**
     * Konsist's own assertion over a list. Not usable here.
     *
     * It throws on the first rejection and describes it in prose, so katachi could not say
     * which declarations were rejected — and the first line of a report block is a path a
     * reader can open.
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().assertTrue { it.hasInternalModifier }   // compile error
     * classes().must { it.hasInternalModifier }         // ok
     * ```
     */
    @Deprecated(
        "katachi cannot see which declarations assertTrue rejected. Use must / mustNot / mustBeEmpty.",
        ReplaceWith("must(function)"),
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> List<E?>.assertTrue(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
        function: (E) -> Boolean?,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertTrue", cause = null)

    /**
     * Konsist's own assertion over a list. Not usable here — see [assertTrue].
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().assertFalse { it.hasPublicOrDefaultModifier }   // compile error
     * classes().mustNot { it.hasPublicOrDefaultModifier }       // ok
     * ```
     */
    @Deprecated(
        "katachi cannot see which declarations assertFalse rejected. Use must / mustNot / mustBeEmpty.",
        ReplaceWith("mustNot(function)"),
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> List<E?>.assertFalse(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
        function: (E) -> Boolean?,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertFalse", cause = null)

    /**
     * Konsist's own assertion over a single declaration. Not usable here — see [assertTrue].
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().first().assertTrue { it.hasInternalModifier }   // compile error
     * classes().take(1).must { it.hasInternalModifier }         // ok
     * ```
     */
    @Deprecated(
        "katachi cannot see which declaration assertTrue rejected. Use must / mustNot / mustBeEmpty.",
        ReplaceWith("listOfNotNull(this).must(function)"),
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> E?.assertTrue(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
        function: (E) -> Boolean?,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertTrue", cause = null)

    /**
     * Konsist's own assertion over a single declaration. Not usable here — see [assertTrue].
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().first().assertFalse { it.hasPublicOrDefaultModifier }   // compile error
     * classes().take(1).mustNot { it.hasPublicOrDefaultModifier }       // ok
     * ```
     */
    @Deprecated(
        "katachi cannot see which declaration assertFalse rejected. Use must / mustNot / mustBeEmpty.",
        ReplaceWith("listOfNotNull(this).mustNot(function)"),
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> E?.assertFalse(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
        function: (E) -> Boolean?,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertFalse", cause = null)

    /**
     * Konsist's own assertion over a sequence. Not usable here — see [assertTrue].
     *
     * There is no sequence form of [must] either: a constraint holds every rejection at once
     * so that it can report them in walk order, so end the query with `.toList()`.
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().asSequence().assertTrue { it.hasInternalModifier }   // compile error
     * classes().asSequence().toList().must { it.hasInternalModifier } // ok
     * ```
     */
    @Deprecated(
        "katachi cannot see which declarations assertTrue rejected. Use toList() then must / mustNot.",
        ReplaceWith("toList().must(function)"),
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> Sequence<E?>.assertTrue(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
        function: (E) -> Boolean?,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertTrue", cause = null)

    /**
     * Konsist's own assertion over a sequence. Not usable here — see [assertTrue].
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().asSequence().assertFalse { it.hasPublicOrDefaultModifier }      // compile error
     * classes().asSequence().toList().mustNot { it.hasPublicOrDefaultModifier } // ok
     * ```
     */
    @Deprecated(
        "katachi cannot see which declarations assertFalse rejected. Use toList() then must / mustNot.",
        ReplaceWith("toList().mustNot(function)"),
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> Sequence<E?>.assertFalse(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
        function: (E) -> Boolean?,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertFalse", cause = null)

    /**
     * Konsist's own null assertion. Not usable here — see [assertTrue].
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().firstOrNull().assertNull()                          // compile error
     * classes().filter { it.hasInternalModifier }.mustBeEmpty()     // ok
     * ```
     */
    @Deprecated(
        "katachi cannot report an assertion that names no declaration. Use mustBeEmpty.",
        ReplaceWith("listOfNotNull(this).mustBeEmpty()"),
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> E?.assertNull(
        additionalMessage: String? = null,
        testName: String? = null,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertNull", cause = null)

    /**
     * Konsist's own not-null assertion. Not usable here — see [assertTrue].
     *
     * katachi has no word for "there must be at least one of these": a report block names
     * something that exists and is wrong, and an absence has no path to name. Declare the file
     * in `layout { }` instead, which is what makes a missing file a `[MissingFile]` block.
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().firstOrNull().assertNotNull()                     // compile error
     * layout { "useCase" / "*UseCase".ktFile() }                  // ok, and a better report
     * ```
     */
    @Deprecated(
        "katachi reports things that exist and are wrong. Declare the file in layout { } instead.",
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> E?.assertNotNull(
        additionalMessage: String? = null,
        testName: String? = null,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertNotNull", cause = null)

    /**
     * Konsist's own emptiness assertion over a list. Not usable here — see [assertTrue].
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().filter { it.hasPublicOrDefaultModifier }.assertEmpty()     // compile error
     * classes().filter { it.hasPublicOrDefaultModifier }.mustBeEmpty()     // ok
     * ```
     */
    @Deprecated(
        "katachi cannot see which declarations assertEmpty rejected. Use mustBeEmpty.",
        ReplaceWith("mustBeEmpty()"),
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> List<E?>.assertEmpty(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertEmpty", cause = null)

    /**
     * Konsist's own non-emptiness assertion over a list. Not usable here — see [assertNotNull]
     * for why katachi has no word for it.
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().assertNotEmpty()                     // compile error
     * layout { "useCase" / "*UseCase".ktFile() }     // ok, and a better report
     * ```
     */
    @Deprecated(
        "katachi reports things that exist and are wrong. Declare the file in layout { } instead.",
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> List<E?>.assertNotEmpty(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertNotEmpty", cause = null)

    /**
     * Konsist's own emptiness assertion over a sequence. Not usable here — see [assertTrue].
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().asSequence().assertEmpty()              // compile error
     * classes().asSequence().toList().mustBeEmpty()     // ok
     * ```
     */
    @Deprecated(
        "katachi cannot see which declarations assertEmpty rejected. Use toList() then mustBeEmpty.",
        ReplaceWith("toList().mustBeEmpty()"),
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> Sequence<E?>.assertEmpty(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertEmpty", cause = null)

    /**
     * Konsist's own non-emptiness assertion over a sequence. Not usable here — see
     * [assertNotNull] for why katachi has no word for it.
     *
     * ## Example 1: what to write instead
     * ```kt
     * classes().asSequence().assertNotEmpty()        // compile error
     * layout { "useCase" / "*UseCase".ktFile() }     // ok, and a better report
     * ```
     */
    @Deprecated(
        "katachi reports things that exist and are wrong. Declare the file in layout { } instead.",
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> Sequence<E?>.assertNotEmpty(
        strict: Boolean = false,
        additionalMessage: String? = null,
        testName: String? = null,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertNotEmpty", cause = null)
}
