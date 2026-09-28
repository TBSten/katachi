package com.example.sample.groups

import com.example.sample.modulePackage
import com.example.sample.roles.featureComponent
import com.example.sample.roles.featureTest
import com.example.sample.roles.route
import com.example.sample.roles.screen
import com.example.sample.roles.viewModel
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutDirectoryScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.*

/**
 * Roles of one feature: what every `:feature:<name>` module holds.
 *
 * Kept apart from [uiGroup] because the two differ in how they grow. A feature module is a
 * place where things are *expected* to multiply — it is written as `":feature:*"` and reads
 * the matched name back as `wildcard("feature")` — while `:ui` and `:navigation` are shared
 * modules where adding something is a design decision. Folding both into one group would
 * hide that difference in the generated documentation, and would mix two shapes of `layout`
 * inside a single group.
 *
 * This is the group where the module path earns its keep twice over. `":feature:*"` (written
 * `":feature:${capture("feature")}"`) stands
 * for the feature modules that exist, so adding `:feature:profile` to `settings.gradle.kts`
 * needs no edit here; and `wildcard("feature")` is that module's own name, so a file is not merely
 * allowed to be *a* screen but has to be **that module's** screen. `:feature:home` may hold
 * `HomeScreen.kt` and nothing else called `*Screen.kt`: a `ProfileScreen.kt` left behind
 * there is reported, and a missing `HomeScreen.kt` is reported too, which a `*Screen.kt`
 * could never say.
 *
 * The roles below start from the same place, so that place is written once as
 * [featureSources] — this project's own addition to the layout vocabulary, not katachi's.
 */
fun DeclarationContainerScope.featureGroup() = "feature".group {
    title = "各画面の構成"
    summary = "画面1つぶんのモジュール。:feature:<name> ごとに Screen / ViewModel / Route を1つずつ置き、画面の部品とテストを足していく"
    description = """
        `:feature:home` `:feature:settings` のような、画面1つぶんのモジュールの中身。
        どのモジュールにも `<Name>Screen.kt` `<Name>ViewModel.kt` `<Name>Route.kt` が
        1つずつあり、ファイル名はモジュール名から決まる。画面が増えるときは
        モジュールごと増やすので、1つの feature に2つ目の画面は入らない。

        3つに割ってあるのは、変わる理由が別々だから。Screen は見た目、ViewModel は状態、
        Route は外とのつなぎ目で、この中で feature の外から参照されるのは Route だけ。
        `:app` が知っているのも Route だけで、Screen と ViewModel はモジュールの中に閉じる。

        共通レイヤー（`:ui` と `:navigation`）を同じ group に入れていないのは、増え方が違うから。
        feature は「足すのが当たり前」の場所なので `":feature:*"` と書いてあり、
        `settings.gradle.kts` に `include(":feature:profile")` を足せば、この定義を1行も
        触らずに検査対象になる。共通レイヤーに1つ足すのは毎回が設計判断で、そちらは
        UI (共通レイヤー) group にある。

        feature 同士は互いに依存しない。別の画面へ遷移するときも、遷移先の決定は `:app` 側にあり、
        feature が受け取るのはコールバック1つ。つながりは `:app` の1箇所にしかないので、
        feature を消すときに他の feature を読み直さなくて済む。

        画面の部品とテストは、1つの feature の中で数が増えていく。どちらもファイル名を
        モジュール名で始め（`HomeUserCard.kt`、`HomeViewModelTest.kt`）、テンプレートから
        生成できる。画面の部品は `":feature:*"` の `*` に `feature` と名前を付けてあり、
        `--arg feature=home` で生成先のモジュールを選ぶので、feature を足してもこの定義は
        触らなくてよい。テストだけはテンプレートの中身が画面ごとに違うのでモジュールを
        1つずつ名指ししてあり、feature を足したら `FeatureModule` にも1行足す。
    """.trimIndent()

    screen()
    viewModel()
    route()
    featureComponent()
    featureTest()
}

/**
 * Where a feature module keeps its Kotlin sources: `src/main/kotlin` plus the module's own
 * package, which is the start of every path in this group.
 *
 * **This is the project's own vocabulary, written exactly the way katachi writes its own.**
 * `mainSourceSet`, `kotlin` and `modulePackage` are not members of `LayoutScope`; each is a
 * function taking the scope as a context parameter, so one more of them can be added from
 * outside katachi — from here — and reads at the call site like the ones that shipped with
 * it. `with(layoutScope)` is what hands the scope on to them.
 *
 * Nothing here is sugar the DSL had to be taught. `featureSources() / "X".ktFile()` declares
 * the same path `mainSourceSet / kotlin / modulePackage / "X".ktFile()` did, which is why
 * the recorded layout snapshot does not move when a role is rewritten to use it.
 *
 * `internal` and declared next to the group rather than inside one role's file, because the
 * roles of this group read it. The directory entries it builds are attributed to this
 * file; the `*.kt` entries that follow the `/` still belong to the role that wrote them, so a
 * violation keeps naming the role.
 */
context(layoutScope: LayoutScope)
internal fun featureSources(): LayoutDirectory = with(layoutScope) {
    mainSourceSet / kotlin / modulePackage
}

/**
 * The feature modules this project has, by name: `Home` is `:feature:home`.
 *
 * Only for a template whose *content* differs per module. Choosing the module a file is
 * generated into needs no list: `":feature:${capture("feature")}".module { }` names the
 * wildcard, and `--arg feature=home` binds it to a module that exists — which is how
 * FeatureComponent is written. FeatureTest arranges a different fake for each screen, so its
 * template is a `when` over this enum, and [eachFeatureModule] declares its layout module by
 * module, one `.template` per module with the module's own id
 * (`--arg template=feature.FeatureTest.home`); a new module fails to compile there until the
 * new screen is taught to it.
 *
 * The check keeps it honest in one direction: an entry whose module was deleted is reported
 * as that module's missing `build.gradle.kts`. A new module missing from it is not reported
 * until a file of that role is put there, which then shows up as `[UnexpectedFile]`
 * — add the entry when adding the module to `settings.gradle.kts`.
 */
enum class FeatureModule {
    Home,
    Settings,
    ;

    /** The module path, `:feature:home`. */
    val modulePath: String get() = ":feature:${name.lowercase()}"
}

/**
 * Declares [block] once for every [FeatureModule], inside that module.
 *
 * The concrete counterpart of `":feature:${capture("feature")}".module { }`: what
 * `wildcard("feature")` would have read arrives as the [FeatureModule] instead, for a template
 * that branches on it. A role that only picks the module should use the capture.
 */
context(layoutScope: LayoutScope)
internal fun eachFeatureModule(block: LayoutDirectoryScope.(FeatureModule) -> Unit) {
    with(layoutScope) {
        FeatureModule.entries.forEach { feature ->
            feature.modulePath.module {
                // Two or more places for one role want a sentence each on when to pick it.
                description = "`${feature.modulePath}` の分。ファイル名は `${feature.name}` で始める"
                block(feature)
            }
        }
    }
}
