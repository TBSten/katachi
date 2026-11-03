package me.tbsten.katachi.dsl

internal interface ModuleAwareLayoutScope : LayoutScope {
    val currentModulePath: String?

    val currentWildcards: List<String>?

    val currentCaptures: Map<String, String>?

    fun expandModulePath(modulePath: String, block: LayoutDirectoryScope.() -> Unit): LayoutModule
}
