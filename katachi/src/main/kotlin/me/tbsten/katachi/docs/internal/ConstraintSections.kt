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
import java.util.IdentityHashMap

/** The file a section is worked out for when the role has no example and the project no file. */
private const val PLACEHOLDER_FILE_NAME: String = "Sample.kt"

/** What a role example without an extension is read as: the name of a Kotlin file. */
private const val KOTLIN_EXTENSION: String = ".kt"

/** One section a constraint wrote about itself, as the page holds it. */
internal class ConstraintSection(val markdown: String)

/**
 * One entry of a role page's `## Constraints` list: a named constraint.
 *
 * [section] is the section that constraint wrote, or `null` when it wrote none. A constraint
 * that was folded into another -- the same check handed to several modules -- points at the
 * section it was folded into. The page turns the section into a link to its first heading (see
 * [rolePage]): the id of a heading depends on the other headings of the page, which only the page
 * knows.
 */
internal class ConstraintListItem(val name: String, val section: ConstraintSection?)

/** What a role's constraints put on its page: the list entries and the sections they wrote. */
internal class ConstraintDocumentation(
    val items: List<ConstraintListItem>,
    val sections: List<ConstraintSection>,
)

/**
 * What [constraints] -- the constraints of [role], in the order they were written -- put on the
 * role's page: one list item per named declaration, and the sections, empty ones left out. See
 * [FileConstraint.documentSection].
 *
 * One section per constraint, not per declaration of it: a constraint written inside a wildcard
 * module key can be handed out once per module, and the same check would otherwise describe
 * itself once for each. Handed out means either the same check (one `fileConstraint(check = x)`
 * given to every module) or a check of its own per module (a block that builds its check, such as
 * `ktFileContent { }`, run once per module) -- the latter is recognised by being written at the
 * same place under the same name. Declarations folded into one are listed once: the list item of a
 * name is not repeated for each module it was handed to, and it links to the section it was folded
 * into. Whatever a constraint throws is not caught
 * -- a constraint that cannot describe itself is a mistake in the definition, the kind the page
 * generation stops on.
 */
internal fun constraintSectionsOf(
    context: ArchitectureProcessContext<*>,
    role: Role,
    constraints: List<DeclaredFileConstraint>,
): ConstraintDocumentation {
    // `FileConstraint` implementations need not declare `equals`, and two lambdas that happen to
    // compare equal are still two constraints, so this is a set of identities.
    val firstByCheck: MutableMap<FileConstraint, DeclaredFileConstraint> = IdentityHashMap()
    val firstByPlace = HashMap<Pair<String?, DeclarationSite>, DeclaredFileConstraint>()
    val distinct = ArrayList<DeclaredFileConstraint>()
    // The declaration whose section stands for each one: itself when distinct, else the one it
    // was folded into by check or by place.
    val standingFor = IdentityHashMap<DeclaredFileConstraint, DeclaredFileConstraint?>()
    for (declared in constraints) {
        val byCheck = firstByCheck.putIfAbsent(declared.check, declared)
        val place = declared.name to declared.declaredAt
        val byPlace = if (declared.declaredAt == DeclarationSite.Unknown) null else firstByPlace.putIfAbsent(place, declared)
        if (byCheck == null && byPlace == null) {
            distinct += declared
            standingFor[declared] = declared
        } else {
            standingFor[declared] = listOfNotNull(byCheck, byPlace).firstOrNull { it in distinct }
        }
    }
    val sectionOf = IdentityHashMap<DeclaredFileConstraint, ConstraintSection>()
    val sections = distinct.mapNotNull { declared ->
        val documentContext = FileConstraintDocumentContext(
            role = role,
            name = declared.name,
            declaredAt = declared.declaredAt,
            captureNames = declared.coverage.captureNames,
            exampleFile = { exampleFileOf(context, role, declared.coverage) },
        )
        declared.check.documentSection(documentContext)?.let(::sectionBody)?.let(::ConstraintSection)
            ?.also { sectionOf[declared] = it }
    }
    // A declaration folded into another with the same name is the same entry again; one with a
    // different name (the same check named twice) is a different entry, as it reads differently.
    val listed = ArrayList<Pair<String, DeclaredFileConstraint?>>()
    val items = ArrayList<ConstraintListItem>()
    for (declared in constraints) {
        val name = declared.name ?: continue
        val stands = standingFor[declared]
        if (listed.any { (listedName, listedStands) -> listedName == name && listedStands === stands }) continue
        listed += name to stands
        items += ConstraintListItem(name, stands?.let { sectionOf[it] })
    }
    return ConstraintDocumentation(items, sections)
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
