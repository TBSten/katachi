package me.tbsten.katachi.konture.internal

import io.github.baole.konture.ClassDeclaration
import io.github.baole.konture.FileDeclaration
import io.github.baole.konture.ProjectGraph
import io.github.baole.konture.core.model.Subject
import io.github.baole.konture.core.model.Violation
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.konture.KatachiKontureUnlocatableViolationException

// A data class so that one declaration rejected twice collapses to one entry.
internal data class KontureViolationSite(
    val file: String,
    val declaration: String?,
    val line: Int?,
)

// What a violation carries depends on the builder, so the caller passes `kind`: `files` is named after the file at line 1 (a convention; a location equal to the subject's means the whole file).
internal fun siteOf(
    kind: SuiteKind,
    violation: Violation,
    covered: KontureCoveredFiles,
    names: KontureDeclarationNames,
): KontureViolationSite {
    val subject = violation.subject
    val location = violation.sourceLocation ?: subject.location
        ?: throw KatachiKontureUnlocatableViolationException(
            ruleId = violation.ruleId,
            subject = subject.name,
            filePath = null,
        )
    val relative = covered.relativeOf(location.filePath)
        ?: throw KatachiKontureUnlocatableViolationException(
            ruleId = violation.ruleId,
            subject = subject.name,
            filePath = location.filePath,
        )
    val declaration = when (kind) {
        SuiteKind.Files -> null
        SuiteKind.Classes -> names.ofClass(subject)
        SuiteKind.Functions -> names.ofMember(
            subject,
            members = { owner -> owner.functions.map { Member(it.name, it.sourceLine, it.receiverType) } },
            topLevel = { file -> file.topLevelFunctions.map { Member(it.name, it.sourceLine, it.receiverType) } },
        )
        SuiteKind.Properties -> names.ofMember(
            subject,
            members = { owner -> owner.properties.map { Member(it.name, it.sourceLine, it.receiverType) } },
            topLevel = { file -> file.topLevelProperties.map { Member(it.name, it.sourceLine, it.receiverType) } },
        )
    }
    // Konture writes `-1` for a line it does not know. A files violation at the subject's own
    // location is about the whole file; its `line = 1` is Konture's placeholder, not a fact.
    val line = when {
        kind == SuiteKind.Files && violation.sourceLocation == subject.location -> null
        else -> location.line?.takeIf { it > 0 }
    }
    return KontureViolationSite(file = relative, declaration = declaration, line = line)
}

internal fun KontureViolationSite.toFileConstraintFailure(): FileConstraintFailure =
    FileConstraintFailure(file = file, declaration = declaration, line = line)

internal class Member(val name: String, val line: Int?, val receiver: String?)

// Names a declaration the way `ktFileContent` does, without the package (`HomeScreen.State`, `User.id`), so that two
// members of one name in one file stay two baseline entries. Konture names a member after its class's simple name
// alone (`pkg.State.id`, and `.State.id` in the default package); the member's line picks the class out of the graph.
internal class KontureDeclarationNames(private val graph: ProjectGraph) {
    private val files: Map<String, FileDeclaration> by lazy(LazyThreadSafetyMode.NONE) {
        graph.builds.values.flatten().flatMap { it.files }.associateBy { it.filePath }
    }

    fun ofClass(subject: Subject): String {
        val file = fileOf(subject)
            ?: return (subject as? Subject.ClassSubject)?.simpleName ?: subject.name.substringAfterLast('.')
        return subject.name.withoutPackage(file)
    }

    // An extension is named after its receiver (`String.toDto`), so that `String.toDto` and `Int.toDto` of one file stay
    // two baseline entries. The line picks the declaration out of the file: names alone repeat.
    fun ofMember(
        subject: Subject,
        members: (owner: ClassDeclaration) -> List<Member>,
        topLevel: (file: FileDeclaration) -> List<Member>,
    ): String {
        val name = subject.name.substringAfterLast('.')
        val file = fileOf(subject) ?: return name
        val local = subject.name.withoutPackage(file)
        val line = subject.location?.line
        fun List<Member>.written(): String {
            val member = firstOrNull { it.name == name && it.line == line }
                ?: filter { it.name == name }.singleOrNull()
            return member?.receiver?.takeIf { it.isNotEmpty() }?.let { "$it.$name" } ?: name
        }
        val simpleClassName = local.substringBeforeLast('.', missingDelimiterValue = "")
        if (simpleClassName.isEmpty()) return topLevel(file).written()
        val owner = file.classes.firstOrNull { it.name == simpleClassName && members(it).any { m -> m.name == name && m.line == line } }
            ?: return local
        return "${owner.fqName.withoutPackage(file)}.${members(owner).written()}"
    }

    private fun fileOf(subject: Subject): FileDeclaration? = subject.location?.filePath?.let(files::get)
}

private fun String.withoutPackage(file: FileDeclaration): String = removePrefix("${file.packageName}.")
