package com.example.edusync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.edusync.util.SecurityUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "secure_session")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val username = stringPreferencesKey("username")
        val role = stringPreferencesKey("role")
        val teacherId = stringPreferencesKey("teacher_id")
        val mustChangePassword = stringPreferencesKey("must_change_password")
    }

    val sessionUser: Flow<User?> = context.sessionDataStore.data.map { preferences ->
        val username = preferences[Keys.username]?.let(SecurityUtils::decrypt).orEmpty()
        if (username.isBlank()) {
            null
        } else {
            val role = preferences[Keys.role]
                ?.let(SecurityUtils::decrypt)
                ?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }
                ?: UserRole.TEACHER

            User(
                username = username,
                role = role,
                teacherId = preferences[Keys.teacherId]?.let(SecurityUtils::decrypt)?.toIntOrNull(),
                mustChangePassword = preferences[Keys.mustChangePassword]
                    ?.let(SecurityUtils::decrypt)
                    ?.toBooleanStrictOrNull()
                    ?: false
            )
        }
    }

    suspend fun saveSession(user: User) {
        context.sessionDataStore.edit { preferences ->
            preferences[Keys.username] = SecurityUtils.encrypt(user.username)
            preferences[Keys.role] = SecurityUtils.encrypt(user.role.name)
            preferences[Keys.teacherId] = SecurityUtils.encrypt(user.teacherId?.toString().orEmpty())
            preferences[Keys.mustChangePassword] = SecurityUtils.encrypt(user.mustChangePassword.toString())
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}
