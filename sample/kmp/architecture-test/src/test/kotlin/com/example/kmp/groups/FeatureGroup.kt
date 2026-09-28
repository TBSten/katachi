package com.example.kmp.groups

import com.example.kmp.forbiddenContents
import com.example.kmp.roles.featureComponent
import com.example.kmp.roles.route
import com.example.kmp.roles.screen
import com.example.kmp.roles.viewModel
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiGroup] because the two differ in how they grow. A feature module is a
 * place where things are *expected* to multiply — `":feature:*".module(capture = "feature") { }`
 * stands for however many there are and reads the matched name back as `wildcard("feature")` —
 * while `:ui` and
 * `:navigation` are shared modules where adding something is a design decision. Folding both
 * into one group would hide that difference in the generated documentation, and would mix two
 * shapes of `layout` inside a single group.
 *
 * Screen, ViewModel and Route are where this sample ties a file name to the module it sits in:
 * each one reads `wildcard("feature")` back, so `:feature:home` is required to hold
 * `HomeScreen.kt`, `HomeViewModel.kt` and `HomeRoute.kt` — not merely *a* screen, a view model
 * and a route. FeatureComponent reads the same name, and its template is given it
 * (`--arg feature=home`) to pick the module it generates into.
 */
fun DeclarationContainerScope.featureGroup() = "feature".group {
    title = "フィーチャー"
    summary = "1画面につき1モジュール。:feature:<name> が Screen / ViewModel / Route を必ず持つ"
    description = """
        画面ごとに1つずつ増えていくモジュールの層です。`:feature:home` と `:feature:settings` が
        あり、画面を足すときはモジュールを足します。

        Screen / ViewModel / Route の3つをここに集めたのは、どれも「1つの画面のためのもの」
        だからです。3つとも `wildcard("feature")`、つまり `:feature:*` が捕まえたモジュール名を
        読み戻してファイル名を決めるので、`:feature:home` には `HomeScreen.kt` /
        `HomeViewModel.kt` / `HomeRoute.kt` が要ります。「画面が1つあればいい」ではなく
        「その名前の画面が要る」まで言い切れるのがこの group の形です。

        ui group と分けてあるのは、増え方が違うからです。feature はモジュールが増える前提の
        場所で、`":feature:*".module(capture = "feature") { }` という1つの宣言が何個あっても
        足ります。一方 `:ui` や `:navigation` に何かを足すのは設計判断です。1つの group にまとめると、
        生成されるドキュメントでその差が消えてしまいます。

        画面の部品（FeatureComponent）だけは1つの feature の中で数が増えていく役割で、
        テンプレートから生成できます。`:feature:*` の `*` に `feature` と名前を付けてあるので、
        `--arg feature=home` で生成先のモジュールを選びます。

        この group の役割はすべて `commonMain` です。画面まわりに `androidMain` / `iosMain` は1つも
        ありません。プラットフォーム差は data group の PlatformImplementation に閉じています。
    """.trimIndent()
    forbiddenContents = """
        - 複数の画面から使う部品。それは `:ui` の Component です
        - データの取得。`:data` にあり、feature はインターフェース越しに読みます
        - 他の feature への依存。画面どうしは直接つながらず、`:navigation` の `Destination`
          を介します
    """.trimIndent()

    screen()
    viewModel()
    route()
    featureComponent()
}
