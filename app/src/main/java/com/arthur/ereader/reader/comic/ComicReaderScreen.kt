package com.arthur.ereader.reader.comic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthur.ereader.core.files.FormatTools
import com.arthur.ereader.domain.model.*
import com.arthur.ereader.feature.settings.BookSettingsSheet
import com.arthur.ereader.reader.common.ReaderProgressViewModel
import com.arthur.ereader.reader.common.ReaderBookmarkViewModel
import com.arthur.ereader.reader.common.containsPosition
import com.arthur.ereader.reader.common.ReaderSettingsViewModel
import com.arthur.ereader.reader.common.ReaderChromeOverlay
import com.arthur.ereader.reader.common.ReaderBookmarksDialog
import com.arthur.ereader.reader.common.readerGestures
import com.arthur.ereader.reader.common.readerTaps
import com.arthur.ereader.reader.common.rememberReaderChromeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicReaderScreen(
    book: Book,
    onBack: () -> Unit,
    vm: ReaderProgressViewModel = hiltViewModel(),
    bookmarkVm: ReaderBookmarkViewModel = hiltViewModel(),
    settingsVm: ReaderSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val saved by vm.savedLocator.collectAsStateWithLifecycle()
    val positionLoaded by vm.positionLoaded.collectAsStateWithLifecycle()
    val settingsFlow = remember(book.id, settingsVm) { settingsVm.effectiveSettings(book.id) }
    val effectiveSettings by settingsFlow.collectAsStateWithLifecycle(initialValue = GlobalReaderSettings())
    val settings = effectiveSettings.comic
    val bookmarks by bookmarkVm.bookmarks.collectAsStateWithLifecycle()
    var archive by remember { mutableStateOf<ComicArchive?>(null) }
    var page by remember(book.id) { mutableIntStateOf(0) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var isClosing by remember(book.id) { mutableStateOf(false) }
    val chrome = rememberReaderChromeState()
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val verticalState = rememberLazyListState()

    LaunchedEffect(book.id) {
        vm.load(book)
        bookmarkVm.load(book)
    }
    LaunchedEffect(book.uri) {
        runCatching {
            withContext(Dispatchers.IO) { openArchive(context.cacheDir, book, context.contentResolver) }
        }.onSuccess {
            archive?.close()
            archive = it
            error = if (it.entries.isEmpty()) "Este CBZ não contém imagens compatíveis." else null
        }.onFailure {
            error = "Não foi possível abrir o CBZ: ${it.message ?: "arquivo inválido."}"
        }
    }
    DisposableEffect(archive) {
        val openedArchive = archive
        onDispose { openedArchive?.close() }
    }

    LaunchedEffect(saved, archive, positionLoaded, settings.direction, settings.displayMode) {
        val count = archive?.entries?.size ?: 0
        if (positionLoaded && count > 0) {
            page = saved?.pageOrNull()?.coerceIn(0, count - 1)
                ?: if (settings.direction == ReadingDirection.RTL) count - 1 else 0
            if (settings.displayMode == ComicDisplayMode.VERTICAL) verticalState.scrollToItem(page)
        }
    }
    LaunchedEffect(archive, page, settings.displayMode) {
        val source = archive ?: return@LaunchedEffect
        if (settings.displayMode == ComicDisplayMode.PAGED && source.entries.isNotEmpty()) {
            runCatching { withContext(Dispatchers.IO) { source.decode(page) } }
                .onSuccess { image = it; error = null }
                .onFailure { error = "Não foi possível renderizar a página: ${it.message}" }
        }
    }
    DisposableEffect(image) {
        val displayedImage = image
        onDispose { displayedImage?.takeIf { !it.isRecycled }?.recycle() }
    }
    LaunchedEffect(verticalState, settings.displayMode, archive) {
        if (settings.displayMode == ComicDisplayMode.VERTICAL && archive != null) {
            snapshotFlow { verticalState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collectLatest { index ->
                    delay(400)
                    page = index
                }
        }
    }
    LaunchedEffect(settings) { scale = 1f; pan = Offset.Zero }

    val total = archive?.entries?.size ?: 0
    val currentLocator = ReaderLocator.page(book.format, page)
    fun closeReader() {
        if (isClosing) return
        isClosing = true
        if (positionLoaded && total > 0) {
            vm.saveAndThen(book, currentLocator, progressForPage(page, total), onBack)
        } else {
            onBack()
        }
    }
    BackHandler {
        when {
            showSettings -> showSettings = false
            showBookmarks -> showBookmarks = false
            else -> closeReader()
        }
    }
    LaunchedEffect(page, total, positionLoaded) {
        if (positionLoaded && total > 0) {
            vm.save(book, ReaderLocator.page(book.format, page), progressForPage(page, total))
        }
    }
    DisposableEffect(book.id, page, total, positionLoaded) {
        onDispose {
            if (positionLoaded && total > 0) {
                vm.save(book, ReaderLocator.page(book.format, page), progressForPage(page, total))
            }
        }
    }

    fun previous() {
        if (settings.direction == ReadingDirection.RTL) {
            if (page < total - 1) page++
        } else if (page > 0) page--
    }
    fun next() {
        if (settings.direction == ReadingDirection.RTL) {
            if (page > 0) page--
        } else if (page < total - 1) page++
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(Modifier.fillMaxSize()) {
                when {
                    error != null -> Text(error!!, Modifier.padding(24.dp))
                    archive == null -> Text("Abrindo quadrinho…", Modifier.padding(24.dp))
                    settings.displayMode == ComicDisplayMode.VERTICAL -> LazyColumn(
                        modifier = Modifier.fillMaxSize().readerTaps(
                            onPrevious = {},
                            onNext = {},
                            onToggleControls = chrome::toggle,
                            doubleTapEnabled = { false },
                        ),
                        state = verticalState,
                    ) {
                        itemsIndexed(archive!!.entries, key = { index, entry -> "$index:${entry.name}" }) { index, _ ->
                            val itemModifier = if (settings.fitMode == FitMode.WIDTH) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.fillParentMaxHeight().fillMaxWidth()
                            }
                            ComicPage(archive!!, index, settings.fitMode, itemModifier)
                        }
                    }
                    image != null -> Image(
                        bitmap = image!!.asImageBitmap(),
                        contentDescription = "Página ${page + 1}",
                        contentScale = settings.fitMode.toContentScale(),
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { viewport = it }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = pan.x,
                                translationY = pan.y,
                            )
                            .readerGestures(
                                viewport = { viewport },
                                scale = { scale },
                                onPrevious = ::previous,
                                onNext = ::next,
                                onToggleControls = chrome::toggle,
                                onZoomPan = { zoom, x, y ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    pan = if (scale <= 1.01f) Offset.Zero else pan + Offset(x, y)
                                },
                            ),
                    )
                }
        }
        ReaderChromeOverlay(
            state = chrome,
            title = book.title,
            onBack = ::closeReader,
            onSettings = { showSettings = true },
            progress = progressForPage(page, total),
            positionLabel = if (total > 0) "Página ${page + 1} de $total" else "Abrindo quadrinho",
            bookmarked = bookmarks.containsPosition(currentLocator),
            bookmarkEnabled = total > 0,
            onToggleBookmark = {
                bookmarkVm.toggle(book, currentLocator, "Página ${page + 1}")
                chrome.show()
            },
            onShowBookmarks = { showBookmarks = true },
            topActions = {
                if (settings.displayMode == ComicDisplayMode.PAGED) {
                    TextButton(onClick = { chrome.show(); scale = 1f; pan = Offset.Zero }) { Text("1×") }
                }
            },
            bottomActions = {
                TextButton(enabled = if (settings.direction == ReadingDirection.RTL) page < total - 1 else page > 0, onClick = { chrome.show(); previous() }) {
                    Text("Anterior")
                }
                TextButton(enabled = if (settings.direction == ReadingDirection.RTL) page > 0 else page < total - 1, onClick = { chrome.show(); next() }) {
                    Text("Próxima")
                }
            },
        )
    }
    if (showSettings) BookSettingsSheet(book, { showSettings = false }, settingsVm)
    if (showBookmarks) {
        ReaderBookmarksDialog(
            bookmarks = bookmarks,
            onDismiss = { showBookmarks = false },
            onSelect = { bookmark ->
                page = bookmark.locator.pageOrNull()?.coerceIn(0, (total - 1).coerceAtLeast(0)) ?: page
                showBookmarks = false
                chrome.show()
            },
        )
    }
}

