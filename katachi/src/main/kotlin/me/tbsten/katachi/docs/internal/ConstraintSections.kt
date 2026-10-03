package me.tbsten.katachi.docs.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintDocumentContext
import me.tbsten.katachi.dsl.FileConstraintExampleFile
import me.tbsten.katachi.dsl.FileConstraintExampleSource
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.DeclaredFileConstraint
import me.tbsten.katachi.dsl.internal.FileConstraintCoverage
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.ArchitectureProcessContext
import java.util.Collections
import java.util.IdentityHashMap

/** The file a section is worked out for when the role has no example and the project no file. */
private const val PLACEHOLDER_FILE_NAME: String = "Sample.kt"

/** What a role example without an extension is read as: the name of a Kotlin file. */
private const val KOTLIN_EXTENSION: String = ".kt"

/**
 * The sections [constraints] -- the constraints of [role], in the order they were written -- put
 * on the role's page, empty ones left out. See [FileConstraint.documentSection].
 *
 * One section per constraint, not per declaration of it: a constraint written inside a wildcard
 * module key can be handed out once per module, and the same check would otherwise describe
 * itself once for each. Handed out means either the same check (one `fileConstraint(check = x)`
 * given to every module) or a check of its own per module (a block that builds its check, such as
 * `ktFileContent { }`, run once per module) -- the latter is recognised by being written at the
 * same place under the same name. Whatever a constraint throws is not caught -- a constraint that
 * cannot describe itself is a mistake in the definition, the kind the page generation stops on.
 */
internal fun constraintSectionsOf(
    context: ArchitectureProcessContext<*>,
    role: Role,
    constraints: List<DeclaredFileConstraint>,
): List<String> {
    // `FileConstraint` implementations need not declare `equals`, and two lambdas that happen to
    // compare equal are still two constraints, so this is a set of identities.
    val seen: MutableSet<FileConstraint> = Collections.newSetFromMap(IdentityHashMap())
    val written = HashSet<Pair<String?, DeclarationSite>>()
    val distinct = constraints.filter { declared ->
        val isNewCheck = seen.add(declared.check)
        val isNewPlace = declared.declaredAt == DeclarationSite.Unknown || written.add(declared.name to declared.declaredAt)
        isNewCheck && isNewPlace
    }
    return distinct.mapNotNull { declared ->
        val documentContext = FileConstraintDocumentContext(
            role = role,
            name = declared.name,
            declaredAt = declared.declaredAt,
            captureNames = declared.coverage.captureNames,
            exampleFile = { exampleFileOf(context, role, declared.coverage) },
        )
        declared.check.documentSection(documentContext)?.let(::sectionBody)
    }
}

/**
 * [section] as a page holds it: the blank lines around it dropped, so that one blank line
 * separates it from the section before it however it was written. `null` for a blank section,
 * which says nothing and gets no place on the page.
 */
private fun sectionBody(section: String): String? =
    section.lines().dropWhile { it.isBlank() }.joinToString("\n").trimEnd().ifEmpty { null }

/**
 * The file a section shows its rules for: the role's first example, the first file of the
 * project [coverage] covers, or a stand-in. See [FileConstraintDocumentContext.exampleFile].
 *
 * The project is listed only on the second branch, and a project that cannot be listed -- no
 * root, a tree that refuses to be read -- falls through to the stand-in rather than stopping the
 * page: the example is an illustration, and the rules it illustrates come from the definition.
 */
private fun exampleFileOf(
    context: ArchitectureProcessContext<*>,
    role: Role,
    coverage: FileConstraintCoverage,
): FileConstraintExampleFile {
    val example = role[Examples].orEmpty().firstOrNull()
    if (example != null) {
        val written = example.name.substringAfterLast('/')
        val fileName = if ('.' in written) written else written + KOTLIN_EXTENSION
        return FileConstraintExampleFile(
            fileName = fileName,
            path = null,
            captureValues = coverage.captureValuesOfFileName(fileName),
            source = FileConstraintExampleSource.RoleExample,
        )
    }
    val matched = catching { context.filesOf(role).firstOrNull { coverage.covers(it) } }.getOrNull()
    if (matched != null) {
        return FileConstraintExampleFile(
            fileName = matched.substringAfterLast('/'),
            path = matched,
            captureValues = coverage.captureValuesOf(matched),
            source = FileConstraintExampleSource.MatchedFile,
        )
    }
    return FileConstraintExampleFile(
        fileName = PLACEHOLDER_FILE_NAME,
        path = null,
        captureValues = emptyMap(),
        source = FileConstraintExampleSource.Placeholder,
    )
}
