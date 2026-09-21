package com.example.tool

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the tools around the project that are not the build itself.
 *
 * Like the build group, these are checked but kept out of the generated documentation.
 */
fun ArchitectureScope.toolRoles() {
    "tool".group(documented = false) {
        title = "ツール設定"

        "Git" {
            title = "Git 設定"
            summary = "バージョン管理の設定ファイル"
            example(".gitignore", "生成物を管理対象から外す")
            layout {
                ".gitignore".file()
            }
        }
    }
}
