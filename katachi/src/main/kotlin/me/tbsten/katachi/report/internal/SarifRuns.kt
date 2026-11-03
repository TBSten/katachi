package me.tbsten.katachi.report.internal

import me.tbsten.katachi.dsl.files.FsPath

// One file holds one run: a second write of the same path in this JVM is numbered (katachi-2.sarif) instead of
// overwriting; nothing is remembered between JVMs.
internal class SarifRuns {
    private val counts = HashMap<String, Int>()

    @Synchronized
    fun next(file: FsPath): Int {
        val count = (counts[file.value] ?: 0) + 1
        counts[file.value] = count
        return count
    }

    companion object {
        val OF_THIS_PROCESS: SarifRuns = SarifRuns()
    }
}
