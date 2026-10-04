package me.tbsten.katachi.konture

import io.github.baole.konture.ClassesRuleBuilder
import io.github.baole.konture.FilesRuleBuilder
import io.github.baole.konture.FunctionsRuleBuilder
import io.github.baole.konture.Konture
import io.github.baole.konture.KontureContext
import io.github.baole.konture.LayeredArchitectureDsl
import io.github.baole.konture.ModulesRuleBuilder
import io.github.baole.konture.ProjectGraph
import io.github.baole.konture.PropertiesRuleBuilder
import io.github.baole.konture.RuleBuilder
import io.github.baole.konture.RuleSetBuilder
import io.github.baole.konture.SlicesRuleBuilder
import io.github.baole.konture.SourceSetSelector

/** What every shadow below does when `@Suppress("DEPRECATION_ERROR")` lets a call through. */
private fun refuse(spelling: String): Nothing =
    throw KatachiKontureDefaultGraphException(spelling = spelling, cause = null)

// The deprecation messages, one per family. `const` so that the annotations below stay one line each.
private const val ARCHITECTURE_MESSAGE: String =
    "architecture { } runs against Konture's default graph, which katachi does not build, and throws its " +
        "violations as prose. Write files { } / classes { } / functions { } / properties { } directly in the " +
        "konture { } block."
private const val RULE_MESSAGE: String =
    "rule(id) { } binds its rules to Konture's default graph, which katachi does not build, and its id and " +
        "severity never reach katachi's report. Write files { } / classes { } / functions { } / properties { } " +
        "directly in the konture { } block; the constraint's name is what the report shows."
private const val RULE_SET_MESSAGE: String =
    "architectureRules { } builds a RuleSet whose check() runs against Konture's default graph, which katachi " +
        "does not build. Write files { } / classes { } / functions { } / properties { } directly in the " +
        "konture { } block."
private const val FILES_MESSAGE: String =
    "Konture.files reads Konture's default graph, which katachi does not build. Use files { }."
private const val CLASSES_MESSAGE: String =
    "Konture.classes reads Konture's default graph, which katachi does not build. Use classes { }."
private const val FUNCTIONS_MESSAGE: String =
    "Konture.functions reads Konture's default graph, which katachi does not build. Use functions { }."
private const val PROPERTIES_MESSAGE: String =
    "Konture.properties reads Konture's default graph, which katachi does not build. Use properties { }."
private const val MODULE_GRAPH_MESSAGE: String =
    "Module graph rules need Konture's Gradle plugin and build graph, which katachi does not provide. " +
        "Use Konture directly in a test module of its own."

/**
 * Konture's entry points to its default project graph, declared as compile errors inside a
 * `konture { }` block.
 *
 * Each member below has the same name, the same receiver and the same parameter list as one
 * of Konture's own: `architecture`, `rule` and `architectureRules` are top level functions in
 * `io.github.baole.konture`, the rest are extensions on the `Konture` object in the same
 * package. A member of the receiver's own scope beats an imported top level declaration, so
 * writing one of them inside a `konture { }` block resolves here — and here it is
 * `@Deprecated(level = ERROR)`.
 *
 * They would fail at run time anyway: katachi never writes `layout_v2.json`, so
 * `Konture.projectGraph` throws. The shadow turns that run time failure into a compile error
 * whose message names the replacement. Each carries a body rather than being abstract, so that
 * the implementation never overrides a deprecated member, and the body throws
 * [KatachiKontureDefaultGraphException] rather than doing nothing, so that
 * `@Suppress("DEPRECATION_ERROR")` fails loudly instead of passing silently — `Nothing` says so
 * in the type.
 *
 * Five families: the two `architecture` functions and their `Konture.` twins; the builder
 * accessors `Konture.files()` / `classes()` / `functions()` / `properties()` in their four forms
 * (bare, with a source set selector, with a block that runs at once, with both); the module
 * graph rules — `modules`, `slices`, `layeredArchitecture`, `layered`, `assertNoCycles` — which
 * have no replacement here because katachi's layout does not describe Gradle's build graph;
 * the named rule `rule(id) { }`, whose builder reads the default graph the moment it is made;
 * and `architectureRules { }` with its `Konture.` twins, whose `RuleSet` can only be run
 * against the default graph.
 *
 * Not shadowed, by choice: `Konture.scope`, `Konture.fileScope`, `Konture.scopeFromPackage`,
 * `Konture.scopeFromPackageOf<T>()` and the rest of the scope accessors. They are extensions
 * too and could be shadowed the same way, but they read the same missing graph and fail
 * loudly at run time, which katachi reports as [KatachiKontureDefaultGraphException] with the
 * same advice; shadowing every one of them would double this file for the same outcome. Not
 * shadowable at all: members of the `Konture` object itself — `Konture.projectGraph`,
 * `Konture.reset()`, `Konture.failOnSeverity`, `Konture.checkRatchet()` — because a member
 * always wins over a member extension. [KontureConstraintScope] says what writing those inside
 * a block does.
 *
 * ## Example 1: the replacement for each shadowed spelling
 * ```kt
 * konture {
 *     Konture.classes().should().beInternal()                // compile error
 *     Konture.classes { should().beInternal() }              // compile error
 *     architecture { classes { should().beInternal() } }     // compile error
 *     classes { should().beInternal() }                      // ok
 * }
 * ```
 */
