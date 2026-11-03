package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.Role

internal class DeclaredFileConstraint(
    val role: Role,
    val paths: List<String>,
    val layoutPath: String?,
    val name: String?,
    val declaredAt: DeclarationSite,
    val coverage: FileConstraintCoverage,
    val range: FileConstraintRange,
    val check: FileConstraint,
) {
    override fun toString(): String =
        "DeclaredFileConstraint(${role.qualifiedName}, ${name ?: declaredAt}, paths=$paths)"
}

// The fallback is the file the constraint was written in, never ".": that is a real path in every project and says
// nothing.
internal val DeclaredFileConstraint.reportPath: String
    get() = paths.firstOrNull { !GlobLexer.hasGlobWildcard(it) } ?: paths.firstOrNull() ?: declaredAt.fileName
