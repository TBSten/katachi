package com.example.sample.roles

import com.example.sample.forbiddenContents
import com.example.sample.groups.FeatureModule
import com.example.sample.groups.eachFeatureModule
import com.example.sample.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of a feature module's own unit tests, which stand the ViewModel on `:testing`'s fakes.
 *
 * Named module by module through [FeatureModule], like FeatureComponent, so that the template can
 * write into one of them: `HomeViewModelTest.kt` fits only `:feature:home`'s pattern.
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
        `--arg feature=Home --arg name=ViewModel` で `HomeViewModelTest.kt` になる。
    """.trimIndent()
    forbiddenContents = """
        - 本物の Repository（`*RepositoryImpl`）。差し替えは `:testing` のフェイクで行う
        - 画面の描画のテスト。Compose のテストが要るなら役割を分ける
    """.trimIndent()
    example("HomeViewModelTest", "HomeViewModel をフェイクで動かすテスト")
    example("SettingsViewModelTest", "SettingsViewModel をフェイクで動かすテスト")
    layout {
        eachFeatureModule { feature ->
            testSourceSet / kotlin / modulePackage / "${feature.name}*Test".ktFile()
        }
    }
    // What the test arranges differs per screen, so it is a `when` over the modules: adding a
    // FeatureModule entry makes this fail to compile until the new screen is taught here.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg roleName=FeatureTest --arg feature=Home --arg name=ViewModel
    template {
        val feature by enumParameter(FeatureModule.entries)
        val name by stringParameter(default = "ViewModel")
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

        file("$testClass.kt") {
            """
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
            """.trimIndent()
        }
    }
}
