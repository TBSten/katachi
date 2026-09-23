package com.example.kmp.roles

import com.example.kmp.processor.owner
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * What git needs to be told about this project.
 *
 * No `title` here on purpose: an undocumented role has no display name to show, so this is the
 * one place in this sample that exercises the default — the role name itself.
 * `ProjectArchitectureSpec` asserts it.
 */
fun DeclarationContainerScope.git() = "Git" {
    summary = ".gitignore など"
    documented = false
    // Also `owner = "platform"` (see com.example.kmp.processor.Owner), this sample's own
    // metadata key -- not katachi's. `PlatformOwnedFilesSpec` builds a variant of
    // this exact role with the tag left out to prove its processor really reads it.
    owner = "platform"
    example(".gitignore", "生成物を Git の管理から外す")
    layout {
        ".gitignore".file()
    }
}