public interface KontureDefaultGraphShadows {
    /**
     * Konture's top level `architecture { }`. Not usable here: it runs against Konture's default graph and
     * throws its violations as one `AssertionError`, so katachi could neither point it at the covered files
     * nor read what it rejected.
     *
     * ## Example 1: what to write instead
     * ```kt
     * architecture { classes { should().beInternal() } }   // compile error
     * classes { should().beInternal() }                    // ok
     * ```
     */
    @Deprecated(ARCHITECTURE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun architecture(block: KontureContext.() -> Unit): Nothing = refuse("architecture")

    /**
     * Konture's top level `architecture(graph) { }`. Not usable here — see [architecture].
     *
     * ## Example 1: what to write instead
     * ```kt
     * architecture(graph) { classes { should().beInternal() } }   // compile error
     * classes { should().beInternal() }                           // ok
     * ```
     */
    @Deprecated(ARCHITECTURE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun architecture(graph: ProjectGraph, block: KontureContext.() -> Unit): Nothing = refuse("architecture")

    /**
     * Konture's `Konture.architecture { }`. Not usable here — see [architecture].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.architecture { classes { should().beInternal() } }   // compile error
     * classes { should().beInternal() }                            // ok
     * ```
     */
    @Deprecated(ARCHITECTURE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun Konture.architecture(block: KontureContext.() -> Unit): Nothing = refuse("Konture.architecture")

    /**
     * Konture's `Konture.architecture(graph) { }`. Not usable here — see [architecture].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.architecture(graph) { classes { should().beInternal() } }   // compile error
     * classes { should().beInternal() }                                   // ok
     * ```
     */
    @Deprecated(ARCHITECTURE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun Konture.architecture(graph: ProjectGraph, block: KontureContext.() -> Unit): Nothing =
        refuse("Konture.architecture")

    /**
     * Konture's `Konture.files()`. Not usable here: it binds the rule to Konture's default graph, which katachi
     * does not build. `files { }` binds the same builder to the covered files.
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.files().should() nameEndsWith "UseCase.kt"   // compile error
     * files { should() nameEndsWith "UseCase.kt" }         // ok
     * ```
     */
    @Deprecated(FILES_MESSAGE, ReplaceWith("files { }"), level = DeprecationLevel.ERROR)
    public fun Konture.files(): Nothing = refuse("Konture.files()")

    /**
     * Konture's `Konture.files(sourceSets)`. Not usable here — see [files]. The graph katachi builds has one
     * source set, so there is nothing to select.
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.files(SourceSets.production()).should() nameEndsWith "UseCase.kt"   // compile error
     * files { should() nameEndsWith "UseCase.kt" }                                // ok
     * ```
     */
    @Deprecated(FILES_MESSAGE, ReplaceWith("files { }"), level = DeprecationLevel.ERROR)
    public fun Konture.files(sourceSets: SourceSetSelector): Nothing = refuse("Konture.files(sourceSets)")

    /**
     * Konture's `Konture.files { }`, which runs and asserts at once. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.files { should() nameEndsWith "UseCase.kt" }   // compile error
     * files { should() nameEndsWith "UseCase.kt" }           // ok
     * ```
     */
    @Deprecated(FILES_MESSAGE, ReplaceWith("files { }"), level = DeprecationLevel.ERROR)
    public fun Konture.files(block: FilesRuleBuilder.() -> Unit): Nothing = refuse("Konture.files { }")

    /**
     * Konture's `Konture.files(sourceSets) { }`. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.files(SourceSets.production()) { should() nameEndsWith "UseCase.kt" }   // compile error
     * files { should() nameEndsWith "UseCase.kt" }                                    // ok
     * ```
     */
    @Deprecated(FILES_MESSAGE, ReplaceWith("files { }"), level = DeprecationLevel.ERROR)
    public fun Konture.files(sourceSets: SourceSetSelector, block: FilesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.files(sourceSets) { }")

    /**
     * Konture's `Konture.classes()`. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.classes().should().beInternal()   // compile error
     * classes { should().beInternal() }         // ok
     * ```
     */
    @Deprecated(CLASSES_MESSAGE, ReplaceWith("classes { }"), level = DeprecationLevel.ERROR)
    public fun Konture.classes(): Nothing = refuse("Konture.classes()")

    /**
     * Konture's `Konture.classes(sourceSets)`. Not usable here — see [files]. The graph katachi builds has one
     * source set, so there is nothing to select.
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.classes(SourceSets.production()).should().beInternal()   // compile error
     * classes { should().beInternal() }                                // ok
     * ```
     */
    @Deprecated(CLASSES_MESSAGE, ReplaceWith("classes { }"), level = DeprecationLevel.ERROR)
    public fun Konture.classes(sourceSets: SourceSetSelector): Nothing = refuse("Konture.classes(sourceSets)")

    /**
     * Konture's `Konture.classes { }`, which runs and asserts at once. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.classes { should().beInternal() }   // compile error
     * classes { should().beInternal() }           // ok
     * ```
     */
    @Deprecated(CLASSES_MESSAGE, ReplaceWith("classes { }"), level = DeprecationLevel.ERROR)
    public fun Konture.classes(block: ClassesRuleBuilder.() -> Unit): Nothing = refuse("Konture.classes { }")

    /**
     * Konture's `Konture.classes(sourceSets) { }`. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.classes(SourceSets.production()) { should().beInternal() }   // compile error
     * classes { should().beInternal() }                                    // ok
     * ```
     */
    @Deprecated(CLASSES_MESSAGE, ReplaceWith("classes { }"), level = DeprecationLevel.ERROR)
    public fun Konture.classes(sourceSets: SourceSetSelector, block: ClassesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.classes(sourceSets) { }")

    /**
     * Konture's `Konture.functions()`. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.functions().should().bePublic()   // compile error
     * functions { should().bePublic() }         // ok
     * ```
     */
    @Deprecated(FUNCTIONS_MESSAGE, ReplaceWith("functions { }"), level = DeprecationLevel.ERROR)
    public fun Konture.functions(): Nothing = refuse("Konture.functions()")

    /**
     * Konture's `Konture.functions(sourceSets)`. Not usable here — see [files]. The graph katachi builds has one
     * source set, so there is nothing to select.
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.functions(SourceSets.production()).should().bePublic()   // compile error
     * functions { should().bePublic() }                                // ok
     * ```
     */
    @Deprecated(FUNCTIONS_MESSAGE, ReplaceWith("functions { }"), level = DeprecationLevel.ERROR)
    public fun Konture.functions(sourceSets: SourceSetSelector): Nothing = refuse("Konture.functions(sourceSets)")

    /**
     * Konture's `Konture.functions { }`, which runs and asserts at once. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.functions { should().bePublic() }   // compile error
     * functions { should().bePublic() }           // ok
     * ```
     */
    @Deprecated(FUNCTIONS_MESSAGE, ReplaceWith("functions { }"), level = DeprecationLevel.ERROR)
    public fun Konture.functions(block: FunctionsRuleBuilder.() -> Unit): Nothing = refuse("Konture.functions { }")

    /**
     * Konture's `Konture.functions(sourceSets) { }`. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.functions(SourceSets.production()) { should().bePublic() }   // compile error
     * functions { should().bePublic() }                                    // ok
     * ```
     */
    @Deprecated(FUNCTIONS_MESSAGE, ReplaceWith("functions { }"), level = DeprecationLevel.ERROR)
    public fun Konture.functions(sourceSets: SourceSetSelector, block: FunctionsRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.functions(sourceSets) { }")

    /**
     * Konture's `Konture.properties()`. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.properties().should().beVal()   // compile error
     * properties { should().beVal() }         // ok
     * ```
     */
    @Deprecated(PROPERTIES_MESSAGE, ReplaceWith("properties { }"), level = DeprecationLevel.ERROR)
    public fun Konture.properties(): Nothing = refuse("Konture.properties()")

    /**
     * Konture's `Konture.properties(sourceSets)`. Not usable here — see [files]. The graph katachi builds has one
     * source set, so there is nothing to select.
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.properties(SourceSets.production()).should().beVal()   // compile error
     * properties { should().beVal() }                                // ok
     * ```
     */
    @Deprecated(PROPERTIES_MESSAGE, ReplaceWith("properties { }"), level = DeprecationLevel.ERROR)
    public fun Konture.properties(sourceSets: SourceSetSelector): Nothing = refuse("Konture.properties(sourceSets)")

    /**
     * Konture's `Konture.properties { }`, which runs and asserts at once. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.properties { should().beVal() }   // compile error
     * properties { should().beVal() }           // ok
     * ```
     */
    @Deprecated(PROPERTIES_MESSAGE, ReplaceWith("properties { }"), level = DeprecationLevel.ERROR)
    public fun Konture.properties(block: PropertiesRuleBuilder.() -> Unit): Nothing = refuse("Konture.properties { }")

    /**
     * Konture's `Konture.properties(sourceSets) { }`. Not usable here — see [files].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.properties(SourceSets.production()) { should().beVal() }   // compile error
     * properties { should().beVal() }                                    // ok
     * ```
     */
    @Deprecated(PROPERTIES_MESSAGE, ReplaceWith("properties { }"), level = DeprecationLevel.ERROR)
    public fun Konture.properties(sourceSets: SourceSetSelector, block: PropertiesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.properties(sourceSets) { }")

    /**
     * Konture's `Konture.modules()`. Not usable here, and has no replacement: module rules need Gradle's
     * build graph, which katachi's layout does not describe. Use Konture itself, with its Gradle plugin, in a
     * test module of its own.
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.modules().should() notDependOnModules ":app"   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.modules(): Nothing = refuse("Konture.modules()")

    /**
     * Konture's `Konture.modules(sourceSets)`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.modules(SourceSets.production())   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.modules(sourceSets: SourceSetSelector): Nothing = refuse("Konture.modules(sourceSets)")

    /**
     * Konture's `Konture.modules { }`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.modules { should() notDependOnModules ":app" }   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.modules(block: ModulesRuleBuilder.() -> Unit): Nothing = refuse("Konture.modules { }")

    /**
     * Konture's `Konture.modules(sourceSets) { }`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.modules(SourceSets.production()) { }   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.modules(sourceSets: SourceSetSelector, block: ModulesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.modules(sourceSets) { }")

    /**
     * Konture's `Konture.slices()`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.slices().should().beFreeOfCycles()   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.slices(): Nothing = refuse("Konture.slices()")

    /**
     * Konture's `Konture.slices(sourceSets)`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.slices(SourceSets.production())   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.slices(sourceSets: SourceSetSelector): Nothing = refuse("Konture.slices(sourceSets)")

    /**
     * Konture's `Konture.slices { }`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.slices { should().beFreeOfCycles() }   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.slices(block: SlicesRuleBuilder.() -> Unit): Nothing = refuse("Konture.slices { }")

    /**
     * Konture's `Konture.slices(sourceSets) { }`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.slices(SourceSets.production()) { }   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.slices(sourceSets: SourceSetSelector, block: SlicesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.slices(sourceSets) { }")

    /**
     * Konture's `Konture.layeredArchitecture()`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.layeredArchitecture()   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.layeredArchitecture(): Nothing = refuse("Konture.layeredArchitecture()")

    /**
     * Konture's `Konture.layered { }`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.layered { }   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.layered(block: LayeredArchitectureDsl.() -> Unit): Nothing = refuse("Konture.layered { }")

    /**
     * Konture's `Konture.assertNoCycles()`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.assertNoCycles()   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.assertNoCycles(): Nothing = refuse("Konture.assertNoCycles()")

    /**
     * Konture's `Konture.assertNoCycles(includeTestConfigurations)`. Not usable here — see [modules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.assertNoCycles(includeTestConfigurations = true)   // compile error, nothing to write here
     * ```
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.assertNoCycles(includeTestConfigurations: Boolean): Nothing =
        refuse("Konture.assertNoCycles(includeTestConfigurations)")

    /**
     * Konture's top level `rule(id) { }`. Not usable here: its `RuleBuilder` reads Konture's default graph the
     * moment it is made, and the id and severity it carries never reach katachi's report, which names the
     * constraint instead.
     *
     * ## Example 1: what to write instead
     * ```kt
     * rule("domain.internal") { classes { should().beInternal() } }   // compile error
     * classes { should().beInternal() }                               // ok
     * ```
     */
    @Deprecated(RULE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun rule(id: String, block: RuleBuilder.() -> Unit): Nothing = refuse("rule(id) { }")

    /**
     * Konture's top level `architectureRules(name) { }`. Not usable here: the `RuleSet` it builds can only be
     * run by `check()`, against Konture's default graph.
     *
     * ## Example 1: what to write instead
     * ```kt
     * architectureRules("domain") { classes { should().beInternal() } }   // compile error
     * classes { should().beInternal() }                                   // ok
     * ```
     */
    @Deprecated(RULE_SET_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun architectureRules(name: String? = null, block: RuleSetBuilder.() -> Unit): Nothing =
        refuse("architectureRules(name) { }")

    /**
     * Konture's top level `architectureRules { }`. Not usable here — see [architectureRules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * architectureRules { classes { should().beInternal() } }   // compile error
     * classes { should().beInternal() }                         // ok
     * ```
     */
    @Deprecated(RULE_SET_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun architectureRules(block: RuleSetBuilder.() -> Unit): Nothing = refuse("architectureRules { }")

    /**
     * Konture's `Konture.architectureRules(name) { }`. Not usable here — see [architectureRules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.architectureRules("domain") { classes { should().beInternal() } }   // compile error
     * classes { should().beInternal() }                                           // ok
     * ```
     */
    @Deprecated(RULE_SET_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun Konture.architectureRules(name: String? = null, block: RuleSetBuilder.() -> Unit): Nothing =
        refuse("Konture.architectureRules(name) { }")

    /**
     * Konture's `Konture.architectureRules { }`. Not usable here — see [architectureRules].
     *
     * ## Example 1: what to write instead
     * ```kt
     * Konture.architectureRules { classes { should().beInternal() } }   // compile error
     * classes { should().beInternal() }                                 // ok
     * ```
     */
    @Deprecated(RULE_SET_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun Konture.architectureRules(block: RuleSetBuilder.() -> Unit): Nothing =
        refuse("Konture.architectureRules { }")
}
