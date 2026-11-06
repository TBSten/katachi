package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.LayoutModule

// Only gradle() reaches it, through expandModules: the module index is held by the scope and never handed out.
internal interface ModuleAwareLayoutScope : LayoutScope {
    fun expandModulePath(modulePath: String, block: LayoutDirectoryScope.() -> Unit): LayoutModule
}
