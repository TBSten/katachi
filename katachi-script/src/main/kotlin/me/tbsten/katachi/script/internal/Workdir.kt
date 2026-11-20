package me.tbsten.katachi.script.internal

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

internal sealed interface WorkdirLookup {
    /** [configured]: named by `KATACHI_WORKDIR`, not found below the current directory. */
    data class Found(val path: Path, val configured: Boolean = false) : WorkdirLookup

    data object NotFound : WorkdirLookup

    data class Ambiguous(val candidates: List<Path>) : WorkdirLookup

    // `KATACHI_WORKDIR` names a place that is not a working directory: going on without one would quietly fall
    // back to the default module directory, language and log place, as with two of them.
    data class Misconfigured(val configured: String, val path: Path) : WorkdirLookup
}

private const val SEARCH_DEPTH = 4

private val SKIPPED_DIRECTORIES = setOf(".git", ".gradle", "build", "node_modules", ".idea", ".kotlin")

internal const val CHECK_LIST_FILE: String = "check-list.html"

/**
 * The working directory of the katachi skill's setup: a directory with `check-list.html` and `tmp/`,
 * which the agent copies from the skill. It is looked up at `KATACHI_WORKDIR` first and then below
 * the current directory. Commands that use it work without it too.
 */
internal fun ScriptContext.lookupWorkdir(): WorkdirLookup {
    env["KATACHI_WORKDIR"]?.takeIf { it.isNotBlank() }?.let { configured ->
        val path = resolve(configured)
        return if (path.resolve(CHECK_LIST_FILE).isReadableFile()) WorkdirLookup.Found(path, configured = true) else WorkdirLookup.Misconfigured(configured, path)
    }
    if (!cwd.isDirectory()) return WorkdirLookup.NotFound
    // A check list someone else made (a shared parent, /tmp, a CI runner) may be unreadable: it is no working directory of
    // this user, and reading it would stop every command.
    val (readable, unreadable) = checkListsBelow(cwd)
        .filter { it.parent.resolve("tmp").isDirectory() }
        .partition { it.isReadableFile() }
    if (unreadable.isNotEmpty()) {
        warnOnce(Messages.Workdir.unreadableCheckListsSkipped(unreadable.sorted().joinToString(" ") { fileUri(it) }))
    }
    val candidates = readable.map { it.parent }.sorted()
    return when (candidates.size) {
        0 -> WorkdirLookup.NotFound
        1 -> WorkdirLookup.Found(candidates.single())
        else -> WorkdirLookup.Ambiguous(candidates)
    }
}

// Every command looks the working directory up first, `--version` too: a directory it cannot read is skipped,
// not a reason to stop.
private fun checkListsBelow(root: Path): List<Path> {
    val found = mutableListOf<Path>()
    Files.walkFileTree(
        root,
        emptySet(),
        SEARCH_DEPTH,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root && dir.name in SKIPPED_DIRECTORIES) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                // add(), not +=: a Path is an Iterable<Path> too, and += would read it as its name parts.
                if (attrs.isRegularFile && file.name == CHECK_LIST_FILE) found.add(file)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult = FileVisitResult.CONTINUE
        },
    )
    return found
}

/**
 * The working directory, or null when there is none. Stops when there are two or more, or when KATACHI_WORKDIR
 * names no working directory: going on with
 * neither would quietly fall back to the default module directory, language and log place.
 */
internal fun ScriptContext.findWorkdir(): Path? = foundWorkdir()?.path

private fun ScriptContext.foundWorkdir(): WorkdirLookup.Found? = when (val lookup = lookupWorkdir()) {
    is WorkdirLookup.Found -> lookup
    WorkdirLookup.NotFound -> null
    is WorkdirLookup.Ambiguous -> die(ambiguousWorkdirMessage(lookup))
    is WorkdirLookup.Misconfigured -> die(misconfiguredWorkdirMessage(lookup))
}

internal fun ScriptContext.misconfiguredWorkdirMessage(lookup: WorkdirLookup.Misconfigured): Msg =
    Messages.Workdir.misconfigured(lookup.configured, fileUri(lookup.path))

