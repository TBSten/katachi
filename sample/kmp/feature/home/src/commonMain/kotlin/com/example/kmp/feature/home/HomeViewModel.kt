package com.example.kmp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kmp.data.user.UserRepository
import com.example.kmp.ui.core.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class HomeViewModel(private val repository: UserRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<UiState<List<String>>>(UiState.Loading)

    val state: StateFlow<UiState<List<String>>> = mutableState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        mutableState.value = UiState.Loading
        viewModelScope.launch {
            mutableState.value = UiState.Loaded(repository.names())
        }
    }
}
