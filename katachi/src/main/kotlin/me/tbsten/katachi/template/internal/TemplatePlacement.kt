package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.template.KatachiAmbiguousTemplatePlacementException
import me.tbsten.katachi.template.KatachiNoTemplatePlacementException
import me.tbsten.katachi.template.KatachiTemplatePathOutsideProjectException
import me.tbsten.katachi.template.KatachiUnsafeTemplateFileNameException
import me.tbsten.katachi.template.KatachiWildcardTemplatePlacementException

/**
 * One declared file pattern of a role, split the way generation has to read it: the directory a
 * file would land in, and the pattern its name has to satisfy.
 */
private class FilePlace(
    /** The flattened entry this came from, kept so a message can print the pattern as written. */
    val entry: LayoutEntry,
    /** Everything before the last `/` of the pattern; empty when it sits at the project root. */
    val directory: String,
    /** The last segment of the pattern, compiled. */
    val nameGlob: Glob,
) {
    /**
     * Whether the directory still holds a `*` or a `**`.
     *
     * Such an entry says where a *kind* of module keeps this role, not where one file goes: which
     * modules exist is a question only a walk of the project can answer, and `declaredEntries`
     * deliberately does not ask it.
     */
    val directoryIsPattern: Boolean =
        directory.isNotEmpty() && Glob.compile(directory, Glob.PATH_SEPARATOR).hasWildcard

    fun resolve(fileName: String): String =
        if (directory.isEmpty()) fileName else "$directory/$fileName"
}

/**
 * Where [fileName] goes, as a project-root relative path, according to [role]'s `layout { }`.
 *
 * A template names files and the layout names directories, so this is the one place the two meet.
 * The match is `Glob.matches` and nothing else: the generated name is compared against the last
 * segment of every file pattern the role declared, and the directory of the one that matches is
 * the answer. Nothing is invented -- a name no pattern accepts has no place to go, and a name two
 * different directories accept is a question katachi refuses to answer on the user's behalf.
 */
internal fun placeTemplateFile(
    role: Role,
    entries: List<LayoutEntry>,
    fileName: String,
    declaredAt: DeclarationSite,
): String {
    requireCreatableFileName(role, fileName, declaredAt)

    val places = filePlacesOf(role, entries)
    val matching = places.filter { it.nameGlob.matches(fileName) }
    if (matching.isEmpty()) {
        throw KatachiNoTemplatePlacementException(
            role = role.qualifiedName,
            fileName = fileName,
            declaredPatterns = places.map { it.entry.path }.distinct().sorted(),
            declaredAt = declaredAt,
        )
    }

    val concrete = matching.filterNot { it.directoryIsPattern }
    if (concrete.isEmpty()) {
        throw KatachiWildcardTemplatePlacementException(
            role = role.qualifiedName,
            fileName = fileName,
            patterns = matching.map { it.entry.path }.distinct().sorted(),
            declaredAt = declaredAt,
        )
    }

    // Two entries that resolve to the same path are one place said twice -- a role may declare
    // `useCase/*.kt` and `useCase/*UseCase.kt` and mean one directory. Only two *different*
    // directories are a question.
    val paths = concrete.map { it.resolve(fileName) }.distinct().sorted()
    if (paths.size > 1) {
        throw KatachiAmbiguousTemplatePlacementException(
            role = role.qualifiedName,
            fileName = fileName,
            candidates = paths,
            declaredAt = declaredAt,
        )
    }

    val path = paths.single()
    requireInsideProject(role, path, declaredAt)
    return path
}

/**
 * The file patterns [role] declared, in declaration order.
 *
 * Three kinds of entry are left out, and each for its own reason. A plain `Directory` claims no
 * file. An `AnyFile` or an `Ignore` says a directory is *not* checked, which is the opposite of
 * "this is where files of this role belong" -- generating into one would put a file somewhere the
 * layout has stopped describing. And a `synthetic` entry is the `build/` and `build.gradle.kts`
 * pair a `module { }` block injects, which is katachi's own doing rather than the user's.
 *
 * Identity comparison on the role, the way `ProjectWalk` does it: [Role] declares no `equals`, so
 * two roles are the same role only when they are the same object.
 */
private fun filePlacesOf(role: Role, entries: List<LayoutEntry>): List<FilePlace> = entries
    .filter { it.role === role && it.kind == LayoutEntryKind.File && !it.synthetic }
    .map { entry ->
        FilePlace(
            entry = entry,
            directory = entry.path.substringBeforeLast('/', missingDelimiterValue = ""),
            nameGlob = Glob.compile(entry.path.substringAfterLast('/'), Glob.PATH_SEPARATOR),
        )
    }

/**
 * Characters a generated file name may not hold.
 *
 * `*`, `?`, `[`, `]`, `{` and `}` are katachi's glob metacharacters or the ones it rejects, so a
 * name holding them would be compared against the layout as a literal and then created on disk as
 * a file nothing can name back. The rest are what Windows refuses outright; katachi's own check
 * has to give the same answer on every platform, so they are refused everywhere.
 *
 * `/`, `\`, a newline and a NUL are not listed because `TemplateScope.file` already refuses them:
 * the string is interpolated before `file(...)` sees it, so that check reads the finished name.
 */
private const val UNCREATABLE_CHARACTERS: String = "*?[]{}:\"<>|"

/** Refuses a rendered name that cannot safely become a file. */
private fun requireCreatableFileName(role: Role, fileName: String, declaredAt: DeclarationSite) {
    val offending = fileName.filter { it in UNCREATABLE_CHARACTERS || it < ' ' }.toSet()
    if (offending.isEmpty()) return
    throw KatachiUnsafeTemplateFileNameException(
        role = role.qualifiedName,
        fileName = fileName,
        characters = offending.map(Char::toString).sorted(),
        declaredAt = declaredAt,
    )
}

/**
 * Refuses a resolved path that leaves the project.
 *
 * [LayoutEntry.path] is documented as relative to the project root, and a template's own file name
 * can hold no separator, so reaching this means a `layout { }` block named a directory that climbs
 * out. It is checked rather than trusted because what is about to be written is the user's own
 * source tree: everything else katachi writes lands below `build/`, and that net is gone here.
 */
private fun requireInsideProject(role: Role, path: String, declaredAt: DeclarationSite) {
    val climbs = path.startsWith('/') || path.split('/').any { it == ".." }
    if (!climbs) return
    throw KatachiTemplatePathOutsideProjectException(
        role = role.qualifiedName,
        path = path,
        declaredAt = declaredAt,
    )
}
