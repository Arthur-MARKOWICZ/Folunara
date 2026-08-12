package com.arthur.ereader.reader.epub

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.arthur.ereader.domain.model.Book

@Composable fun EpubReaderScreen(book: Book, onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(book.id) { context.startActivity(Intent(context, EpubReaderActivity::class.java).putExtra(EpubReaderActivity.EXTRA_BOOK_ID, book.id)); onBack() }
}
