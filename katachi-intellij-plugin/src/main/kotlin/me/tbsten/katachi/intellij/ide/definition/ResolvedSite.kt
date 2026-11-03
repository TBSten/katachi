package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.vfs.VirtualFile

internal sealed interface ResolvedSite {
    data class Found(val file: VirtualFile, val line: Int) : ResolvedSite

    data class Unresolved(val reason: String) : ResolvedSite
}
