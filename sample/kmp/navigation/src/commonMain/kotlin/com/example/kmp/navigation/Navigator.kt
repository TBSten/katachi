package com.example.kmp.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the app currently is.
 *
 * Hand-rolled on purpose: the sample is about where files live, so it carries the navigation
 * state itself instead of pulling in a navigation library whose graph would live in yet
 * another place.
 */
class Navigator(start: Destination = Destination.Home) {
    private val mutableCurrent = MutableStateFlow(start)

    val current: StateFlow<Destination> = mutableCurrent.asStateFlow()

    fun navigateTo(destination: Destination) {
        mutableCurrent.value = destination
    }
}
