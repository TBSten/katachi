package com.example.kmp.navigation

/** Every place the app can navigate to. */
sealed interface Destination {
    val route: String

    data object Home : Destination {
        override val route: String = "home"
    }

    data object Settings : Destination {
        override val route: String = "settings"
    }
}
