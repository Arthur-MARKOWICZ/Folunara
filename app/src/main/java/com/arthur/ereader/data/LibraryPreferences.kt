package com.arthur.ereader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arthur.ereader.domain.model.LibraryLayoutMode
import com.arthur.ereader.domain.model.LibrarySortMode
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

    suspend fun setLayout(value: LibraryLayoutMode) {
        context.libraryDataStore.edit { it[LAYOUT] = value.name }
    }

    suspend fun setSort(value: LibrarySortMode) {
        context.libraryDataStore.edit { it[SORT] = value.name }
    }

    private inline fun <reified T : Enum<T>> String.enumOrNull(): T? =
        runCatching { enumValueOf<T>(this) }.getOrNull()

    private companion object {
        val LAYOUT = stringPreferencesKey("layout")
        val SORT = stringPreferencesKey("sort")
    }
}
