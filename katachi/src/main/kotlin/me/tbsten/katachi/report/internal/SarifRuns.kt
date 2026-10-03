package me.tbsten.katachi.report.internal

import me.tbsten.katachi.dsl.files.FsPath

/**
 * How many times each SARIF file was written in the runs of this JVM so far.
 *
 * One file holds one run, so a second `assert()` in the same JVM that names the same path --
 * two architecture tests in one module, say -- must not overwrite the first. It is numbered
 * instead, `katachi-2.sarif`, and the caller says so on standard error. Between JVMs nothing is
 * remembered: the first write of a run overwrites whatever an earlier build left behind.
 */
internal class SarifRuns {
    private val counts = HashMap<String, Int>()

    /**
     * Claims the next number for [file], an absolute path: `1` the first time, then `2`, `3`,
     * and so on. Claimed before the write, so a write that fails still uses up its number.
     */
    @Synchronized
    fun next(file: FsPath): Int {
        val count = (counts[file.value] ?: 0) + 1
        counts[file.value] = count
        return count
    }

    companion object {
        /** The runs of this JVM: what `assert()` uses unless a spec says otherwise. */
        val OF_THIS_PROCESS: SarifRuns = SarifRuns()
    }
}
