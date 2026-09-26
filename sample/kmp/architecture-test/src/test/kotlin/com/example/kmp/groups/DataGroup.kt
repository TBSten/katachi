package com.example.kmp.groups

import com.example.kmp.forbiddenContents
import com.example.kmp.roles.platformImplementation
import com.example.kmp.roles.repository
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The data layer, including the parts that differ per platform.
 *
 * `:data` is one module split into packages, the same way `:ui` is: `user` holds the
 * repositories, `platform` the expect/actual pair.
 */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "データ"
    summary = ":data モジュール。データの取得口と、プラットフォームで実装が変わる部分"
    description = """
        `:data` 1モジュールを package で割った層です。`user` にリポジトリ、`platform` に
        expect/actual の組が入ります。`:ui` と同じく、モジュールではなく package で分けた形です。

        2つの役割を同じ group に置いているのは、どちらも「アプリの外から値を持ってくる口」
        だからです。`UserRepository` はデータの取得口、`platformName()` は実行環境という
        外側の情報の取得口で、呼ぶ側（ViewModel）から見ればどちらも同じ `:data` への1本の依存に
        見えます。

        KMP のプラットフォーム差をこの group に閉じ込めているのが、このサンプルの設計です。
        `androidMain` / `iosMain` を持つのは `:data` だけで、UI 側（`:ui` と feature）には
        `expect`/`actual` が1つもありません。プラットフォーム固有の処理が要るという話になったら、
        画面ではなくここに降ろしてください。
    """.trimIndent()
    forbiddenContents = """
        - UI の型。`UiState` は `:ui` の core package にあり、`:data` はそれを知りません
        - テスト用の偽実装。`FakeUserRepository` は `:testing` にあります
        - `@Composable`。`:data` のビルドスクリプトに Compose のプラグインは入っていません
    """.trimIndent()

    repository()
    platformImplementation()
}
