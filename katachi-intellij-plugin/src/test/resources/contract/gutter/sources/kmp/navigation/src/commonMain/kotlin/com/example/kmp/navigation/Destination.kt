package com.example.kmp.navigation

/** Every place the app can navigate to. */
sealed interface Destination {
    val route: String

    /** What the destination is called in the navigation bar. */
    val label: String

    data object Home : Destination {
        override val route: String = "home"
        override val label: String = "Home"
    }

    data object Settings : Destination {
        override val route: String = "settings"
        override val label: String = "Settings"
    }

    companion object {
        /** The destinations the navigation bar offers, in the order it shows them. */
        val topLevel: List<Destination> = listOf(Home, Settings)

        fun ofRoute(route: String): Destination? = topLevel.firstOrNull { it.route == route }
    }
}
