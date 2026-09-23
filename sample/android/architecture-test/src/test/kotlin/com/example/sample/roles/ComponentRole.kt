package com.example.sample.roles

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of a widget shared across features, rather than owned by one of them. */
fun DeclarationContainerScope.component() = "Component" {
    title = "共通コンポーネント"
    summary = ":ui モジュールの component package に置く、feature をまたいで使う部品"
    example("AppButton", "アプリ共通のボタン")
    layout {
        ":ui".module {
            mainSourceSet / kotlin / modulePackage / "component" / "*".ktFile()
        }
    }
}
