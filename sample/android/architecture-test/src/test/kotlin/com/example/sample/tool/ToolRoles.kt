package com.example.sample.tool

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the tools a repository carries that are neither the app nor its build: git and
 * the prose that explains the sample, plus whatever else earns a place later (CI,
 * formatters, editor settings).
 *
 * They sit in their own group rather than in `build`, because a group is declared exactly
 * once and each of these files belongs to a different tool. Like the build scripts, they
 * are checked but not documented.
 *
 * This group is also what `ProjectArchitectureSpec` leaves out to prove the check is not
 * passing by accident: drop it and exactly the two files below turn into violations.
 *
 * Deliberately not `inline`. katachi reads the declaration site off the stack trace, and an
 * inlined frame reports a line number remapped past the end of the caller's file.
 */
fun ArchitectureScope.toolRoles() {
    "tool".group {
        documented = false
        title = "ツール"

        // No `title` here on purpose: an undocumented role has no display name to show,
        // and the sample asserts that the role name is then used as-is.
        "Git" {
            summary = ".gitignore など"
            documented = false
            layout {
                ".gitignore".file()
            }
        }

        "Documentation" {
            summary = "README.md など、リポジトリを読む人に向けた説明"
            documented = false
            layout {
                "README.md".file()
            }
        }
    }
}