internal fun ScriptContext.ambiguousWorkdirMessage(lookup: WorkdirLookup.Ambiguous): Msg =
    Messages.Workdir.ambiguous(lookup.candidates.joinToString("") { "    ${fileUri(it)}\n" })

private val HTML_LANG = Regex("<html[^>]*\\slang=\"([a-z]+)\"")

/** The language of the check list the katachi skill copied into [workdir]: its `<html lang="...">`. */
internal fun recordedLang(workdir: Path): Lang? {
    val text = workdir.resolve(CHECK_LIST_FILE).readTextOrNull() ?: return null
    return Lang.of(HTML_LANG.find(text.take(2000))?.groupValues?.get(1))
}

private fun Path.isReadableFile(): Boolean = isRegularFile() && Files.isReadable(this)

/** The text of a regular file, or null when there is none or it cannot be read: an unreadable check list counts as absent. */
private fun Path.readTextOrNull(): String? = try {
    if (isRegularFile()) readText() else null
} catch (_: IOException) {
    null
}

/** A non-empty file's text, or null. The kit's `[ -s file ] && cat file`. */
internal fun Path.textIfNotEmpty(): String? = if (isRegularFile() && fileSize() > 0) readText() else null

// ---------------------------------------------------------------- the check list's JSON, read only

/** The JSON object in the block [id] of [html], or null when the file, the block or the JSON is not there. */
internal fun readJsonBlockOrNull(html: Path, id: String): JsonObject? {
    val block = extractJsonBlock(html.readTextOrNull() ?: return null, id) as? JsonBlock.Found ?: return null
    return parseJsonOrNull(block.text) as? JsonObject
}

/** `meta.<key>` of the check list as a string; empty when it is missing or not a string. The kit's `read_checklist_meta`. */
internal fun checklistMeta(checkList: Path, key: String): String = stringField(readJsonBlockOrNull(checkList, "checklist")?.get("meta"), key)

/** `preferences.<key>` of the check list; empty when there is none. */
internal fun ScriptContext.preference(key: String): String {
    val workdir = findWorkdir() ?: return ""
    return stringField(readJsonBlockOrNull(workdir.resolve(CHECK_LIST_FILE), "checklist")?.get("preferences"), key)
}

private fun stringField(node: kotlinx.serialization.json.JsonElement?, key: String): String {
    val value = (node as? JsonObject)?.get(key) as? JsonPrimitive ?: return ""
    return if (value.isString) value.content else ""
}

// ---------------------------------------------------------------- where the verification module goes

internal const val MODULE_DIR_DEFAULT: String = "architecture-test"

private val MODULE_DIR_PATTERN = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]*(/[A-Za-z0-9_][A-Za-z0-9_.-]*)*$")

/**
 * Stops unless [value] is a path relative to the Gradle root (`--module-dir`), as a usage error: `test` exits 1 only
 * for a failed check.
 */
internal fun validateModuleDir(value: String) {
    if (!MODULE_DIR_PATTERN.matches(value)) {
        dieUsage(Messages.Workdir.moduleDirMustPath(value))
    }
}

internal fun moduleGradlePathOf(moduleDir: String): String = ":" + moduleDir.replace('/', ':')

internal enum class ModuleDirSource { ARG, CHECKLIST, DEFAULT }

internal class ModuleDir(
    /** Relative to the Gradle root: `architecture-test`, `static-analysis/katachi`. */
    val dir: String,
    val source: ModuleDirSource,
    /** `meta.moduleDir` in the check list; empty when there is none. */
    val recorded: String,
) {
    /** `:architecture-test`, `:static-analysis:katachi`. */
    val gradlePath: String get() = moduleGradlePathOf(dir)

    /** ` --module-dir <dir>` when it came from that option, for a command a message suggests: without it, that command would use another module. */
    val optionText: String get() = if (source == ModuleDirSource.ARG) " --module-dir $dir" else ""
}

