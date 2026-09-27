package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.KatachiBaselineUpdateInCiException
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** The system property that rebuilds the baseline from the run. */
internal const val BASELINE_UPDATE_PROPERTY: String = "katachi.baseline.update"

/** The system property that only removes what was fixed from the baseline. */
internal const val BASELINE_PRUNE_PROPERTY: String = "katachi.baseline.prune"

/** Reads and writes the baseline file. A spec hands in one that lives in memory. */
internal interface BaselineStore {
    /** The text of [file] (an absolute path), or `null` when there is no such file. */
    fun read(file: String): String?

    /** Replaces the text of [file] (an absolute path), creating it and its directory if needed. */
    fun write(file: String, text: String)
}

/**
 * The baseline on disk, read and written as UTF-8.
 *
 * A write goes to a file next to the target first and is then moved over it, so a run killed
 * half way -- a cancelled Gradle build, a full disk -- leaves the old baseline or the new one,
 * never half of one. The move is atomic where the file system supports it, and a plain replace
 * where it does not.
 */
internal object FileBaselineStore : BaselineStore {
    override fun read(file: String): String? = File(file).takeIf { it.isFile }?.readText(Charsets.UTF_8)

    override fun write(file: String, text: String) {
        val target = File(file).absoluteFile.toPath()
        val directory = target.parent
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, ".${target.fileName}.", ".tmp")
        try {
            Files.write(temporary, text.toByteArray(Charsets.UTF_8))
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}

/**
 * Everything the baseline reads from outside the definition: the file, the two system
 * properties, the `CI` variable, where its notices go, and what the other runs of this JVM
 * found.
 */
internal class BaselineEnvironment(
    val store: BaselineStore = FileBaselineStore,
    val systemProperty: (String) -> String? = { System.getProperty(it) },
    val environmentVariable: (String) -> String? = { System.getenv(it) },
    // Looked up on each call rather than captured, so a spec swapping `System.err` sees it.
    val standardError: (String) -> Unit = { System.err.println(it) },
    val runs: BaselineRuns = BaselineRuns.OF_THIS_PROCESS,
)

/** What a run does with the baseline. */
internal enum class BaselineMode { Check, Update, Prune }

/**
 * The mode the system properties ask for. A value other than `true` or `false` is reported on
 * standard error and read as not given: `-Dkatachi.baseline.update` alone, or `=yes`, is more
 * likely a slip than a request to rewrite the file.
 *
 * @throws KatachiBaselineUpdateInCiException when an update or a prune is asked for on CI.
 */
internal fun BaselineEnvironment.mode(): BaselineMode {
    val update = isRequested(BASELINE_UPDATE_PROPERTY)
    val prune = isRequested(BASELINE_PRUNE_PROPERTY)
    if ((update || prune) && environmentVariable("CI").equals("true", ignoreCase = true)) {
        throw KatachiBaselineUpdateInCiException(if (update) BASELINE_UPDATE_PROPERTY else BASELINE_PRUNE_PROPERTY)
    }
    // Asked for both, update wins: it is the one that leaves the ledger matching the project.
    return when {
        update -> BaselineMode.Update
        prune -> BaselineMode.Prune
        else -> BaselineMode.Check
    }
}

private fun BaselineEnvironment.isRequested(property: String): Boolean {
    val value = systemProperty(property) ?: return false
    if (value.equals("true", ignoreCase = true)) return true
    if (!value.equals("false", ignoreCase = true)) {
        standardError(
            "Warning: -D$property=$value is neither true nor false, so it was ignored. " +
                "Give -D$property=true to ask for it.",
        )
    }
    return false
}
