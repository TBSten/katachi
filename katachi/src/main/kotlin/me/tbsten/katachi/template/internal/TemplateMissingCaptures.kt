package me.tbsten.katachi.template.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.ModulePattern
import me.tbsten.katachi.dsl.internal.fillCapturedPath
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException

private class CaptureDeclaration(val entry: LayoutEntry, val variant: LayoutCaptures)

private fun declarationOf(entries: List<LayoutEntry>, name: String): CaptureDeclaration? {
    for (entry in entries) {
        if (entry.kind != LayoutEntryKind.File || entry.synthetic) continue
        val variant = entry.captureVariants.firstOrNull { name in it.names } ?: continue
        return CaptureDeclaration(entry, variant)
    }
    return null
}

private fun displayPatternOf(declaration: CaptureDeclaration): String =
    fillCapturedPath(
        declaration.entry.path,
        declaration.variant,
        declaration.variant.names.associateWith { "<$it>" },
    ) { capture -> "<${capture.name}${capture.transform?.let { ".${it.spelling}" }.orEmpty()}>" }

private fun declaredWithOf(declaration: CaptureDeclaration?, name: String): String {
    val module = declaration?.variant?.moduleCapture?.takeIf { name in it.names }
        ?: return "capture(\"$name\")"
    // Re-compiling an already-substituted pattern (`*`, not a capture token): nothing here can
    // raise KatachiAdjacentCaptureException, so which declaration site is blamed does not matter.
    val pattern = ModulePattern.compile(module.modulePattern, DeclarationSite.Unknown)
    val filled = pattern.filledIn(module.names.map { "\${capture(\"$it\")}" })
    return "\"$filled\".module { }"
}

internal fun missingCaptureException(
    role: Role,
    entries: List<LayoutEntry>,
    names: List<String>,
    declaredAt: DeclarationSite,
    fileName: String?,
    existingValues: (String) -> List<String> = { emptyList() },
    cause: Throwable? = null,
): KatachiMissingTemplateCaptureException {
    val roleEntries = entries.filter { it.role === role }
    val sites = captureSitesOf(roleEntries)
    val declarations = names.associateWith { declarationOf(roleEntries, it) }
    val missing = LinkedHashMap<String, MutableList<String>>()
    for ((name, declaration) in declarations) {
        declaration ?: continue
        missing.getOrPut(displayPatternOf(declaration)) { mutableListOf() } += name
    }
    return KatachiMissingTemplateCaptureException(
        role = role.qualifiedName,
        fileName = fileName,
        names = names,
        missing = missing,
        captureDeclaredAt = names.mapNotNull { name -> sites[name]?.let { name to it } }.toMap(),
        existingValues = names.associateWith { name -> catching { existingValues(name) }.getOrNull().orEmpty() },
        declaredWith = declarations.mapValues { (name, declaration) -> declaredWithOf(declaration, name) },
        declaredAt = declaredAt,
        cause = cause,
    )
}

internal fun existingCaptureValues(
    entries: List<LayoutEntry>,
    fileSystem: KatachiFileSystem,
    projectRoot: () -> FsPath,
    modules: () -> ModuleIndex,
): (String) -> List<String> = { name ->
    val declaration = declarationOf(entries, name)
    val module = declaration?.variant?.moduleCapture?.takeIf { name in it.names }
    when {
        declaration == null -> emptyList()
        module != null -> {
            val index = module.names.indexOf(name)
            // Re-compiling an already-substituted pattern (`*`, not a capture token): nothing
            // here can raise KatachiAdjacentCaptureException, so the declaration site is moot.
            modules().matchingModules(ModulePattern.compile(module.modulePattern, DeclarationSite.Unknown))
                .mapNotNull { it.wildcards.getOrNull(index) }
                .distinct()
                .sorted()
        }
        else -> existingDirectoriesAt(declaration, name, fileSystem, projectRoot)
    }
}

private fun existingDirectoriesAt(
    declaration: CaptureDeclaration,
    name: String,
    fileSystem: KatachiFileSystem,
    projectRoot: () -> FsPath,
): List<String> {
    // The plain occurrence: a derived level is not a directory a value is read from.
    val capture = declaration.variant.pathCaptures.firstOrNull { it.name == name && it.transform == null }
        ?: return emptyList()
    val parent = declaration.entry.path.split('/').take(capture.segmentIndex)
    if (parent.isNotEmpty() && Glob.compile(parent.joinToString("/"), Glob.PATH_SEPARATOR).hasWildcard) {
        return emptyList()
    }
    val directory = parent.fold(projectRoot()) { path, segment -> path / segment }
    return fileSystem.list(directory).filter { fileSystem.isDirectory(it) }.map { it.name }.sorted()
}
