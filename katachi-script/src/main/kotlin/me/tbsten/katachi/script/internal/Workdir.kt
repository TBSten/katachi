package me.tbsten.katachi.script.internal

import java.nio.file.Files
import java.nio.file.Path
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
            .filter { it.isRegularFile() && it.name == "check-list.html" }
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
        "作業用ディレクトリが分かりません。先に 'katachi init' を実行してください。別の場所から実行するときは環境変数 KATACHI_WORKDIR で指定してください。",
        "Cannot find the working directory. Run 'katachi init' first. When running from somewhere else, point KATACHI_WORKDIR at it.",
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
