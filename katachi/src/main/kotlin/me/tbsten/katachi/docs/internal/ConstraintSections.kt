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

private const val PLACEHOLDER_FILE_NAME: String = "Sample.kt"

private const val KOTLIN_EXTENSION: String = ".kt"

internal class ConstraintSection(val markdown: String)

internal class ConstraintListItem(val name: String, val section: ConstraintSection?)

internal class ConstraintDocumentation(
    val items: List<ConstraintListItem>,
    val sections: List<ConstraintSection>,
)

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

private fun sectionBody(section: String): String? =
    section.lines().dropWhile { it.isBlank() }.joinToString("\n").trimEnd().ifEmpty { null }

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