@Composable
private fun ComicPage(
    archive: ComicArchive,
    index: Int,
    fitMode: FitMode,
    modifier: Modifier,
) {
    var bitmap by remember(archive, index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(archive, index) {
        bitmap = withContext(Dispatchers.IO) { runCatching { archive.decode(index) }.getOrNull() }
    }
    DisposableEffect(bitmap) {
        val displayedBitmap = bitmap
        onDispose { displayedBitmap?.takeIf { !it.isRecycled }?.recycle() }
    }
    Box(modifier) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Página ${index + 1}",
                contentScale = fitMode.toContentScale(),
                modifier = if (fitMode == FitMode.WIDTH) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
            )
        } ?: CircularProgressIndicator(Modifier.padding(24.dp))
    }
}

private fun FitMode.toContentScale() = when (this) {
    FitMode.PAGE -> ContentScale.Fit
    FitMode.WIDTH -> ContentScale.FillWidth
    FitMode.HEIGHT -> ContentScale.FillHeight
}

private class ComicArchive(
    private val zip: ZipFile,
    val entries: List<ZipEntry>,
) : Closeable {
    fun decode(index: Int): Bitmap = synchronized(zip) {
        val entry = entries[index.coerceIn(0, entries.lastIndex)]
        require(entry.size <= MAX_PAGE_BYTES || entry.size < 0) { "Imagem muito grande para abrir com segurança." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagem inválida." }
        val options = BitmapFactory.Options().apply {
            inSampleSize = comicSampleSize(bounds.outWidth, bounds.outHeight)
        }
        zip.getInputStream(entry).use { input -> BitmapFactory.decodeStream(input, null, options) }
            ?: error("Imagem inválida.")
    }

    override fun close() = zip.close()
}

private fun openArchive(
    cacheDir: File,
    book: Book,
    resolver: android.content.ContentResolver,
): ComicArchive {
    val cached = File(cacheDir, "cbz-${book.id}.cbz")
    resolver.openInputStream(book.uri.toUri())?.use { input ->
        cached.outputStream().use(input::copyTo)
    } ?: error("Não foi possível acessar este arquivo.")
    val zip = ZipFile(cached)
    return runCatching {
        val entries = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
            .toList()
            .let { byName ->
                val order = FormatTools.naturalSort(byName.map { it.name })
                val byEntryName = byName.associateBy { it.name }
                order.mapNotNull(byEntryName::get)
            }
        require(entries.size <= MAX_PAGES) { "Arquivo CBZ contém páginas demais." }
        ComicArchive(zip, entries)
    }.getOrElse {
        zip.close()
        throw it
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private const val MAX_PAGES = 10_000
private const val MAX_PAGE_BYTES = 80L * 1024 * 1024
private const val MAX_DECODE_EDGE = 4096

internal fun comicSampleSize(width: Int, height: Int, maxEdge: Int = MAX_DECODE_EDGE): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    var sample = 1
    while (maxOf(width / sample, height / sample) > maxEdge && sample <= 128) sample *= 2
    return sample
}
