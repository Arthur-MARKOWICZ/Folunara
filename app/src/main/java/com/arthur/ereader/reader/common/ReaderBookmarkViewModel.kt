package com.arthur.ereader.reader.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.ereader.data.BookRepository
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.Bookmark
import com.arthur.ereader.domain.model.ReaderLocator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderBookmarkViewModel @Inject constructor(
    private val books: BookRepository,
) : ViewModel() {
    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks = _bookmarks.asStateFlow()
    private var observation: Job? = null

    fun load(book: Book) {
        observation?.cancel()
        _bookmarks.value = emptyList()
        observation = viewModelScope.launch {
            books.observeBookmarks(book).collect(_bookmarks::emit)
        }
    }

    fun toggle(book: Book, locator: ReaderLocator, title: String?) = viewModelScope.launch {
        books.toggleBookmark(book, locator, title)
    }
}

fun List<Bookmark>.containsPosition(locator: ReaderLocator): Boolean =
    any { it.locator.samePositionAs(locator) }
