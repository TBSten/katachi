package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role that owns where a value comes from, so the domain does not have to know. */
fun DeclarationContainerScope.repository() = "Repository" {
    title = "リポジトリ"
    summary = "データの取得・保存を担い、取得元の詳細をドメインから隠す"
    example("HealthRepository", "稼働状態を読み出す")
    layout {
        ":".module {
            mainSourceSet / kotlin / modulePackage / "repository" / "*Repository".ktFile()
        }
    }
}
