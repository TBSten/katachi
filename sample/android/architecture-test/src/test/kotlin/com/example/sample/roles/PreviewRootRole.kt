package com.example.sample.roles

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the single base every `@Preview` wraps its body in. */
fun DeclarationContainerScope.previewRoot() = "PreviewRoot" {
    title = "プレビューの土台"
    summary = ":ui モジュールの preview package に置く、すべての @Preview が中身を包む土台。" +
        "テーマと背景を 1 箇所で決め、darkTheme を受け取って明暗を出し分ける"
    example("PreviewRoot", "プレビュー共通の土台")
    layout {
        ":ui".module {
            // Required, so deleting the file fails the check with `[MissingFile]`
            // rather than leaving every `@Preview` without a base.
            mainSourceSet / kotlin / modulePackage / "preview" / "PreviewRoot".ktFile()
        }
    }
}
