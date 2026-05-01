package com.example.edusync.data

import com.google.firebase.database.*
import com.example.edusync.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserRepository @Inject constructor(
    private val database: FirebaseDatabase
) {
    private val usersRef = database.getReference("users")
    private val codesRef = database.getReference("verification_codes")
    private val temporaryCredentialsRef = database.getReference("temporary_credentials")

    suspend fun getUserByUsername(username: String): User? = withContext(Dispatchers.IO) {
        val snapshot = usersRef.child(username).get().await()
        snapshot.getValue(User::class.java)
    }

    suspend fun insertUser(user: User) = withContext(Dispatchers.IO) {
        usersRef.child(user.username).setValue(user).await()
    }

    suspend fun changePassword(username: String, currentPassword: String, newPassword: String): User? = withContext(Dispatchers.IO) {
        val snapshot = usersRef.child(username).get().await()
        val user = snapshot.getValue(User::class.java) ?: return@withContext null
        val currentHash = withContext(Dispatchers.Default) { SecurityUtils.hashPassword(currentPassword) }
        if (user.password != currentHash) return@withContext null

        val newHash = withContext(Dispatchers.Default) { SecurityUtils.hashPassword(newPassword) }
        val updatedUser = user.copy(password = newHash, mustChangePassword = false)
        usersRef.child(username).setValue(updatedUser).await()
        user.teacherId?.let { teacherId ->
            temporaryCredentialsRef.child(teacherId.toString()).updateChildren(
                mapOf(
                    "temporaryPassword" to "",
                    "consumed" to true,
                    "consumedAt" to System.currentTimeMillis()
                )
            ).await()
        }
        updatedUser
    }

    suspend fun getUserByTeacherId(teacherId: Int): User? = withContext(Dispatchers.IO) {
        val snapshot = usersRef.get().await()
        withContext(Dispatchers.Default) {
            snapshot.children.mapNotNull { it.getValue(User::class.java) }
                .find { it.teacherId == teacherId }
        }
    }

    fun getAllUsersFlow(): Flow<List<User>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // PDF Optimization: Offload parsing to Default dispatcher
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(User::class.java) }
                    trySend(list)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        usersRef.addValueEventListener(listener)
        awaitClose { usersRef.removeEventListener(listener) }
    }.flowOn(Dispatchers.IO)

    fun getAllVerificationCodes(): Flow<List<VerificationCode>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                launch(Dispatchers.Default) {
                    val list = snapshot.children.mapNotNull { it.getValue(VerificationCode::class.java) }
                    val decryptedList = list.map { vc ->
                        val decryptedCode = SecurityUtils.decrypt(vc.code)
                        vc.copy(code = decryptedCode)
                    }
                    trySend(decryptedList)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        codesRef.addValueEventListener(listener)
        awaitClose { codesRef.removeEventListener(listener) }
    }.flowOn(Dispatchers.IO)

    suspend fun getValidVerificationCode(inputCode: String): VerificationCode? = withContext(Dispatchers.IO) {
        val snapshot = codesRef.get().await()
        withContext(Dispatchers.Default) {
            val allCodes = snapshot.children.mapNotNull { it.getValue(VerificationCode::class.java) }
            allCodes.find { 
                val decrypted = SecurityUtils.decrypt(it.code)
                decrypted == inputCode && !it.isUsed 
            }
        }
    }

    suspend fun updateVerificationCode(code: VerificationCode) = withContext(Dispatchers.IO) {
        // PDF Optimization: Decrypt/Encrypt on Default dispatcher
        val rawCode = withContext(Dispatchers.Default) { 
            SecurityUtils.decrypt(code.code)
        }
        val encryptedCodeValue = withContext(Dispatchers.Default) { SecurityUtils.encrypt(rawCode) }
        val encryptedObject = code.copy(code = encryptedCodeValue)
        codesRef.child(encryptedCodeValue.hashCode().toString()).setValue(encryptedObject).await()
    }

    suspend fun insertVerificationCode(code: VerificationCode) = withContext(Dispatchers.IO) {
        val encryptedCodeValue = withContext(Dispatchers.Default) { SecurityUtils.encrypt(code.code) }
        val encryptedObject = code.copy(code = encryptedCodeValue)
        codesRef.child(encryptedCodeValue.hashCode().toString()).setValue(encryptedObject).await()
    }
}
