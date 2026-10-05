package com.example.sample.feature.home

import com.example.sample.testing.FakeUserRepository
import com.example.sample.ui.core.UiState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun `exposes the value read from the repository as Content`() {
        val viewModel = HomeViewModel(userRepository = FakeUserRepository(userName = "katachi"))

        assertEquals(UiState.Content(HomeContent(userName = "katachi", visitCount = 1)), viewModel.uiState.value)
    }
}
