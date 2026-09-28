package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.ModuleMiss
import me.tbsten.katachi.template.KatachiAmbiguousTemplatePlacementException
import me.tbsten.katachi.template.KatachiNoTemplatePlacementException
import me.tbsten.katachi.template.KatachiTemplateModuleNotFoundException
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
    /** The last segment of the pattern, compiled. */
    val nameGlob: Glob,
)

/**
 * The layout a run's files are placed by: one role's entries, and the module captures whose
 * values picked no module while they were flattened.
 *
 * Usually the declarations alone. When the run gave values to a module capture the entries are
 * flattened again against the modules that exist, since which directory, `modulePackage` and
 * `wildcard(...)` file names a module has is only known once it is one module. See
 * [me.tbsten.katachi.dsl.internal.ModuleIndex.boundTo].
 */
internal class PlacementLayout(
    val entries: List<LayoutEntry>,
    val misses: List<ModuleMiss> = emptyList(),
)

/**
 * Where [fileName] goes, as a project-root relative path, according to [role]'s `layout { }`.
 *
 * A template names files and the layout names directories, so this is the one place the two meet.
 * The match is `Glob.matches` and nothing else: the generated name is compared against the last
 * segment of every file pattern the role declared, and the directory of the one that matches is
 * the answer. Nothing is invented -- a name no pattern accepts has no place to go, and a name two
 * different directories accept is a question katachi refuses to answer on the user's behalf.
 *
 * A directory named with `capture("...")` is filled in from [values]; one that is a `*` without a
 * name names no single directory and is not a place. The captures decide between places as well:
 *
 * - a place reached through a capture the run gave a value wins over the places that name no
 *   capture -- `--arg x=login` asked for `scenario/login/`, not for a `scenario/common/` beside it;
 * - a place that needs a capture the run gave no value fails the file, even when a place without
 *   captures would take it: writing to that one instead would drop the forgotten `--arg` without
 *   a word ([missingCaptures] builds the exception).
 *
 * A module capture whose value picks no existing module ([PlacementLayout.misses]) fails the file
 * whatever else would take it.
 *
 * @param patternNameRewrite How the last segment of a declared pattern reads before it is
 *   compiled as a glob. Identity for a real run, where the declared text is already what a real
 *   module's `wildcard(...)` built it out of. A preview passes one instead: its own candidate
 *   name and the declared pattern spell the same capture two different ways -- `wildcard(...)`
 *   as `<name>`, `captureValue(...)` as its own placeholder -- and neither survives being
 *   compared to the other as text. Rewriting both to the same glob-safe token first lets the
 *   match run as if one real value had picked both.
 */
