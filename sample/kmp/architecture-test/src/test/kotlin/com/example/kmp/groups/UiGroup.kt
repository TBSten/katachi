package com.example.kmp.groups

import com.example.kmp.forbiddenContents
import com.example.kmp.roles.component
import com.example.kmp.roles.navigation
import com.example.kmp.roles.preview
import com.example.kmp.roles.previewRoot
import com.example.kmp.roles.theme
import com.example.kmp.roles.uiCore
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The shared UI modules a screen draws from: `:ui` and `:navigation`.
 *
 * The per-feature roles (`Screen` / `ViewModel` / `Route`) live in [featureGroup] instead. See
 * the note there for why the two are separate groups.
 *
 * Every layout here starts at a module path rather than at a directory name. `":ui".module { }`
 * is the directory the build puts `:ui` in, plus the two lines every Gradle module has
 * (`build/` is not checked, `build.gradle.kts` has to be there), and the package below it
 * comes from `modulePackage` instead of being spelled out per role.
 *
 * Component / Theme / UiCore all live in the one `:ui` module. They used to be
 * `:ui:component` / `:ui:theme` / `:ui:core`; now they are packages of `:ui`, which is the
 * shape katachi has to be able to describe.
 */
fun DeclarationContainerScope.uiGroup() = "ui".group {
    title = "UI"
    summary = ":ui と :navigation。どの画面にも属さない共有の UI と、遷移先の定義"
    description = """
        画面から使われる側の、共有モジュール2つです。特定の画面に属さないものがここに集まります。

        `:ui` は1つのモジュールを package で割っています。`component`（部品）、`theme`（見た目）、
        `core`（画面の状態の型）、`preview`（プレビューの土台）の4つで、それぞれが役割1つです。
        以前は `:ui:component` のような別モジュールでしたが、統合して package にしました。
        「この役割はこのモジュールのこの package」と言えることが katachi に要る形だからで、
        実際の現場でもこちらの方がよく見ます。

        `:navigation` は同じ group にいますが Compose に依存しません。遷移先の一覧と現在地だけを
        持ち、それをどう見せるかは `:app:android` の `AppRoot` の仕事です。画面から使われる側で
        あることは `:ui` と同じなので、この group に置いています。

        layout はどれもモジュールパスから始まり、その下の package は `modulePackage` から
        導きます。ディレクトリ名を書き写すのではなく、ビルドが言っていることをそのまま書く、
        というのがこのサンプル全体の方針です。
    """.trimIndent()
    forbiddenContents = """
        - 画面ごとの Screen / ViewModel / Route。それは feature group です
        - feature モジュールへの依存。依存は常に feature から `:ui` / `:navigation` へ向きます。
          逆向きの参照が1つ入ると、画面を足すたびに共有モジュールが太ります
    """.trimIndent()

    component()
    theme()
    uiCore()
    preview()
    previewRoot()
    navigation()
}
