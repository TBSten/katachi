package com.example.sample.roles

import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of the types the UI layer itself is built out of. */
fun DeclarationContainerScope.uiCore() = "UiCore" {
    title = "UI 基盤"
    summary = ":ui モジュールの core package に置く、UI 層の土台になる型"
    description = """
        UI 層が自分を組み立てるための語彙。いまは `UiState` だけで、画面の状態が
        `Loading` / `Content` / `Error` のどれであるかを表す sealed interface と、
        `Content` の中身を取り出す `contentOrNull` が入っている。ViewModel がこれを流し、
        Screen がこれを `when` で分岐する。

        `component` でも `theme` でもなく `core` にあるのは、その2つがこの語彙の上に
        書かれるから。そして意図的に Compose へ依存しない。`:feature:*` の ViewModel は
        `androidx.compose.*` を1つも import せずに `UiState` を組み立てられる。
        ここに Compose の型が入ると、その線が消える。

        ファイル名は `*.kt` で、型が増えること自体は想定している。ただし足してよいのは
        「複数の画面が同じ形で使う UI の語彙」だけ。画面1つぶんの状態（`HomeContent` /
        `SettingsContent`）は、その feature の ViewModel と同じファイルに置く。
        描画に関わる部品は `component`、色や字は `theme`。
    """.trimIndent()
    example("UiState", "画面状態を表す sealed interface")
    layout {
        ":ui".module {
            mainSourceSet / kotlin / modulePackage / "core" / "*".ktFile()
        }
    }
}
