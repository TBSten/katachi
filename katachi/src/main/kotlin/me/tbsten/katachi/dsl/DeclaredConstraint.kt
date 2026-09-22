package me.tbsten.katachi.dsl

import me.tbsten.katachi.InternalKatachiApi

/**
 * One constraint, with the layout around it already evaluated.
 *
 * A [ConstraintDeclaration] says what the user wrote; this says what it turned out to be
 * about. A wildcard module key produces one of these per module it expanded to, each with its
 * own resolved [layoutPath], which is why this is the value the check works with and the
 * declaration is not.
 *
 * Nothing here has been run. The block is kept as it was written and evaluated only when a
 * check that evaluates constraints is handed to `assert(...)`.
 *
 * ## Example 1: read back what a definition declared, without running anything
 * ```kt
 * projectArchitecture.process { model ->
 *     model.declaredConstraints.map { "${it.role.qualifiedName} ${it.name}" }
 * }
 * ```
 */
@InternalKatachiApi
public class DeclaredConstraint internal constructor(
    /**
     * The role whose layout this constraint was written in.
     *
     * ## Example 1: group the declared constraints by role
     * ```kt
     * declaredConstraints.groupBy { it.role.qualifiedName }
     * ```
     */
    public val role: Role,
    /**
     * The resolved directories this constraint is anchored at, outermost first.
     *
     * One entry for a constraint written in a directory block, and possibly several for one
     * written directly on a role, whose `layout { }` blocks may sit far apart. Empty only when
     * the layout declared nothing at all.
     *
     * ## Example 1: name the places a constraint is about
     * ```kt
     * declaredConstraints.single().paths shouldBe listOf("core/domain/useCase")
     * ```
     */
    public val paths: List<String>,
    /**
     * The block's own resolved directory, or `null` when the constraint covers more than one
     * place — written on the role itself, or directly under `layout { }`.
     *
     * Resolved, never the key as written: `":feature:*"` is evaluated once per feature module,
     * so printing the key would give every one of them the same context line.
     *
     * ## Example 1: tell a per-directory constraint from a role-wide one
     * ```kt
     * declaredConstraints.filter { it.layoutPath != null }
     * ```
     */
    public val layoutPath: String?,
    /**
     * What the constraint was called, or `null` when it was declared without a name.
     *
     * ## Example 1: list the named constraints of a definition
     * ```kt
     * declaredConstraints.mapNotNull { it.name }
     * ```
     */
    public val name: String?,
    /**
     * Where `constraint(...)` was written.
     *
     * ## Example 1: point a report back at the line that declared the constraint
     * ```kt
     * declaredConstraints.single().declaredAt.fileName shouldBe "ProjectArchitecture.kt"
     * ```
     */
    public val declaredAt: DeclarationSite,
    /** Which files this constraint covers. See [ConstraintCoverage]. */
    internal val coverage: ConstraintCoverage,
    /** The block itself. Evaluated by a check, not by the DSL. */
    internal val check: FileSetConstraint,
) {
    override fun toString(): String =
        "DeclaredConstraint(${role.qualifiedName}, ${name ?: declaredAt}, paths=$paths)"
}

/**
 * The path a report block opens with.
 *
 * A path without a wildcard is preferred, because an agent reading the report can open it as
 * it is written. When the layout declared nothing at all there is no path to offer, and the
 * fallback is the file the constraint was written in — never `"."`, which is a real path in
 * every project and says nothing about where to look.
 */
internal val DeclaredConstraint.reportPath: String
    get() = paths.firstOrNull { '*' !in it } ?: paths.firstOrNull() ?: declaredAt.fileName
