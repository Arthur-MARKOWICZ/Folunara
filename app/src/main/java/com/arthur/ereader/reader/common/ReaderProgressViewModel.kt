package com.arthur.ereader.reader.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthur.ereader.data.BookRepository
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.ReaderLocator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReaderProgressViewModel @Inject constructor(private val books: BookRepository) : ViewModel() {
    private val _savedLocator = MutableStateFlow<ReaderLocator?>(null)
    val savedLocator = _savedLocator.asStateFlow()
    private val _positionLoaded = MutableStateFlow(false)
    val positionLoaded = _positionLoaded.asStateFlow()
    fun load(book: Book) = viewModelScope.launch {
        _positionLoaded.value = false
        _savedLocator.value = books.progress(book.id)?.locator
        _positionLoaded.value = true
    }

    fun save(book: Book, locator: ReaderLocator, progress: Float) = viewModelScope.launch {
        books.saveProgress(book.id, locator, progress)
    }

    fun saveAndThen(book: Book, locator: ReaderLocator, progress: Float, done: () -> Unit) = viewModelScope.launch {
        runCatching { books.saveProgress(book.id, locator, progress) }
        done()
    }
}