internal fun placeTemplateFile(
    role: Role,
    layout: PlacementLayout,
    fileName: String,
    declaredAt: DeclarationSite,
    values: Map<String, String> = emptyMap(),
    missingCaptures: (names: List<String>) -> Throwable = { names ->
        missingCaptureException(role, layout.entries, names, declaredAt, fileName)
    },
    patternNameRewrite: (String) -> String = { it },
): String {
    requireCreatableFileName(role, fileName, declaredAt)

    val places = filePlacesOf(role, layout.entries, patternNameRewrite)
    val matching = places.filter { it.nameGlob.matches(fileName) }
    if (matching.isEmpty()) {
        // A module capture without its value leaves the names a layout builds out of
        // `wildcard(...)` as placeholders, which no real name matches: the value is what is wrong.
        val unfilled = unfilledModuleCaptures(role, layout.entries, values)
        if (unfilled.isNotEmpty()) throw missingCaptures(unfilled)
        layout.misses.firstOrNull()?.let { throw moduleNotFound(role, it, fileName, declaredAt) }
        throw KatachiNoTemplatePlacementException(
            role = role.qualifiedName,
            fileName = fileName,
            declaredPatterns = places.map { it.entry.path }.distinct().sorted(),
            declaredAt = declaredAt,
        )
    }

    // Places reached through a capture the run gave a value, and places that name none.
    val captured = LinkedHashSet<String>()
    val plain = LinkedHashSet<String>()
    val missing = LinkedHashSet<String>()
    val unnamed = LinkedHashSet<String>()
    for (place in matching) {
        for (variant in place.entry.captureVariants.ifEmpty { listOf(LayoutCaptures.NONE) }) {
            val needed = variant.names.filter { it !in values }
            if (needed.isNotEmpty()) {
                missing += needed
                continue
            }
            val filled = fillCapturedPath(place.entry.path, variant, values)
            val directory = filled.substringBeforeLast('/', missingDelimiterValue = "")
            // Such an entry says where a *kind* of directory keeps this role, not where one file
            // goes: which ones exist is a question only a walk of the project can answer.
            if (directory.isNotEmpty() && Glob.compile(directory, Glob.PATH_SEPARATOR).hasWildcard) {
                unnamed += filled
                continue
            }
            val path = if (directory.isEmpty()) fileName else "$directory/$fileName"
            if (variant.names.isEmpty()) plain += path else captured += path
        }
    }

    // A value the run gave a module capture that picks no module is refused even when another
    // place still takes the file: the value was passed on purpose, and generating somewhere else
    // would drop it without a word -- `--arg feature=hoem` landing in `:app` instead of failing.
    layout.misses.firstOrNull()?.let { throw moduleNotFound(role, it, fileName, declaredAt) }

    val chosen = when {
        captured.isNotEmpty() -> captured
        missing.isNotEmpty() -> throw missingCaptures(missing.toList())
        else -> plain
    }

    // Two entries that resolve to the same path are one place said twice -- a role may declare
    // `useCase/*.kt` and `useCase/*UseCase.kt` and mean one directory. Only two *different*
    // directories are a question.
    val paths = chosen.sorted()
    if (paths.size > 1) {
        throw KatachiAmbiguousTemplatePlacementException(
            role = role.qualifiedName,
            fileName = fileName,
            candidates = paths,
            declaredAt = declaredAt,
        )
    }
    if (paths.size == 1) {
        val path = paths.single()
        requireInsideProject(role, path, declaredAt)
        return path
    }

    throw KatachiWildcardTemplatePlacementException(
        role = role.qualifiedName,
        fileName = fileName,
        patterns = unnamed.sorted(),
        declaredAt = declaredAt,
    )
}

/** The names of module captures of [role]'s file patterns that [values] gives no value, in declaration order. */
private fun unfilledModuleCaptures(
    role: Role,
    entries: List<LayoutEntry>,
    values: Map<String, String>,
): List<String> {
    val unfilled = LinkedHashSet<String>()
    for (place in filePlacesOf(role, entries)) {
        for (variant in place.entry.captureVariants) {
            unfilled += variant.moduleCapture?.names.orEmpty().filter { it !in values }
        }
    }
    return unfilled.toList()
}

private fun moduleNotFound(role: Role, miss: ModuleMiss, fileName: String, declaredAt: DeclarationSite) =
    KatachiTemplateModuleNotFoundException(
        role = role.qualifiedName,
        fileName = fileName,
        modulePattern = miss.modulePattern,
        captureNames = miss.captureNames,
        modulePath = miss.modulePath,
        existing = miss.existing,
        existingArgs = miss.existingValues.map { values ->
            miss.captureNames.zip(values).joinToString(" ") { (name, value) -> "--arg $name=$value" }
        },
        declaredAt = declaredAt,
    )

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
private fun filePlacesOf(
    role: Role,
    entries: List<LayoutEntry>,
    patternNameRewrite: (String) -> String = { it },
): List<FilePlace> = entries
    .filter { it.role === role && it.kind == LayoutEntryKind.File && !it.synthetic }
    .map { entry ->
        FilePlace(
            entry = entry,
            nameGlob = Glob.compile(patternNameRewrite(entry.path.substringAfterLast('/')), Glob.PATH_SEPARATOR),
        )
    }

/**
 * Characters a generated file name may not hold.
 *
 * `*`, `?`, `[`, `]`, `{` and `}` are katachi's glob metacharacters or the ones it rejects, so a
 * name holding them would be compared against the layout as a literal and then created on disk as
 * a file nothing can name back. The rest are what Windows refuses outright; katachi's own check
 * has to give the same answer on every platform, so they are refused everywhere. Control characters
 * and line separators are refused beside these, by [isUncreatableInName].
 *
 * `/`, `\`, a newline and a NUL are not listed because `TemplateScope.file` already refuses them:
 * the string is interpolated before `file(...)` sees it, so that check reads the finished name.
 */
internal const val UNCREATABLE_CHARACTERS: String = "*?[]{}:\"<>|"

/** Refuses a rendered name that cannot safely become a file. */
private fun requireCreatableFileName(role: Role, fileName: String, declaredAt: DeclarationSite) {
    val offending = fileName.filter(::isUncreatableInName).toSet()
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
