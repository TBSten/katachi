package me.tbsten.katachi.check.internal

/**
 * What each check found against each baseline file in the runs of this JVM so far.
 *
 * The baseline files a check's entries under its class name alone, so every run of that class
 * answers for all of them: a run calls the entries it did not find stale, and an update or a
 * prune replaces them with its own. That holds as long as every run of the class finds the same
 * thing. Two `assert(...)` calls passing the class configured differently -- or two definitions
 * with different layouts naming one baseline file -- break it, and would otherwise take turns
 * deleting each other's entries without either ever converging.
 *
 * Within one test run the project does not change, so two runs of one check finding different
 * things is exactly that situation, and is what this remembers in order to refuse it.
 */
internal class BaselineRuns {
    private val found = HashMap<Pair<String, String>, Map<BaselineKey, Int>>()

    /**
     * Remembers that [check] found [keys] against the baseline [file], and hands back what an
     * earlier run of the same check against the same file found when that was different, or
     * `null` when there was no earlier run or it found the same.
     */
    @Synchronized
    fun record(file: String, check: String, keys: Map<BaselineKey, Int>): Map<BaselineKey, Int>? {
        val earlier = found.putIfAbsent(file to check, keys) ?: return null
        return earlier.takeIf { it != keys }
    }

    companion object {
        /** The runs of this JVM: what `assert()` and a processor run use unless a spec says otherwise. */
        val OF_THIS_PROCESS: BaselineRuns = BaselineRuns()
    }
}