/** [arg] > `meta.moduleDir` > the default. The kit's `resolve_module_dir`. */
internal fun ScriptContext.resolveModuleDir(arg: String? = null): ModuleDir {
    val recorded = findWorkdir()?.let { checklistMeta(it.resolve(CHECK_LIST_FILE), "moduleDir") } ?: ""
    val module = when {
        !arg.isNullOrEmpty() -> ModuleDir(arg, ModuleDirSource.ARG, recorded)
        recorded.isNotEmpty() -> ModuleDir(recorded, ModuleDirSource.CHECKLIST, recorded)
        else -> ModuleDir(MODULE_DIR_DEFAULT, ModuleDirSource.DEFAULT, recorded)
    }
    validateModuleDir(module.dir)
    return module
}

/**
 * `meta.projectRoot` of the check list, or the current directory. When the current directory is in another
 * work tree of the same repository, the same place in that work tree: the recorded one would check files
 * the change never touched and pass (KATACHI-354). When the working directory found below the current directory
 * is not in the recorded project, the project was copied or moved along with it: the project that holds it, for the
 * same reason (KATACHI-420).
 */
internal fun ScriptContext.projectRoot(): Path {
    val workdir = foundWorkdir() ?: return cwd
    val checkList = workdir.path.resolve(CHECK_LIST_FILE)
    val recorded = checklistMeta(checkList, "projectRoot")
    if (recorded.isEmpty()) return cwd
    val root = resolve(recorded)
    sameRepositoryCounterpart(root, cwd)?.let { counterpart ->
        warnOnce(Messages.Workdir.projectRootInOtherWorkTree(fileUri(root), fileUri(counterpart)))
        return counterpart
    }
    // KATACHI_WORKDIR may name a working directory kept outside the project.
    if (workdir.configured) return root
    val holder = projectHoldingMovedWorkdir(workdir.path, root, checklistMeta(checkList, "workdir")) ?: return root
    warnOnce(Messages.Workdir.projectRootNotHoldingWorkdir(fileUri(root), fileUri(workdir.path), fileUri(holder)))
    return holder
}

/**
 * The project that holds [workdir] when that is not [root], the recorded project root; null when [workdir] is in place.
 * [recordedWorkdir] is `meta.workdir`, the working directory relative to the project root, which a copy keeps: the
 * project is [workdir] without it. Without it, the current directory, above [workdir] as the lookup found it.
 */
private fun ScriptContext.projectHoldingMovedWorkdir(workdir: Path, root: Path, recordedWorkdir: String): Path? {
    val here = workdir.realOrSelf()
    val there = root.realOrSelf()
    val relative = recordedWorkdir.takeIf { it.isNotEmpty() }?.let { runCatching { Path.of(it) }.getOrNull() }?.takeIf { !it.isAbsolute }
    if (relative != null && there.resolve(relative).normalize() == here) return null
    val inside = relative?.normalize()?.takeIf { it.nameCount > 0 && !it.startsWith("..") && here.endsWith(it) }
    val holder = when {
        inside != null -> generateSequence(here) { it.parent }.elementAtOrNull(inside.nameCount) ?: return null
        here.startsWith(there) -> return null
        else -> cwd
    }
    return holder.takeIf { it.realOrSelf() != there }
}

/** `<project root>/<module dir>/src/test/kotlin`. The kit's `definition_dir`. */
internal fun ScriptContext.definitionDir(module: ModuleDir): Path = projectRoot().resolve(module.dir).resolve("src/test/kotlin").normalize()

internal fun ScriptContext.definitionMissingMessage(module: ModuleDir, dir: Path): Msg =
    Messages.Workdir.definitionDirMissing(fileUri(dir), module.dir)

/** Stops when the definition directory is not there (lint). */
internal fun ScriptContext.requireDefinitionDir(module: ModuleDir): Path {
    val dir = definitionDir(module)
    if (dir.isDirectory()) return dir
    die(definitionMissingMessage(module, dir))
}

/** The kit's `[ -e path ]` for a path relative to [base]. */
internal fun Path.has(relative: String): Boolean = resolve(relative).exists()
