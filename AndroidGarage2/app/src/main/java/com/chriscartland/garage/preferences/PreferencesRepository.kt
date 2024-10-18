package com.chriscartland.garage.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface PreferencesRepository {
    val isUserSignedIn: Flow<Boolean>
    suspend fun setUserSignedIn(isSignedIn: Boolean)
}

class PreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PreferencesRepository {
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
        private val IS_USER_SIGNED_IN = booleanPreferencesKey("is_user_signed_in")
    }

    override val isUserSignedIn: Flow<Boolean> = context.dataStore.data.map { preferences ->
        Log.d(TAG, "isUserSignedIn: ${preferences[IS_USER_SIGNED_IN]}")
        preferences[IS_USER_SIGNED_IN] ?: false
    }

    override suspend fun setUserSignedIn(isSignedIn: Boolean) {
        Log.d(TAG, "setUserSignedIn: $isSignedIn")
        context.dataStore.edit { preferences ->
            preferences[IS_USER_SIGNED_IN] = isSignedIn
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class UserPreferencesRepositoryModule {
    @Binds
    abstract fun bindUserPreferencesRepository(
        preferencesRepositoryImpl: PreferencesRepositoryImpl
    ): PreferencesRepository
}

private const val TAG = "UserPreferencesRepo"
