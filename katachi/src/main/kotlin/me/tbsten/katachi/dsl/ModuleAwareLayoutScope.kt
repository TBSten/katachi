package me.tbsten.katachi.dsl

/**
 * What a layout scope *knows* while it is being evaluated.
 *
 * [LayoutScope] is the other half, and the one a layout block is written against: what a
 * layout *says* — a directory, a file, `/`, and stopping the check. That vocabulary is the
 * same whichever project is being described, and it holds nothing about the project it is
 * describing.
 *
 * Declared here is what the scope carries with it while a block is being run against one
 * concrete project: the module the block is currently being evaluated for, what that module
 * path's wildcards captured, and the ability to resolve a module path against the project's
 * modules. `"...".ktFile()` needs none of it — it is written against `file()` alone — but the
 * Gradle vocabulary in `me.tbsten.katachi.dsl.gradle` cannot be written without it: expanding
 * `":feature:*"` needs the project's module index, and `modulePackage` and `wildcards` need
 * the module at hand.
 *
 * Keeping the two apart is what lets that utility layer depend on a contract instead of on the
 * class implementing it, and keeps [LayoutScope] itself down to the vocabulary a project
 * writing its own `"...".protoFile()` has to read.
 *
 * A utility layer reaches this through the extensions in `me.tbsten.katachi.dsl.gradle`
 * ([me.tbsten.katachi.dsl.gradle.currentModulePath],
 * [me.tbsten.katachi.dsl.gradle.currentWildcards] and
 * [me.tbsten.katachi.dsl.gradle.expandModulePath]) rather than by asking for this type: a
 * `context(layoutScope: LayoutScope)` function is what both katachi's own utilities and a
 * project's are written as, and those extensions are what bridges it to here.
 */
@InternalKatachiApi
public interface ModuleAwareLayoutScope : LayoutScope {
    /**
     * The module this scope is being evaluated for, as katachi prints it (`":feature:home"`),
     * or `null` directly under `layout { }` and inside a plain directory block.
     */
    public val currentModulePath: String?

    /**
     * What the module path's wildcards captured for the module being evaluated, or `null`
     * outside a module block, where there is no module path to have captured anything.
     */
    public val currentWildcards: List<String>?

    /**
     * Runs [block] once per module [modulePath] stands for, below that module's own directory.
     *
     * Each expansion is given the two lines every Gradle module has — `"build".ignore()` and
     * `"build.gradle".ktsFile()` — before [block] runs, so what this declares is the same
     * declaration a hand written directory block would make.
     *
     * @param modulePath a Gradle module path, possibly holding `*` or `**`.
     * @throws me.tbsten.katachi.check.KatachiGlobSyntaxException when the module path cannot
     *   be read.
     * @throws KatachiModuleOutsideLayoutRootException when this scope is not the root of a
     *   `layout { }` block: a module path is resolved below the project root, so a directory
     *   around it would quietly be prepended to the answer.
     */
    public fun expandModulePath(modulePath: String, block: LayoutDirectoryScope.() -> Unit): LayoutModule
}
