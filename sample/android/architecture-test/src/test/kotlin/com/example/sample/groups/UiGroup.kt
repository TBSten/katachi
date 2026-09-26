package com.example.sample.groups

import com.example.sample.allowedContents
import com.example.sample.roles.component
import com.example.sample.roles.navigation
import com.example.sample.roles.preview
import com.example.sample.roles.previewRoot
import com.example.sample.roles.theme
import com.example.sample.roles.uiCore
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the shared UI: what `:ui` and `:navigation` hold.
 *
 * The per-feature roles (`Screen` / `ViewModel` / `Route`) live in [featureGroup] instead.
 * See the note there for why the two are separate groups.
 *
 * `:ui` is one module split into packages, which is the shape `modulePackage` exists for:
 * `component` / `theme` / `core` / `preview` are named as what they are — one more level
 * below the module's own package — and `mainSourceSet / kotlin / modulePackage` says where
 * that package starts without any role file ever repeating `com/example/sample`.
 */
fun DeclarationContainerScope.uiGroup() = "ui".group {
    title = "UI (共通レイヤー)"
    summary = "feature をまたいで共有する UI。:ui の4つの package と :navigation"
    description = """
        どの画面からも使われる UI と、その土台。`:ui` は1つのモジュールを package で割ってあり、
        `component`（共通部品）・`theme`（色とタイポグラフィ）・`core`（UI 層の語彙）・
        `preview`（プレビューの土台）の4つ。`:navigation` は画面遷移の窓口だけを持つ別モジュール。

        `:ui` は下の層も横の feature も知らないので、`:data` や `:feature:*` への依存は入らない。

        feature 側の Screen / ViewModel / Route がここに無いのは、増え方が違うから。
        あちらはモジュールを足せば勝手に増える場所で、こちらは1つ足すたびに
        「全画面で使うのか」を決める場所なので、group を分けてある。

        `:navigation` が `:ui` と別モジュールなのは、依存の向きのため。feature は
        `AppNavigator` インターフェースにだけ依存し、`NavHostController` には触らない。
        画面部品を使いたいだけのコードにナビゲーションの依存を持ち込ませないためでもある。
        グラフの組み立ては `:app` が行う。
    """.trimIndent()
    allowedContents = """
        ここに置いてよいのは、2つ以上の feature が使うもの、あるいは使うと決まっているもの。
        1つの画面でしか使わないものは、その feature モジュールに置く。
    """.trimIndent()

    component()
    theme()
    uiCore()
    preview()
    previewRoot()
    navigation()
}
