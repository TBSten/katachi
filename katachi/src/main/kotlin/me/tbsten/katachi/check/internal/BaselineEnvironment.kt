package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.KatachiBaselineUpdateInCiException
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal const val BASELINE_UPDATE_PROPERTY: String = "katachi.baseline.update"

internal const val BASELINE_PRUNE_PROPERTY: String = "katachi.baseline.prune"

internal interface BaselineStore {
    fun read(file: String): String?

    fun write(file: String, text: String)
}

// Writes a sibling file and moves it over the target, so a killed run never leaves half a baseline.
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

internal class BaselineEnvironment(
    val store: BaselineStore = FileBaselineStore,
    val systemProperty: (String) -> String? = { System.getProperty(it) },
    val environmentVariable: (String) -> String? = { System.getenv(it) },
    // Looked up on each call rather than captured, so a spec swapping `System.err` sees it.
    val standardError: (String) -> Unit = { System.err.println(it) },
    val runs: BaselineRuns = BaselineRuns.OF_THIS_PROCESS,
)

internal enum class BaselineMode { Check, Update, Prune }

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
