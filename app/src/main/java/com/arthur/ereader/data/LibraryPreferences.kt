package com.arthur.ereader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arthur.ereader.domain.model.LibraryLayoutMode
import com.arthur.ereader.domain.model.LibrarySortMode
import com.arthur.ereader.domain.model.AutomationMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.libraryDataStore by preferencesDataStore(name = "library_ui")

@Singleton
class LibraryPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val layout: Flow<LibraryLayoutMode> = context.libraryDataStore.data.map { preferences ->
        preferences[LAYOUT]?.enumOrNull<LibraryLayoutMode>() ?: LibraryLayoutMode.GRID
    }

    val sort: Flow<LibrarySortMode> = context.libraryDataStore.data.map { preferences ->
        preferences[SORT]?.enumOrNull<LibrarySortMode>() ?: LibrarySortMode.RECENTLY_ADDED
    }

    val automationMode: Flow<AutomationMode> = context.libraryDataStore.data.map { preferences ->
        preferences[AUTOMATION_MODE]?.enumOrNull<AutomationMode>() ?: AutomationMode.ASK
    }

    val externalMetadataEnabled: Flow<Boolean> = context.libraryDataStore.data.map { preferences ->
        preferences[EXTERNAL_METADATA_ENABLED] ?: false
    }

    suspend fun setLayout(value: LibraryLayoutMode) {
        context.libraryDataStore.edit { it[LAYOUT] = value.name }
    }

    suspend fun setSort(value: LibrarySortMode) {
        context.libraryDataStore.edit { it[SORT] = value.name }
    }

    suspend fun setAutomationMode(value: AutomationMode) {
        context.libraryDataStore.edit { it[AUTOMATION_MODE] = value.name }
    }

    suspend fun setExternalMetadataEnabled(value: Boolean) {
        context.libraryDataStore.edit { it[EXTERNAL_METADATA_ENABLED] = value }
    }

    private inline fun <reified T : Enum<T>> String.enumOrNull(): T? =
        runCatching { enumValueOf<T>(this) }.getOrNull()

    private companion object {
        val LAYOUT = stringPreferencesKey("layout")
        val SORT = stringPreferencesKey("sort")
        val AUTOMATION_MODE = stringPreferencesKey("organization_automation_mode")
        val EXTERNAL_METADATA_ENABLED = booleanPreferencesKey("external_metadata_enabled")
    }
}
