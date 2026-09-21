package com.example.kmp.app

import com.example.kmp.navigation.Destination
import com.example.kmp.navigation.Navigator
import com.example.kmp.testing.FakeUserRepository
import com.example.kmp.ui.core.UiState
import com.example.kmp.ui.core.valueOrNull
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The Compose-free parts of the sample's own code.
 *
 * Every other module of this build is Kotlin Multiplatform with only Android and iOS
 * targets, so `:app:android` is where anything that is not a `@Composable` gets exercised.
 * (`:architecture-test` is a JVM module too, but it holds the katachi definition and
 * nothing of the app.) The screens themselves are not tested here: that would need a
 * Compose test runtime, which is outside what this sample is for.
 */
class SampleModulesSpec : FreeSpec({
    "Navigator は初期状態でホームを指す" {
        Navigator().current.value shouldBe Destination.Home
    }

    "navigateTo で現在地が変わる" {
        val navigator = Navigator()
        navigator.navigateTo(Destination.Settings)
        navigator.current.value shouldBe Destination.Settings
    }

    "route 文字列から Destination を引ける" {
        Destination.ofRoute("settings") shouldBe Destination.Settings
        Destination.ofRoute("unknown") shouldBe null
    }

    "ナビゲーションバーに出す遷移先は宣言順" {
        Destination.topLevel.map { it.route } shouldContainExactly listOf("home", "settings")
    }

    "UiState.valueOrNull は Loaded のときだけ値を返す" {
        UiState.Loaded("x").valueOrNull() shouldBe "x"
        UiState.Loading.valueOrNull() shouldBe null
        UiState.Failed("boom").valueOrNull() shouldBe null
    }

    "テスト用のフェイクが :data のインターフェースを満たす" {
        FakeUserRepository(listOf("alice")).names() shouldContainExactly listOf("alice")
    }
})
