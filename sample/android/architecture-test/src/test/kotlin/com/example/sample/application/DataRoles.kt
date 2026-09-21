package com.example.sample.application

import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the data layer: what `:data` holds.
 *
 * Deliberately not `inline`, for the reason spelled out on [uiRoles].
 */
fun ArchitectureScope.dataRoles() {
    "data".group {
        title = "データ"

        "Repository" {
            title = "リポジトリ"
            summary = "データの取得と保存。インターフェースと実装を :data の、扱う対象ごとの package " +
                "（user / settings）に並べて置く"
            example("UserRepository", "ユーザーの取得と保存のインターフェース")
            example("UserRepositoryImpl", "UserRepository の実装")
            // Two layouts: a role may live in more than one place. Here the interface
            // (`*Repository.kt`) and the implementation (`*RepositoryImpl.kt`).
            layout { }
            layout { }
        }
    }
}
