package com.example.edusync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edusync.data.SessionManager
import com.example.edusync.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    val sessionUser = sessionManager.sessionUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    fun save(user: User) {
        viewModelScope.launch {
            sessionManager.saveSession(user)
        }
    }

    fun clear() {
        viewModelScope.launch {
            sessionManager.clearSession()
        }
    }
}
