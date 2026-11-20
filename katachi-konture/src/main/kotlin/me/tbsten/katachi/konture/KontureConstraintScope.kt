package me.tbsten.katachi.konture

import io.github.baole.konture.ClassesRuleBuilder
import io.github.baole.konture.FilesRuleBuilder
import io.github.baole.konture.FunctionsRuleBuilder
import io.github.baole.konture.KontureDsl
import io.github.baole.konture.PropertiesRuleBuilder
import me.tbsten.katachi.dsl.KatachiDsl

/**
 * Receiver of a `konture { }` block: the four kinds of Konture rule, each over the files the
 * constraint covers.
 *
 * [files], [classes], [functions] and [properties] open Konture's own rule builders —
 * `FilesRuleBuilder`, `ClassesRuleBuilder`, `FunctionsRuleBuilder`, `PropertiesRuleBuilder` —
 * and inside them the vocabulary is Konture's, unchanged: `that() inPackage "..."`,
 * `should() notDependOnPackages "..."`, `should() satisfy { }` and the rest. katachi adds no
 * word of its own; Konture's `should()` is the end of a rule.
 *
 * What is different is where the rules run and what happens to their violations. The builders
 * are bound to a graph made of the covered `.kt` files alone — no Gradle plugin, no
 * `layout_v2.json` — and every rule is evaluated in Konture's audit mode, so that a violation
 * is collected rather than thrown and each one becomes a report block pointing at one file.
 * A rule that selects nothing is refused, as Konture refuses it
 * ([KatachiKontureEmptySelectionException]): write Konture's `allowEmpty()` in a rule that may
 * select nothing. A rule with no `should()` is refused ([KatachiKontureRuleRefusedException]); a
 * block with no rule at all is refused ([KatachiKontureNoExpectationException]). A constraint
 * whose covered files are none yet is not evaluated, so it needs no `allowEmpty()`.
 *
 * Konture's own entry points to its default graph — `Konture.files()`, `Konture.classes { }`,
 * `architecture { }`, `rule(id) { }`, `architectureRules { }` and the rest — are compile errors
 * inside the block; see [KontureDefaultGraphShadows] for the list and the reason.
 *
 * ## What this scope cannot do
 *
 * - **Module graph rules are out of scope.** `modules { }`, `slices { }`, `layeredArchitecture { }`,
 *   `layer`, `sourceSet`, `rule { }` and `noCycles()` need Gradle's build graph, which katachi's
 *   layout does not describe. Use Konture itself, with its Gradle plugin, in a test module of its
 *   own.
 * - **Module filters and assertions match nothing useful.** The graph katachi builds has one
 *   module, named `:`, so `that() inModule ":feature:home"` (and `notInModule`,
 *   `resideInModule`, their plural forms, on files and classes alike) selects nothing, which is
 *   refused like any empty selection, while `should() inModule ":feature:home"` rejects every
 *   declaration. Say where a file lives in `layout { }`, and which package it is in with
 *   `inPackage`.
 * - **Supertypes come from the covered files, names from the walked ones.** `areAssignableTo` /
 *   `beAssignableTo` cannot follow an inheritance chain through a class the constraint does not
 *   cover: cover the whole hierarchy. A bare name is looked up among the classes of every `.kt`
 *   file the check walked, so one under two wildcard imports resolves even when another role
 *   declares its class. A directory the walk did not enter (one the layout ignores, or one no
 *   role declares), libraries and the JDK are not looked in: a bare name under two or more
 *   wildcard imports that means one of their classes stays unresolved. Import such a class by
 *   name.
 * - **Inbound rules see only the covered files.** `onlyBeAccessedByAnyPackage` and
 *   `notBeAccessedByAnyPackage` count accesses from inside the graph, which here is the covered
 *   set.
 * - **Outbound rules on `functions { }` and `properties { }` read no imports.** There,
 *   `notDependOnPackages` and `onlyDependOnPackages` see only the types the declaration itself
 *   uses, once resolved, which is all `notReferenceClass` sees anywhere: a bare name left
 *   unresolved, as above, escapes them. On `files { }` and `classes { }` they read the imports
 *   too.
 * - **`classes { }` does not count `java`, `javax` or `kotlin`.** Konture's `classes { }` forms
 *   drop every dependency on those packages (and their subpackages) before matching, so
 *   `classes { should() notDependOnPackages "java.io.." }` is green on a class that imports
 *   `java.io.File`. Write such a rule in `files { }`, which counts every import.
 * - **It cannot share a test module with `katachi-konsist`.** Konsist and Konture need different
 *   versions of kotlin-compiler-embeddable; whichever Gradle resolves to, one of the two fails to
 *   parse. Put `konture { }` and `konsist { }` in different test
 *   modules.
 * - **Konture's baseline, reports and severities are not used.** Every violation lands in
 *   katachi's report and katachi's baseline is the one that applies; Konture's baseline, HTML /
 *   JSON / SARIF writers and logging are switched off for the run. The exception is a `konture.*`
 *   system property such as `-Dkonture.baseline.generate=true` or `-Dkonture.report.path=...`,
 *   which Konture reads before katachi can switch anything off.
 * - **Members of the `Konture` object cannot be shadowed.** `Konture.reset()`,
 *   `Konture.failOnSeverity = ERROR` and `Konture.checkRatchet()` compile inside the block and
 *   act on the thread's Konture state. katachi switches audit mode back on before it evaluates
 *   the rules, so none of them can turn a violation into a throw; `reset()` still clears
 *   Konture's caches and your own Konture settings on that thread, and `checkRatchet()` may
 *   assert on its own ([KatachiKontureDirectAssertionException]).
 * - **`@Suppress("konture:<ruleId>")` and `suppress { }` do work.** A violation Konture marks as
 *   suppressed is dropped; the rule ids are `files.rule`, `classes.rule`, `functions.rule` and
 *   `properties.rule`.
 * - **`.kts` files are never in the graph.** Konture parses `.kt` only, and so does katachi here.
 *   A constraint that covers only scripts is refused ([KatachiKontureNoKotlinFilesException]).
 * - **Konture's violation message does not reach the report.** A report block carries the path,
 *   the declaration and the line; the prose Konture would have printed is not kept. The
 *   declaration is named without its package, as `ktFileContent` names it (`HomeScreen.State`,
 *   `User.id`), which is what tells two violations in one file apart in a baseline.
 * - **A predicate has to answer, not assert.** `satisfy { }` and its kin are run by Konture on
 *   each declaration with nothing catching what they throw; one that asserts ends the rule at
 *   the first declaration it dislikes and the constraint is reported as unanswered
 *   ([KatachiKontureDirectAssertionException]). Return a `Boolean`.
 *
 * ## Example 1: no class in the covered files depends on Compose
 * ```kt
 * "Does not depend on Compose".konture {
 *     classes { should() notDependOnPackages "androidx.compose.." }
 * }
 * ```
 *
 * ## Example 2: several rules of several kinds in one block
 * ```kt
 * konture {
 *     files { should() nameEndsWith "UseCase.kt" }
 *     classes { that() nameEndsWith "UseCase"; should().bePublic() }
 *     functions { that() haveName "invoke"; should().bePublic() }
 *     properties { should().beVal() }
 * }
 * ```
 */
@KatachiDsl
@KontureDsl
public interface KontureConstraintScope : KontureDefaultGraphShadows {
    /**
     * A Konture rule over the covered files, as `FilesRuleBuilder` spells it.
     *
     * @param block the rule, in Konture's `that() ... should() ...` vocabulary.
     */
    public fun files(block: FilesRuleBuilder.() -> Unit)

    /**
     * A Konture rule over the classes declared in the covered files, as `ClassesRuleBuilder`
     * spells it.
     *
     * @param block the rule, in Konture's `that() ... should() ...` vocabulary.
     */
    public fun classes(block: ClassesRuleBuilder.() -> Unit)

    /**
     * A Konture rule over the functions declared in the covered files, top level and member, as
     * `FunctionsRuleBuilder` spells it.
     *
     * @param block the rule, in Konture's `that() ... should() ...` vocabulary.
     */
    public fun functions(block: FunctionsRuleBuilder.() -> Unit)

    /**
     * A Konture rule over the properties declared in the covered files, top level and member, as
     * `PropertiesRuleBuilder` spells it.
     *
     * @param block the rule, in Konture's `that() ... should() ...` vocabulary.
     */
    public fun properties(block: PropertiesRuleBuilder.() -> Unit)
}
