package com.example.kmp.roles

import com.example.kmp.allowedContents
import com.example.kmp.forbiddenContents
import com.example.kmp.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/**
 * The state holder of one screen, named after the feature module it sits in.
 *
 * Tied to `wildcards[0]` the same way [screen] is: `:feature:home` may hold `HomeViewModel.kt`
 * and nothing else called `*ViewModel.kt`.
 */
fun DeclarationContainerScope.viewModel() = "ViewModel" {
    title = "ViewModel"
    summary = "画面の状態を持つ androidx.lifecycle.ViewModel。" +
        "Repository から取得した値を UiState に変換し、StateFlow で公開する"
    description = """
        画面の状態を持つ場所です。`:feature:<name>` の `commonMain` に `<Name>ViewModel.kt` を
        1ファイル。Screen と同じくファイル名がモジュール名に縛られるので、1つの feature モジュールに
        ViewModel が2つ並ぶことはありません。

        `androidx.lifecycle.ViewModel` を継承していますが、これは Compose Multiplatform 版
        （`org.jetbrains.androidx.lifecycle`）の実体なので `commonMain` に書けて iOS でも動きます。
        パッケージ名が `androidx` で始まるからといって Android 専用ではない、というのが
        KMP で引っかかりやすいところです。

        Repository は引数で受け取るだけで、自分では作りません。作るのは Route の役目です。
    """.trimIndent()
    allowedContents = """
        - `private val mutableState = MutableStateFlow(...)` と、それを `asStateFlow()` で
          公開する `val state: StateFlow<UiState<...>>`
        - 画面から呼ばれる操作（`reload()` など）と、`viewModelScope` を使った読み込み
        - 画面に出す形にまとめた data class（`SettingsUi` のように、同じファイル内で構わない）
    """.trimIndent()
    forbiddenContents = """
        - `@Composable`。描くのは Screen の仕事です
        - `android.*` の import と `Context`。これが要る処理は `:data` の PlatformImplementation
          （expect/actual）に降ろします。ここに書くと `commonMain` がコンパイルできません
        - 他の feature の ViewModel への依存
    """.trimIndent()
    example("HomeViewModel", "ホーム画面の状態")
    example("SettingsViewModel", "設定画面の状態")
    layout {
        ":feature:*".module {
            "commonMain".sourceSet / kotlin / modulePackage /
                "${wildcards[0].pascalCase}ViewModel".ktFile()
        }
    }
}
