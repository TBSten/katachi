package com.example.application

import me.tbsten.katachi.dsl.ArchitectureScope

/** Roles of the data layer: where the values come from. */
fun ArchitectureScope.dataRoles() {
    "data".group {
        title = "データ"

        "Repository" {
            title = "リポジトリ"
            summary = "データの取得・保存を担い、取得元の詳細をドメインから隠す"
            example("HealthRepository", "稼働状態を読み出す")
            layout { }
        }
    }
}
