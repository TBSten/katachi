package com.example.kmp.tool

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Files that belong to the tools around the project rather than to the build. Split out of
 * the `build` group so that "how this project is built" and "what tooling it carries" do not
 * share one bucket.
 *
 * Undocumented for the same reason the build group is: real, but not part of the
 * architecture a reader of the generated docs is looking for.
 */
fun ArchitectureScope.toolRoles() {
    "tool".group(documented = false) {
        title = "ツール"

        // No `title` here on purpose: an undocumented role has no display name to show, so
        // this is the one place in this sample that exercises the default — the role name
        // itself. `ProjectArchitectureSpec` asserts it.
        "Git" {
            summary = ".gitignore など"
            documented = false
            example(".gitignore", "生成物を Git の管理から外す")
            layout {
                ".gitignore".file()
            }
        }
    }
}
