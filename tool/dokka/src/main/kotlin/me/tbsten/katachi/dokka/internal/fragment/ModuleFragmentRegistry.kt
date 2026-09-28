package me.tbsten.katachi.dokka.internal.fragment

import java.util.concurrent.ConcurrentHashMap

/**
 * The fragments the aggregating run has read so far, by module name.
 *
 * Filled by [ModuleFragmentStrategy] while the modules' outputs are copied, and read by
 * [me.tbsten.katachi.dokka.llms.LlmsIndexInstaller] when the aggregated `llms.txt` is written
 * afterwards. One instance per Dokka context; files may be processed concurrently, hence the
 * concurrent map.
 */
internal class ModuleFragmentRegistry {
    private val fragments = ConcurrentHashMap<String, ModuleFragment>()

    fun register(fragment: ModuleFragment) {
        fragments[fragment.moduleName] = fragment
    }

    /** The fragments of [moduleNames], in that order; modules that left none are skipped. */
    fun fragmentsOf(moduleNames: List<String>): List<ModuleFragment> = moduleNames.mapNotNull { fragments[it] }
}
