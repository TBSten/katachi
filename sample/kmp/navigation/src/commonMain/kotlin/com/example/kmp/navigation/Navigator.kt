package com.example.kmp.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Navigator(start: Destination = Destination.Home) {
    private val mutableCurrent = MutableStateFlow(start)

    val current: StateFlow<Destination> = mutableCurrent.asStateFlow()

    fun navigateTo(destination: Destination) {
        mutableCurrent.value = destination
    }
}
