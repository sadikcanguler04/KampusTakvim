package com.example.edusync.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase {
        // Simplified to prevent startup hangs. 
        // Persistence is explicitly disabled to avoid SQLite I/O blocking on API 34/35 emulators.
        val databaseUrl = "https://kampustakvim-default-rtdb.europe-west1.firebasedatabase.app/"
        val database = FirebaseDatabase.getInstance(databaseUrl)
        database.setPersistenceEnabled(false)
        return database
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
}
