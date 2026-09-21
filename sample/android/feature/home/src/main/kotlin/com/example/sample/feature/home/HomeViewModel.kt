package com.example.sample.feature.home

import androidx.lifecycle.ViewModel
import com.example.sample.data.user.UserRepository
import com.example.sample.data.user.UserRepositoryImpl
import com.example.sample.ui.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What [HomeScreen] draws once it has something to draw. */
data class HomeContent(
    val userName: String,
    val visitCount: Int,
)

/**
 * State holder of [HomeScreen].
 *
 * The default argument is what lets `viewModel()` build this without a factory. A real
 * app would inject the repository instead; the shape of the class is the same either way.
 */
class HomeViewModel(
    private val userRepository: UserRepository = UserRepositoryImpl(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<UiState<HomeContent>>(UiState.Loading)

    /** State of the home screen, collected by the composable. */
    val uiState: StateFlow<UiState<HomeContent>> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

    /** Reads the current user again and republishes the screen state. */
    fun refresh() {
        val previous = (mutableUiState.value as? UiState.Content)?.value
        mutableUiState.value = UiState.Content(
            HomeContent(
                userName = userRepository.currentUserName(),
                visitCount = (previous?.visitCount ?: 0) + 1,
            ),
        )
    }
}
