package com.example.kmp.app

import com.example.kmp.navigation.Destination
import com.example.kmp.navigation.Navigator
import com.example.kmp.testing.FakeUserRepository
import com.example.kmp.ui.core.UiState
import com.example.kmp.ui.core.valueOrNull
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

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
