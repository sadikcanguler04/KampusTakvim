package com.example.edusync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edusync.data.FirebaseUserRepository
import com.example.edusync.data.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PasswordChangeResult {
    object Idle : PasswordChangeResult()
    object Loading : PasswordChangeResult()
    data class Success(val user: User) : PasswordChangeResult()
    data class Error(val message: String) : PasswordChangeResult()
}

@HiltViewModel
class PasswordChangeViewModel @Inject constructor(
    private val userRepository: FirebaseUserRepository
) : ViewModel() {
    private val _state = MutableStateFlow<PasswordChangeResult>(PasswordChangeResult.Idle)
    val state = _state.asStateFlow()

    fun changePassword(username: String, currentPassword: String, newPassword: String, confirmPassword: String) {
        if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
            _state.value = PasswordChangeResult.Error("Tum alanlari doldurun")
            return
        }
        if (newPassword.length < 6) {
            _state.value = PasswordChangeResult.Error("Yeni sifre en az 6 karakter olmali")
            return
        }
        if (newPassword != confirmPassword) {
            _state.value = PasswordChangeResult.Error("Yeni sifreler eslesmiyor")
            return
        }
        if (currentPassword == newPassword) {
            _state.value = PasswordChangeResult.Error("Yeni sifre gecici sifreden farkli olmali")
            return
        }

        viewModelScope.launch {
            _state.value = PasswordChangeResult.Loading
            try {
                val updatedUser = userRepository.changePassword(username, currentPassword, newPassword)
                _state.value = if (updatedUser != null) {
                    PasswordChangeResult.Success(updatedUser)
                } else {
                    PasswordChangeResult.Error("Mevcut sifre hatali")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = PasswordChangeResult.Error("Sifre guncellenemedi")
            }
        }
    }

    fun resetState() {
        _state.value = PasswordChangeResult.Idle
    }
}
