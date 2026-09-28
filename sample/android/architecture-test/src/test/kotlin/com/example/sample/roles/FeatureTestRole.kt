package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.FeatureModule
import com.example.sample.groups.eachFeatureModule
import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/**
 * The role of a feature module's own unit tests, which stand the ViewModel on `:testing`'s fakes.
 *
 * Named module by module through [FeatureModule], so that the template can write into one of
 * them: `HomeViewModelTest.kt` fits only `:feature:home`'s pattern.
 *
 * Not `":feature:${capture("feature")}".module { }` like FeatureComponent, because what the
 * test arranges differs per screen: the template is a `when` over [FeatureModule], which a new
 * module cannot slip past, while a captured value is only a string.
 */
fun DeclarationContainerScope.featureTest() = "FeatureTest" {
    title = "画面のテスト"
    summary = ":feature:<name> の src/test に置く、ViewModel を :testing のフェイクで動かすテスト"
    description = """
        feature モジュールの単体テスト。`:feature:home` なら `src/test` の同じ package に
        `HomeViewModelTest.kt` を置き、ファイル名はモジュール名（`Home`）で始めて `Test` で終える。

        ViewModel の Repository はコンストラクタ引数なので、テストでは `:testing` の
        `FakeUserRepository` のようなフェイクを渡して、流れてくる `UiState` を確かめる。
        Compose にも Android の実機にも触らないので、JVM の上だけで走る。
        feature モジュールの `build.gradle.kts` が `testImplementation(project(":testing"))` を
        持つのはこのため。

        テンプレートから、その feature の ViewModel をフェイクで組み立てるテストを生成できる。
        画面ごとに id が分かれていて（`feature.FeatureTest.home` / `.settings`）、
        `--arg template=feature.FeatureTest.home --arg name=ViewModel` で `HomeViewModelTest.kt`
        になる。中身は画面ごとに違う（フェイクとその引数、期待する `Content`）ので、
        値は渡さず id で選ぶ。
    """.trimIndent()
    forbiddenContents = """
        - 本物の Repository（`*RepositoryImpl`）。差し替えは `:testing` のフェイクで行う
        - 画面の描画のテスト。Compose のテストが要るなら役割を分ける
    """.trimIndent()
    example("HomeViewModelTest", "HomeViewModel をフェイクで動かすテスト")
    example("SettingsViewModelTest", "SettingsViewModel をフェイクで動かすテスト")
    // What the test arranges differs per screen, so the content is a `when` over the module
    // ([featureTestContent]), which a new FeatureModule entry fails to compile until it is
    // taught here. One `.template` per module, attached inside [eachFeatureModule] with an id
    // made from the module (`home` / `settings`).
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg template=feature.FeatureTest.home --arg name=ViewModel
    layout {
        eachFeatureModule { feature ->
            testSourceSet / kotlin / modulePackage / "${feature.name}${capture("name")}Test".ktFile()
                .template(id = feature.name.lowercase()) {
                    featureTestContent(feature, captureValue("name"))
                }
        }
    }
}

/** The body [featureTest]'s `.template { }` writes, for one [FeatureModule]. */
private fun featureTestContent(feature: FeatureModule, name: String): String {
    val testClass = "${feature.name}${name}Test"
    val viewModel = "${feature.name}ViewModel"
    val (fake, arrange, expected) = when (feature) {
        FeatureModule.Home -> Triple(
            "FakeUserRepository",
            "userRepository = FakeUserRepository(userName = \"katachi\")",
            "HomeContent(userName = \"katachi\", visitCount = 1)",
        )
        FeatureModule.Settings -> Triple(
            "FakeSettingsRepository",
            "settingsRepository = FakeSettingsRepository(darkThemeEnabled = true)",
            "SettingsContent(darkThemeEnabled = true)",
        )
    }

    return """
        package com.example.sample.feature.${feature.name.lowercase()}

        import com.example.sample.testing.$fake
        import com.example.sample.ui.core.UiState
        import org.junit.Assert.assertEquals
        import org.junit.Test

        class $testClass {
            @Test
            fun `リポジトリから読んだ値を Content として公開する`() {
                val viewModel = $viewModel($arrange)

                assertEquals(UiState.Content($expected), viewModel.uiState.value)
            }
        }
    """.trimIndent() + "\n"
}
