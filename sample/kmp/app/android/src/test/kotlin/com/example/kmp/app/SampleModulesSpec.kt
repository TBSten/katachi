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
    "Navigator starts at home" {
        Navigator().current.value shouldBe Destination.Home
    }

    "navigateTo changes the current destination" {
        val navigator = Navigator()
        navigator.navigateTo(Destination.Settings)
        navigator.current.value shouldBe Destination.Settings
    }

    "Destination can be looked up from a route string" {
        Destination.ofRoute("settings") shouldBe Destination.Settings
        Destination.ofRoute("unknown") shouldBe null
    }

    "Destinations shown in the navigation bar follow declaration order" {
        Destination.topLevel.map { it.route } shouldContainExactly listOf("home", "settings")
    }

    "UiState.valueOrNull returns a value only when Loaded" {
        UiState.Loaded("x").valueOrNull() shouldBe "x"
        UiState.Loading.valueOrNull() shouldBe null
        UiState.Failed("boom").valueOrNull() shouldBe null
    }

    "The test fake satisfies the :data interface" {
        FakeUserRepository(listOf("alice")).names() shouldContainExactly listOf("alice")
    }
})
