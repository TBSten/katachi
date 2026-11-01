package com.example.sample.feature.home

import androidx.lifecycle.ViewModel
import com.example.sample.data.user.UserRepository
import com.example.sample.data.user.UserRepositoryImpl
import com.example.sample.ui.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class HomeContent(
    val userName: String,
    val visitCount: Int,
)

internal class HomeViewModel(
    private val userRepository: UserRepository = UserRepositoryImpl(),
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<UiState<HomeContent>>(UiState.Loading)

    val uiState: StateFlow<UiState<HomeContent>> = mutableUiState.asStateFlow()

    init {
        refresh()
    }

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
