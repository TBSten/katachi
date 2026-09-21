package com.example.sample.application

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.ArchitectureScope

/**
 * Roles of the data layer: what `:data` holds.
 *
 * Deliberately not `inline`, for the reason spelled out on [uiRoles].
 */
fun ArchitectureScope.dataRoles() {
    "data".group {
        title = "データレイヤー"

        "Repository" {
            title = "リポジトリ"
            summary = "データの取得と保存。インターフェースと実装を :data の、扱う対象ごとの package " +
                "（user / settings）に並べて置く"
            example("UserRepository", "ユーザーの取得と保存のインターフェース")
            example("UserRepositoryImpl", "UserRepository の実装")
            // Two layouts: a role may live in more than one place. Here the interface
            // (`*Repository.kt`) and the implementation (`*RepositoryImpl.kt`). Both sit in
            // the same package, so `description` is what tells the two apart — which is the
            // question it exists to answer.
            layout {
                ":data".module {
                    mainSourceSet / kotlin / modulePackage {
                        description = "インターフェース。呼び出し側が依存する型"
                        "user" { "*Repository".ktFile() }
                        "settings" { "*Repository".ktFile() }
                    }
                }
            }
            layout {
                ":data".module {
                    mainSourceSet / kotlin / modulePackage {
                        description = "実装。インターフェースと同じ package に並べる"
                        "user" { "*RepositoryImpl".ktFile() }
                        "settings" { "*RepositoryImpl".ktFile() }
                    }
                }
            }
        }
    }
}
