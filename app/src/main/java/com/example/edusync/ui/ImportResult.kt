package com.example.edusync.ui

import com.example.edusync.data.GeneratedCredential

/**
 * Phase 2 - Asynchronous State Management
 * Excel aktarım durumlarını merkezi bir yerden yönetmek için kullanılır.
 */
sealed class ImportResult {
    object Loading : ImportResult()
    data class Success(
        val count: Int,
        val generatedCredentials: List<GeneratedCredential> = emptyList()
    ) : ImportResult()
    data class Error(val message: String) : ImportResult()
}
