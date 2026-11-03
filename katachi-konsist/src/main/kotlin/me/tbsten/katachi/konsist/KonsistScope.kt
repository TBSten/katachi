package me.tbsten.katachi.konsist

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.provider.KoBaseProvider
import me.tbsten.katachi.dsl.KatachiDsl

/**
 * Receiver of a `konsist { }` block: Konsist's own query vocabulary, plus the three words
 * katachi answers with.
 *
 * Everything on the left of the dot is Konsist's — `classes()`, `functions()`,
 * `declarations()`, `withNameEndingWith(...)` and the rest arrive through Konsist's `KoScope` and
 * Konsist's list extensions, unchanged. What katachi replaces is the right of the dot: where
 * Konsist would end a query with its own `assertTrue`, a `konsist { }` block ends it with
 * [must], [mustNot] or [mustBeEmpty] — each also taking a reason and how to fix a rejection,
 * which the report then prints under it.
 *
 * The reason is the report, not taste. `assertTrue` throws on the first rejection and says
 * so in prose; katachi needs the rejected declarations themselves, because the first line of
 * an `[UnsatisfiedFileConstraint]` block is a real path a reader can open. Collecting them is the
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
 *   the project katachi walked. Use `KoScope` queries and the paths in the report instead.
 * - **`.kts` files are never in the scope.** Konsist parses a file only when its name
 *   ends in `.kt`, so a `build.gradle.kts` or any other script inside
 *   what a constraint covers is invisible here however the layout was written. A rule about
 *   build scripts has to be written as a plain `fileConstraint { }` reading the text itself.
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
 *
 * ## Example 3: an empty list with a reason, in place of Konsist's own assertions
 * ```kt
 * "is not usable from outside".konsist {
 *     classes().filter { it.hasPublicOrDefaultModifier }
 *         .mustBeEmpty("this layer is internal", listOf("Make the class internal"))
 *     // classes().assertTrue { it.hasInternalModifier }  // compile error: use must { }
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
     */
    public fun <T : KoBaseProvider> List<T>.must(predicate: (T) -> Boolean)

    /**
     * Rejects the elements of this list that [predicate] answers `true` for.
     *
     * The exact inverse of [must], written separately because `must { !it.hasX }` reads as a
     * double negative at the call site and the report cannot tell the two apart.
     *
     * @param predicate what must not hold. `true` rejects the element, `false` keeps it.
     */
    public fun <T : KoBaseProvider> List<T>.mustNot(predicate: (T) -> Boolean)

    /**
     * Rejects every element of this list, so that a query which finds anything at all fails.
     *
     * This is how "there must be none of these" is written: narrow with Konsist's query
     * vocabulary until the list holds exactly what is forbidden, then say so.
     */
    public fun <T : KoBaseProvider> List<T>.mustBeEmpty()

    /**
     * [must], with the reason the rule exists and how to fix a rejection, printed in the report
     * under each element it rejects.
     *
     * ```text
     *   Declaration: GetUserUseCaseImpl (line 9)
     *   Reason: a use case is an interface; its implementation lives in :core:data
     *   Declared at: UseCaseRole.kt:16
     *
     *   How to fix:
     *     - Move the class to :core:data
     * ```
     *
     * @param reason why the rule exists, printed as `Reason: <reason>` on one line.
     * @param howToFix the fixes to offer, one `How to fix:` item each, or empty for none.
     * @param predicate what has to hold. `true` keeps the element, `false` rejects it.
     */
    public fun <T : KoBaseProvider> List<T>.must(
        reason: String,
        howToFix: List<String> = emptyList(),
        predicate: (T) -> Boolean,
    )

    /**
     * [mustNot], with the reason and how to fix a rejection. See the `must(reason)` overload.
     *
     * @param reason why the rule exists, printed as `Reason: <reason>` on one line.
     * @param howToFix the fixes to offer, one `How to fix:` item each, or empty for none.
     * @param predicate what must not hold. `true` rejects the element, `false` keeps it.
     */
    public fun <T : KoBaseProvider> List<T>.mustNot(
        reason: String,
        howToFix: List<String> = emptyList(),
        predicate: (T) -> Boolean,
    )

    /**
     * [mustBeEmpty], with the reason and how to fix a rejection. See the `must(reason)` overload.
     *
     * @param reason why the rule exists, printed as `Reason: <reason>` on one line.
     * @param howToFix the fixes to offer, one `How to fix:` item each, or empty for none.
     */
    public fun <T : KoBaseProvider> List<T>.mustBeEmpty(
        reason: String,
        howToFix: List<String> = emptyList(),
    )

    // Shadows must match `com.lemonappdev.konsist.api.verify` signature by signature, one per
    // receiver spelling, or a call resolves back to Konsist. Bodies are concrete and throw so
    // implementations need not override deprecated members and suppressed calls fail loudly.

    /**
     * Konsist's own assertion over a list. Not usable here.
     *
     * It throws on the first rejection and describes it in prose, so katachi could not say
     * which declarations were rejected — and the first line of a report block is a path a
     * reader can open.
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

    /** Konsist's own assertion over a list. Not usable here — see [assertTrue]. */
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

    /** Konsist's own assertion over a single declaration. Not usable here — see [assertTrue]. */
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

    /** Konsist's own assertion over a single declaration. Not usable here — see [assertTrue]. */
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

    /** Konsist's own assertion over a sequence. Not usable here — see [assertTrue]. */
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

    /** Konsist's own null assertion. Not usable here — see [assertTrue]. */
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
     */
    @Deprecated(
        "katachi reports things that exist and are wrong. Declare the file in layout { } instead.",
        level = DeprecationLevel.ERROR,
    )
    public fun <E : KoBaseProvider> E?.assertNotNull(
        additionalMessage: String? = null,
        testName: String? = null,
    ): Nothing = throw KatachiKonsistDirectAssertionException(spelling = "assertNotNull", cause = null)

    /** Konsist's own emptiness assertion over a list. Not usable here — see [assertTrue]. */
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

    /** Konsist's own emptiness assertion over a sequence. Not usable here — see [assertTrue]. */
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
