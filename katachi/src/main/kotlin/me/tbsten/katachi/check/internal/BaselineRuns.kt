package me.tbsten.katachi.check.internal

internal class BaselineRuns {
    private val found = HashMap<Pair<String, String>, Map<BaselineKey, Int>>()

    @Synchronized
    fun record(file: String, check: String, keys: Map<BaselineKey, Int>): Map<BaselineKey, Int>? {
        val earlier = found.putIfAbsent(file to check, keys) ?: return null
        return earlier.takeIf { it != keys }
    }

    companion object {
        val OF_THIS_PROCESS: BaselineRuns = BaselineRuns()
    }
}
