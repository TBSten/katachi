package me.tbsten.katachi.script.internal

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

internal sealed interface WorkdirLookup {
    data class Found(val path: Path) : WorkdirLookup

    data object NotFound : WorkdirLookup

    data class Ambiguous(val candidates: List<Path>) : WorkdirLookup
}

private const val SEARCH_DEPTH = 4

private val SKIPPED_DIRECTORIES = setOf(".git", ".gradle", "build", "node_modules", ".idea", ".kotlin")

internal const val CHECK_LIST_FILE: String = "check-list.html"

internal const val REPORT_FILE: String = "project-code-base-report.html"

/**
 * The working directory `init` made. The kit script finds it by its own location, because `init`
 * copies the script there; the `katachi` command lives in Homebrew's cellar instead, so it looks
 * at `KATACHI_WORKDIR` first and then below the current directory for the `check-list.html` that
 * `init` wrote.
 */
internal fun ScriptContext.lookupWorkdir(): WorkdirLookup {
    env["KATACHI_WORKDIR"]?.takeIf { it.isNotBlank() }?.let { configured ->
        val path = resolve(configured)
        return if (path.isDirectory()) WorkdirLookup.Found(path) else WorkdirLookup.NotFound
    }
    if (!cwd.isDirectory()) return WorkdirLookup.NotFound
    val candidates = Files.walk(cwd, SEARCH_DEPTH).use { stream ->
        stream.asSequence()
            .filter { it.isRegularFile() && it.name == CHECK_LIST_FILE }
            .filter { path -> cwd.relativize(path).none { it.name in SKIPPED_DIRECTORIES } }
            .map { it.parent }
            .filter { it.resolve("tmp").isDirectory() }
            .sorted()
            .toList()
    }
    return when (candidates.size) {
        0 -> WorkdirLookup.NotFound
        1 -> WorkdirLookup.Found(candidates.single())
        else -> WorkdirLookup.Ambiguous(candidates)
    }
}

internal fun ScriptContext.findWorkdir(): Path? = (lookupWorkdir() as? WorkdirLookup.Found)?.path

internal fun ScriptContext.requireWorkdir(): Path = when (val lookup = lookupWorkdir()) {
    is WorkdirLookup.Found -> lookup.path
    WorkdirLookup.NotFound -> die(
        "作業用ディレクトリが分かりません。先に '$CLI_COMMAND init' を実行してください。別の場所から実行するときは環境変数 KATACHI_WORKDIR で指定してください。",
        "Cannot find the working directory. Run '$CLI_COMMAND init' first. When running from somewhere else, point KATACHI_WORKDIR at it.",
    )
    is WorkdirLookup.Ambiguous -> die(
        "作業用ディレクトリの候補が複数あります。環境変数 KATACHI_WORKDIR でどれかを指定してください: " +
            lookup.candidates.joinToString(" ") { fileUri(it) },
        "More than one working directory was found. Point KATACHI_WORKDIR at one of them: " +
            lookup.candidates.joinToString(" ") { fileUri(it) },
    )
}

/** The language `init --lang` recorded, which the kit lets win over `KATACHI_LANG`. */
internal fun recordedLang(workdir: Path): Lang? {
    val file = workdir.resolve("tmp/cache/lang")
    if (!file.isRegularFile()) return null
    return Lang.of(file.readText().trim())
}

/** A non-empty file's text, or null. The kit's `[ -s file ] && cat file`. */
internal fun Path.textIfNotEmpty(): String? = if (isRegularFile() && fileSize() > 0) readText() else null

// ---------------------------------------------------------------- the check list's JSON, read only

/** The JSON object in the block [id] of [html], or null when the file, the block or the JSON is not there. */
internal fun readJsonBlockOrNull(html: Path, id: String): JsonObject? {
    if (!html.isRegularFile()) return null
    val block = extractJsonBlock(html.readText(), id) as? JsonBlock.Found ?: return null
    return parseJsonOrNull(block.text) as? JsonObject
}

/** `meta.<key>` of the check list as a string; empty when it is missing or not a string. The kit's `read_checklist_meta`. */
internal fun checklistMeta(checkList: Path, key: String): String = stringField(readJsonBlockOrNull(checkList, "checklist")?.get("meta"), key)

/** `preferences.<key>` of the check list; empty when there is none. The kit's `read_preference`. */
internal fun ScriptContext.preference(key: String): String {
    val workdir = findWorkdir() ?: return ""
    return stringField(readJsonBlockOrNull(workdir.resolve(CHECK_LIST_FILE), "checklist")?.get("preferences"), key)
}

private fun stringField(node: kotlinx.serialization.json.JsonElement?, key: String): String {
    val value = (node as? JsonObject)?.get(key) as? JsonPrimitive ?: return ""
    return if (value.isString) value.content else ""
}

// ---------------------------------------------------------------- where the test module goes

internal const val MODULE_DIR_DEFAULT: String = "architecture-test"

private val MODULE_DIR_PATTERN = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]*(/[A-Za-z0-9_][A-Za-z0-9_.-]*)*$")

/** Stops unless [value] is a path relative to the Gradle root. init / scaffold `--module-dir` and `preferences set moduleDir` share it. */
internal fun validateModuleDir(value: String) {
    if (!MODULE_DIR_PATTERN.matches(value)) {
        die(
            "--module-dir は Gradle ルートからの相対パスです（例: static-analysis/katachi）: $value",
            "--module-dir must be a path relative to the Gradle root (for example static-analysis/katachi): $value",
        )
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

/** The project root `init` recorded, or the current directory. */
internal fun ScriptContext.projectRoot(): Path {
    val recorded = findWorkdir()?.let { checklistMeta(it.resolve(CHECK_LIST_FILE), "projectRoot") } ?: ""
    return if (recorded.isNotEmpty()) resolve(recorded) else cwd
}

/** `<project root>/<module dir>/src/test/kotlin`. The kit's `definition_dir`. */
internal fun ScriptContext.definitionDir(module: ModuleDir): Path = projectRoot().resolve(module.dir).resolve("src/test/kotlin").normalize()

internal fun ScriptContext.definitionMissingMessage(module: ModuleDir, dir: Path): Pair<String, String> =
    "定義のディレクトリがありません: ${fileUri(dir)} 。scaffold をまだ実行していないか、置き場所が違います（記録: meta.moduleDir = ${module.dir}。変えるなら 'preferences set moduleDir=<path>' か 'scaffold --module-dir <path>'）" to
        "The definition directory does not exist: ${fileUri(dir)} . Either scaffold has not run yet, or the location differs (recorded: meta.moduleDir = ${module.dir}; change it with 'preferences set moduleDir=<path>' or 'scaffold --module-dir <path>')"

/** Stops when the definition directory is not there (lint / compare-violations). */
internal fun ScriptContext.requireDefinitionDir(module: ModuleDir): Path {
    val dir = definitionDir(module)
    if (dir.isDirectory()) return dir
    val (ja, en) = definitionMissingMessage(module, dir)
    die(ja, en)
}

/** The kit's `[ -e path ]` for a path relative to [base]. */
internal fun Path.has(relative: String): Boolean = resolve(relative).exists()
