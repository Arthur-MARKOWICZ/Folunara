package com.arthur.ereader.reader.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.EpubLayoutMode
import com.arthur.ereader.domain.model.EpubThemeMode
import com.arthur.ereader.domain.model.PdfReadingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

@Composable
fun PdfReadingContent(
    book: Book,
    state: PdfReflowState,
    settings: PdfReadingSettings,
    currentPage: Int,
    currentOffset: Int,
    onPositionChanged: (Int, Int) -> Unit,
    onShowOriginal: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (background, foreground) = readingColors(settings.theme)
    Surface(modifier.fillMaxSize(), color = background, contentColor = foreground) {
        when (state) {
            PdfReflowState.Idle, PdfReflowState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is PdfReflowState.Failure -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Não foi possível criar o modo repaginado.\n${state.message}")
            }
            is PdfReflowState.Ready -> {
                if (settings.layout == EpubLayoutMode.SCROLL) {
                    ScrollingReadingDocument(book, state.document, settings, currentPage, currentOffset, onPositionChanged, onShowOriginal)
                } else {
                    PagedReadingDocument(book, state.document, settings, currentPage, onShowOriginal)
                }
            }
        }
    }
}

@Composable
private fun ScrollingReadingDocument(
    book: Book,
    document: PdfReflowDocument,
    settings: PdfReadingSettings,
    currentPage: Int,
    currentOffset: Int,
    onPositionChanged: (Int, Int) -> Unit,
    onShowOriginal: (Int) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentPage.coerceIn(0, (document.pages.size - 1).coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = currentOffset.coerceAtLeast(0),
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (page, offset) -> onPositionChanged(page, offset) }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        items(document.pages, key = { it.index }) { page ->
            ReadingPage(book, page, settings, onShowOriginal, Modifier.fillMaxWidth().widthIn(max = 900.dp))
        }
    }
}

@Composable
private fun PagedReadingDocument(
    book: Book,
    document: PdfReflowDocument,
    settings: PdfReadingSettings,
    currentPage: Int,
    onShowOriginal: (Int) -> Unit,
) {
    val safe = currentPage.coerceIn(0, (document.pages.size - 1).coerceAtLeast(0))
    Box(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        document.pages.getOrNull(safe)?.let { page ->
            ReadingPage(book, page, settings, onShowOriginal, Modifier.fillMaxWidth().widthIn(max = 900.dp))
        }
    }
}

@Composable
private fun ReadingPage(
    book: Book,
    page: PdfExtractedPage,
    settings: PdfReadingSettings,
    onShowOriginal: (Int) -> Unit,
    modifier: Modifier,
) {
    val margin = (16f * settings.pageMargins).dp
    Column(modifier.padding(horizontal = margin, vertical = 16.dp)) {
        Text("Página ${page.index + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        if (page.classification == PdfPageClassification.REFLOWABLE) {
            val size = 18f * settings.fontScale
            Text(
                text = page.reflowText,
                style = TextStyle(fontSize = size.sp, lineHeight = (size * settings.lineHeight).sp),
            )
        } else {
            Text("Página preservada", style = MaterialTheme.typography.titleSmall)
            if (page.diagnostics.reasons.isNotEmpty()) {
                Text(page.diagnostics.reasons.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
            }
            PreservedPdfPage(book, page.index, Modifier.fillMaxWidth().padding(top = 8.dp))
            TextButton(onClick = { onShowOriginal(page.index) }) { Text("Ver no modo Original") }
        }
    }
}

@Composable
private fun PreservedPdfPage(book: Book, pageIndex: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, book.uri, pageIndex) {
        value = withContext(Dispatchers.IO) {
            context.contentResolver.openFileDescriptor(book.uri.toUri(), "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(pageIndex).use { page ->
                        val scale = sqrt(MAX_PREVIEW_PIXELS.toDouble() / (page.width.toLong() * page.height)).coerceAtMost(2.0).toFloat()
                        Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888).also {
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    }
    DisposableEffect(bitmap) {
        val current = bitmap
        onDispose { current?.takeIf { !it.isRecycled }?.recycle() }
    }
    bitmap?.let { image ->
        Image(image.asImageBitmap(), contentDescription = "Página ${pageIndex + 1} preservada", modifier = modifier, contentScale = ContentScale.FillWidth)
    } ?: Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

private fun readingColors(theme: EpubThemeMode): Pair<Color, Color> = when (theme) {
    EpubThemeMode.LIGHT -> Color(0xFFFAFAFA) to Color(0xFF161616)
    EpubThemeMode.SEPIA -> Color(0xFFF4ECD8) to Color(0xFF3D3428)
    EpubThemeMode.DARK -> Color(0xFF161616) to Color(0xFFE8E8E8)
}

private const val MAX_PREVIEW_PIXELS = 8_000_000L
