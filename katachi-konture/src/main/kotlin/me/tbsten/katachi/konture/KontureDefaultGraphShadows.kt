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
 * Inside a `konture { }` block, katachi builds the graph from the covered files and does not
 * build Konture's default graph, so these calls could only fail at run time. Writing one is a
 * compile error whose message names what to write instead.
 *
 * The shadowed calls are `architecture { }`, `rule(id) { }` and `architectureRules { }` with
 * their `Konture.` forms, `Konture.files()` / `classes()` / `functions()` / `properties()` in all
 * their forms, and the module graph rules (`modules`, `slices`, `layeredArchitecture`, `layered`,
 * `assertNoCycles`), which need Konture's Gradle plugin.
 *
 * The scope accessors such as `Konture.scope` are not shadowed: they compile and fail at run time
 * with [KatachiKontureDefaultGraphException]. Members of the `Konture` object itself, such as
 * `Konture.reset()`, compile too; [KontureConstraintScope] says what they do inside a block.
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
     */
    @Deprecated(ARCHITECTURE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun architecture(block: KontureContext.() -> Unit): Nothing = refuse("architecture")

    /** Konture's top level `architecture(graph) { }`. Not usable here — see [architecture]. */
    @Deprecated(ARCHITECTURE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun architecture(graph: ProjectGraph, block: KontureContext.() -> Unit): Nothing = refuse("architecture")

    /** Konture's `Konture.architecture { }`. Not usable here — see [architecture]. */
    @Deprecated(ARCHITECTURE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun Konture.architecture(block: KontureContext.() -> Unit): Nothing = refuse("Konture.architecture")

    /** Konture's `Konture.architecture(graph) { }`. Not usable here — see [architecture]. */
    @Deprecated(ARCHITECTURE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun Konture.architecture(graph: ProjectGraph, block: KontureContext.() -> Unit): Nothing =
        refuse("Konture.architecture")

    /**
     * Konture's `Konture.files()`. Not usable here: it binds the rule to Konture's default graph, which katachi
     * does not build. `files { }` binds the same builder to the covered files.
     */
    @Deprecated(FILES_MESSAGE, ReplaceWith("files { }"), level = DeprecationLevel.ERROR)
    public fun Konture.files(): Nothing = refuse("Konture.files()")

    /**
     * Konture's `Konture.files(sourceSets)`. Not usable here — see [files]. The graph katachi builds has one
     * source set, so there is nothing to select.
     */
    @Deprecated(FILES_MESSAGE, ReplaceWith("files { }"), level = DeprecationLevel.ERROR)
    public fun Konture.files(sourceSets: SourceSetSelector): Nothing = refuse("Konture.files(sourceSets)")

    /** Konture's `Konture.files { }`, which runs and asserts at once. Not usable here — see [files]. */
    @Deprecated(FILES_MESSAGE, ReplaceWith("files { }"), level = DeprecationLevel.ERROR)
    public fun Konture.files(block: FilesRuleBuilder.() -> Unit): Nothing = refuse("Konture.files { }")

    /** Konture's `Konture.files(sourceSets) { }`. Not usable here — see [files]. */
    @Deprecated(FILES_MESSAGE, ReplaceWith("files { }"), level = DeprecationLevel.ERROR)
    public fun Konture.files(sourceSets: SourceSetSelector, block: FilesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.files(sourceSets) { }")

    /** Konture's `Konture.classes()`. Not usable here — see [files]. */
    @Deprecated(CLASSES_MESSAGE, ReplaceWith("classes { }"), level = DeprecationLevel.ERROR)
    public fun Konture.classes(): Nothing = refuse("Konture.classes()")

    /**
     * Konture's `Konture.classes(sourceSets)`. Not usable here — see [files]. The graph katachi builds has one
     * source set, so there is nothing to select.
     */
    @Deprecated(CLASSES_MESSAGE, ReplaceWith("classes { }"), level = DeprecationLevel.ERROR)
    public fun Konture.classes(sourceSets: SourceSetSelector): Nothing = refuse("Konture.classes(sourceSets)")

    /** Konture's `Konture.classes { }`, which runs and asserts at once. Not usable here — see [files]. */
    @Deprecated(CLASSES_MESSAGE, ReplaceWith("classes { }"), level = DeprecationLevel.ERROR)
    public fun Konture.classes(block: ClassesRuleBuilder.() -> Unit): Nothing = refuse("Konture.classes { }")

    /** Konture's `Konture.classes(sourceSets) { }`. Not usable here — see [files]. */
    @Deprecated(CLASSES_MESSAGE, ReplaceWith("classes { }"), level = DeprecationLevel.ERROR)
    public fun Konture.classes(sourceSets: SourceSetSelector, block: ClassesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.classes(sourceSets) { }")

    /** Konture's `Konture.functions()`. Not usable here — see [files]. */
    @Deprecated(FUNCTIONS_MESSAGE, ReplaceWith("functions { }"), level = DeprecationLevel.ERROR)
    public fun Konture.functions(): Nothing = refuse("Konture.functions()")

    /**
     * Konture's `Konture.functions(sourceSets)`. Not usable here — see [files]. The graph katachi builds has one
     * source set, so there is nothing to select.
     */
    @Deprecated(FUNCTIONS_MESSAGE, ReplaceWith("functions { }"), level = DeprecationLevel.ERROR)
    public fun Konture.functions(sourceSets: SourceSetSelector): Nothing = refuse("Konture.functions(sourceSets)")

    /** Konture's `Konture.functions { }`, which runs and asserts at once. Not usable here — see [files]. */
    @Deprecated(FUNCTIONS_MESSAGE, ReplaceWith("functions { }"), level = DeprecationLevel.ERROR)
    public fun Konture.functions(block: FunctionsRuleBuilder.() -> Unit): Nothing = refuse("Konture.functions { }")

    /** Konture's `Konture.functions(sourceSets) { }`. Not usable here — see [files]. */
    @Deprecated(FUNCTIONS_MESSAGE, ReplaceWith("functions { }"), level = DeprecationLevel.ERROR)
    public fun Konture.functions(sourceSets: SourceSetSelector, block: FunctionsRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.functions(sourceSets) { }")

    /** Konture's `Konture.properties()`. Not usable here — see [files]. */
    @Deprecated(PROPERTIES_MESSAGE, ReplaceWith("properties { }"), level = DeprecationLevel.ERROR)
    public fun Konture.properties(): Nothing = refuse("Konture.properties()")

    /**
     * Konture's `Konture.properties(sourceSets)`. Not usable here — see [files]. The graph katachi builds has one
     * source set, so there is nothing to select.
     */
    @Deprecated(PROPERTIES_MESSAGE, ReplaceWith("properties { }"), level = DeprecationLevel.ERROR)
    public fun Konture.properties(sourceSets: SourceSetSelector): Nothing = refuse("Konture.properties(sourceSets)")

    /** Konture's `Konture.properties { }`, which runs and asserts at once. Not usable here — see [files]. */
    @Deprecated(PROPERTIES_MESSAGE, ReplaceWith("properties { }"), level = DeprecationLevel.ERROR)
    public fun Konture.properties(block: PropertiesRuleBuilder.() -> Unit): Nothing = refuse("Konture.properties { }")

    /** Konture's `Konture.properties(sourceSets) { }`. Not usable here — see [files]. */
    @Deprecated(PROPERTIES_MESSAGE, ReplaceWith("properties { }"), level = DeprecationLevel.ERROR)
    public fun Konture.properties(sourceSets: SourceSetSelector, block: PropertiesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.properties(sourceSets) { }")

    /**
     * Konture's `Konture.modules()`. Not usable here, and has no replacement: module rules need Gradle's
     * build graph, which katachi's layout does not describe. Use Konture itself, with its Gradle plugin, in a
     * test module of its own.
     */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.modules(): Nothing = refuse("Konture.modules()")

    /** Konture's `Konture.modules(sourceSets)`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.modules(sourceSets: SourceSetSelector): Nothing = refuse("Konture.modules(sourceSets)")

    /** Konture's `Konture.modules { }`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.modules(block: ModulesRuleBuilder.() -> Unit): Nothing = refuse("Konture.modules { }")

    /** Konture's `Konture.modules(sourceSets) { }`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.modules(sourceSets: SourceSetSelector, block: ModulesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.modules(sourceSets) { }")

    /** Konture's `Konture.slices()`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.slices(): Nothing = refuse("Konture.slices()")

    /** Konture's `Konture.slices(sourceSets)`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.slices(sourceSets: SourceSetSelector): Nothing = refuse("Konture.slices(sourceSets)")

    /** Konture's `Konture.slices { }`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.slices(block: SlicesRuleBuilder.() -> Unit): Nothing = refuse("Konture.slices { }")

    /** Konture's `Konture.slices(sourceSets) { }`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.slices(sourceSets: SourceSetSelector, block: SlicesRuleBuilder.() -> Unit): Nothing =
        refuse("Konture.slices(sourceSets) { }")

    /** Konture's `Konture.layeredArchitecture()`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.layeredArchitecture(): Nothing = refuse("Konture.layeredArchitecture()")

    /** Konture's `Konture.layered { }`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.layered(block: LayeredArchitectureDsl.() -> Unit): Nothing = refuse("Konture.layered { }")

    /** Konture's `Konture.assertNoCycles()`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.assertNoCycles(): Nothing = refuse("Konture.assertNoCycles()")

    /** Konture's `Konture.assertNoCycles(includeTestConfigurations)`. Not usable here — see [modules]. */
    @Deprecated(MODULE_GRAPH_MESSAGE, level = DeprecationLevel.ERROR)
    public fun Konture.assertNoCycles(includeTestConfigurations: Boolean): Nothing =
        refuse("Konture.assertNoCycles(includeTestConfigurations)")

    /**
     * Konture's top level `rule(id) { }`. Not usable here: its `RuleBuilder` reads Konture's default graph the
     * moment it is made, and the id and severity it carries never reach katachi's report, which names the
     * constraint instead.
     */
    @Deprecated(RULE_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun rule(id: String, block: RuleBuilder.() -> Unit): Nothing = refuse("rule(id) { }")

    /**
     * Konture's top level `architectureRules(name) { }`. Not usable here: the `RuleSet` it builds can only be
     * run by `check()`, against Konture's default graph.
     */
    @Deprecated(RULE_SET_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun architectureRules(name: String? = null, block: RuleSetBuilder.() -> Unit): Nothing =
        refuse("architectureRules(name) { }")

    /** Konture's top level `architectureRules { }`. Not usable here — see [architectureRules]. */
    @Deprecated(RULE_SET_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun architectureRules(block: RuleSetBuilder.() -> Unit): Nothing = refuse("architectureRules { }")

    /** Konture's `Konture.architectureRules(name) { }`. Not usable here — see [architectureRules]. */
    @Deprecated(RULE_SET_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun Konture.architectureRules(name: String? = null, block: RuleSetBuilder.() -> Unit): Nothing =
        refuse("Konture.architectureRules(name) { }")

    /** Konture's `Konture.architectureRules { }`. Not usable here — see [architectureRules]. */
    @Deprecated(RULE_SET_MESSAGE, ReplaceWith("block()"), level = DeprecationLevel.ERROR)
    public fun Konture.architectureRules(block: RuleSetBuilder.() -> Unit): Nothing =
        refuse("Konture.architectureRules { }")
}
