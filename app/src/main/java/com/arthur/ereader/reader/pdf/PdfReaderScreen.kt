package com.arthur.ereader.reader.pdf

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.BookReaderOverrides
import com.arthur.ereader.domain.model.FitMode
import com.arthur.ereader.domain.model.EpubLayoutMode
import com.arthur.ereader.domain.model.GlobalReaderSettings
import com.arthur.ereader.domain.model.PdfPageMode
import com.arthur.ereader.domain.model.ReaderLocator
import com.arthur.ereader.domain.model.progressForPage
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class RenderedPdfPage(val index: Int, val total: Int, val bitmap: Bitmap)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    book: Book,
    onBack: () -> Unit,
    onOpenExportedEpub: (Book) -> Unit = {},
    vm: ReaderProgressViewModel = hiltViewModel(),
    bookmarkVm: ReaderBookmarkViewModel = hiltViewModel(),
    settingsVm: ReaderSettingsViewModel = hiltViewModel(),
    reflowVm: PdfReflowViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val saved by vm.savedLocator.collectAsStateWithLifecycle()
    val bookmarks by bookmarkVm.bookmarks.collectAsStateWithLifecycle()
    val positionLoaded by vm.positionLoaded.collectAsStateWithLifecycle()
    val effectiveFlow = remember(book.id, settingsVm) {
        settingsVm.effectiveSettings(book.id).map { it as GlobalReaderSettings? }
    }
    val overridesFlow = remember(book.id, settingsVm) {
        settingsVm.bookOverrides(book.id).map { it as BookReaderOverrides? }
    }
    val effectiveSettings by effectiveFlow.collectAsStateWithLifecycle(initialValue = null)
    val bookOverrides by overridesFlow.collectAsStateWithLifecycle(initialValue = null)
    val pdfSettings = effectiveSettings?.pdf ?: GlobalReaderSettings().pdf
    val reflowState by reflowVm.state.collectAsStateWithLifecycle()
    val exportState by reflowVm.export.collectAsStateWithLifecycle()

    var page by remember(book.id) { mutableIntStateOf(0) }
    var readingOffset by remember(book.id) { mutableIntStateOf(0) }
    var total by remember(book.id) { mutableIntStateOf(0) }
    var rendered by remember(book.id) { mutableStateOf<RenderedPdfPage?>(null) }
    var error by remember(book.id) { mutableStateOf<String?>(null) }
    var loading by remember(book.id) { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showZoomMenu by remember { mutableStateOf(false) }
    var showExportReport by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var isClosing by remember(book.id) { mutableStateOf(false) }
    var zoomScale by remember(book.id) { mutableFloatStateOf(1f) }
    var zoomLoaded by remember(book.id) { mutableStateOf(false) }
    var pan by remember(book.id) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var lastPresentation by remember(book.id) { mutableStateOf<Pair<PdfPageMode, FitMode>?>(null) }
    val chrome = rememberReaderChromeState()
    val cropCache = remember(book.id) { mutableMapOf<Pair<Int, Int>, ContentFitAnalysis>() }
    val warningHost = remember { SnackbarHostState() }
    var entryWarningShown by remember(book.id) { mutableStateOf(false) }
    val latestPage by rememberUpdatedState(page)
    val latestReadingOffset by rememberUpdatedState(readingOffset)
    val latestPageMode by rememberUpdatedState(pdfSettings.pageMode)
    val extractedDocument = (reflowState as? PdfReflowState.Ready)?.document
    val currentDiagnostics = extractedDocument?.pages?.getOrNull(page)?.diagnostics
    val renderScale = ceil(zoomScale).toInt().coerceIn(RENDER_SCALE, MAX_RENDER_SCALE)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/epub+zip"),
    ) { destination -> destination?.let(reflowVm::export) }

    fun closeReader() {
        if (isClosing) return
        isClosing = true
        if (positionLoaded && total > 0) {
            val locator = if (pdfSettings.pageMode == PdfPageMode.READING) ReaderLocator.pdfReading(page, readingOffset)
            else ReaderLocator.page(book.format, page)
            vm.saveAndThen(book, locator, progressForPage(page, total), onBack)
        } else {
            onBack()
        }
    }

    BackHandler {
        when {
            showSettings -> showSettings = false
            showBookmarks -> showBookmarks = false
            showExportReport -> showExportReport = false
            showZoomMenu -> showZoomMenu = false
            else -> closeReader()
        }
    }

    LaunchedEffect(book.id) {
        vm.load(book)
        bookmarkVm.load(book)
        reflowVm.load(book)
    }
    LaunchedEffect(book.id, pdfSettings.pageMode) {
        if (!entryWarningShown && pdfSettings.pageMode == PdfPageMode.CONTENT_FIT) {
            entryWarningShown = true
            warningHost.showSnackbar(
                "Para evitar cortes, algumas páginas podem ser exibidas sem o ajuste automático.",
            )
        }
    }
    LaunchedEffect(extractedDocument?.pages?.size, pdfSettings.pageMode) {
        if (pdfSettings.pageMode == PdfPageMode.READING && extractedDocument != null) {
            total = extractedDocument.pages.size
        }
    }
    LaunchedEffect(bookOverrides) {
        val loaded = bookOverrides ?: return@LaunchedEffect
        if (!zoomLoaded) {
            zoomScale = (loaded.pdfZoomScale ?: 1f).coerceIn(1f, 4f)
            zoomLoaded = true
        }
    }
    LaunchedEffect(effectiveSettings?.pdf) {
        val loaded = effectiveSettings?.pdf ?: return@LaunchedEffect
        val presentation = loaded.pageMode to loaded.fitMode
        if (lastPresentation != null && lastPresentation != presentation) {
            zoomScale = 1f
            zoomLoaded = true
            settingsVm.setBookPdfZoom(book.id, 1f)
            pan = Offset.Zero
        }
        lastPresentation = presentation
    }
    LaunchedEffect(saved, total, positionLoaded) {
        if (positionLoaded && total > 0) {
            saved?.pageOrNull()?.let { page = it.coerceIn(0, total - 1) }
            readingOffset = saved?.blockOrNull()?.coerceAtLeast(0) ?: 0
        }
    }

    LaunchedEffect(book.uri, page, pdfSettings.pageMode, currentDiagnostics?.contentFitSafe, renderScale) {
        if (pdfSettings.pageMode == PdfPageMode.READING) {
            loading = false
            return@LaunchedEffect
        }
        var candidate: RenderedPdfPage? = null
        loading = true
        try {
            val completed = withContext(Dispatchers.IO + NonCancellable) {
                context.contentResolver.openFileDescriptor(book.uri.toUri(), "r")?.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        require(renderer.pageCount > 0) { "PDF sem páginas." }
                        val safePage = page.coerceIn(0, renderer.pageCount - 1)
                        renderer.openPage(safePage).use { pdfPage ->
                            val bitmapSize = constrainedRenderSize(pdfPage.width, pdfPage.height, renderScale)
                            val source = Bitmap.createBitmap(
                                bitmapSize.width,
                                bitmapSize.height,
                                Bitmap.Config.ARGB_8888,
                            )
                            source.eraseColor(Color.WHITE)
                            pdfPage.render(source, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val displayed = if (
                                pdfSettings.pageMode == PdfPageMode.CONTENT_FIT &&
                                currentDiagnostics?.contentFitSafe != false
                            ) {
                                val analysis = cropCache.getOrPut(safePage to renderScale) { ContentFit.analyze(source) }
                                if (analysis.safe) {
                                    analysis.bounds.crop(source).also { cropped -> if (cropped !== source) source.recycle() }
                                } else source
                            } else {
                                source
                            }
                            RenderedPdfPage(safePage, renderer.pageCount, displayed)
                        }
                    }
                } ?: error("Não foi possível acessar este arquivo.")
            }
            candidate = completed
            currentCoroutineContext().ensureActive()
            if (completed.index == page) {
                rendered = completed
                candidate = null
                total = completed.total
                error = null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = "Não foi possível abrir o PDF: ${failure.message ?: "arquivo inválido ou protegido."}"
        } finally {
            candidate?.bitmap?.takeIf { !it.isRecycled }?.recycle()
            loading = false
        }
    }

    val currentRendered = rendered?.takeIf { it.index == page }
    val geometry = calculatePdfViewportGeometry(
        contentWidth = currentRendered?.bitmap?.width ?: 0,
        contentHeight = currentRendered?.bitmap?.height ?: 0,
        viewportWidth = viewport.width,
        viewportHeight = viewport.height,
        fitMode = pdfSettings.fitMode,
        zoomScale = zoomScale,
    )

    LaunchedEffect(currentRendered?.index, viewport, pdfSettings.fitMode, pdfSettings.pageMode) {
        if (currentRendered != null && viewport != IntSize.Zero) {
            pan = Offset(geometry.initialPanX, geometry.initialPanY)
        }
    }
    LaunchedEffect(geometry.maxPanX, geometry.maxPanY) {
        pan = Offset(geometry.clampPanX(pan.x), geometry.clampPanY(pan.y))
    }
    LaunchedEffect(page, readingOffset, total, positionLoaded, pdfSettings.pageMode) {
        if (positionLoaded && total > 0) {
            if (pdfSettings.pageMode == PdfPageMode.READING) delay(400)
            val locator = if (pdfSettings.pageMode == PdfPageMode.READING) ReaderLocator.pdfReading(page, readingOffset)
            else ReaderLocator.page(book.format, page)
            vm.save(book, locator, progressForPage(page, total))
        }
    }
    DisposableEffect(book.id, total, positionLoaded) {
        onDispose {
            if (positionLoaded && total > 0) {
                val locator = if (latestPageMode == PdfPageMode.READING) ReaderLocator.pdfReading(latestPage, latestReadingOffset)
                else ReaderLocator.page(book.format, latestPage)
                vm.save(book, locator, progressForPage(latestPage, total))
            }
        }
    }
    DisposableEffect(currentRendered?.bitmap) {
        val displayedBitmap = currentRendered?.bitmap
        onDispose { displayedBitmap?.takeIf { !it.isRecycled }?.recycle() }
    }

    fun persistZoom(value: Float) {
        val safe = ((value.coerceIn(1f, 4f) * 4f).roundToInt() / 4f).coerceIn(1f, 4f)
        zoomScale = safe
        settingsVm.setBookPdfZoom(book.id, safe)
        val updated = calculatePdfViewportGeometry(
            currentRendered?.bitmap?.width ?: 0,
            currentRendered?.bitmap?.height ?: 0,
            viewport.width,
            viewport.height,
            pdfSettings.fitMode,
            safe,
        )
        pan = Offset(updated.clampPanX(pan.x), updated.clampPanY(pan.y))
    }

    fun previous() { if (page > 0) page-- }
    fun next() { if (page < total - 1) page++ }

    val currentLocator = if (pdfSettings.pageMode == PdfPageMode.READING) {
        ReaderLocator.pdfReading(page, readingOffset)
    } else {
        ReaderLocator.page(book.format, page)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxSize()) {
            if (pdfSettings.pageMode == PdfPageMode.READING) {
                PdfReadingContent(
                    book = book,
                    state = reflowState,
                    settings = pdfSettings.reading,
                    currentPage = page,
                    currentOffset = readingOffset,
                    onPositionChanged = { newPage, newOffset ->
                        page = newPage
                        readingOffset = newOffset
                    },
                    onShowOriginal = {
                        page = it
                        settingsVm.setBookPdfPageMode(book.id, PdfPageMode.ORIGINAL)
                        settingsVm.setBookPdfZoom(book.id, 1f)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .readerTaps(
                            onPrevious = ::previous,
                            onNext = ::next,
                            onToggleControls = chrome::toggle,
                            doubleTapEnabled = { pdfSettings.reading.layout == EpubLayoutMode.PAGED },
                        ),
                )
            } else Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .onSizeChanged { viewport = it }
                    .readerGestures(
                        viewport = { viewport },
                        scale = { zoomScale },
                        onPrevious = ::previous,
                        onNext = ::next,
                        onToggleControls = chrome::toggle,
                        canTurnPage = { dragX ->
                            when {
                                geometry.maxPanX <= EDGE_TOLERANCE -> true
                                dragX < 0f -> pan.x <= -geometry.maxPanX + EDGE_TOLERANCE
                                dragX > 0f -> pan.x >= geometry.maxPanX - EDGE_TOLERANCE
                                else -> false
                            }
                        },
                        onZoomPan = { zoom, x, y ->
                            val nextZoom = (zoomScale * zoom).coerceIn(1f, 4f)
                            val nextGeometry = calculatePdfViewportGeometry(
                                currentRendered?.bitmap?.width ?: 0,
                                currentRendered?.bitmap?.height ?: 0,
                                viewport.width,
                                viewport.height,
                                pdfSettings.fitMode,
                                nextZoom,
                            )
                            zoomScale = nextZoom
                            pan = Offset(
                                nextGeometry.clampPanX(pan.x + x),
                                nextGeometry.clampPanY(pan.y + y),
                            )
                        },
                        onGestureEnd = { if (zoomLoaded) settingsVm.setBookPdfZoom(book.id, zoomScale) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    error != null -> Text(error!!, Modifier.padding(24.dp))
                    currentRendered != null -> {
                        val width = with(density) { geometry.baseWidth.toDp() }
                        val height = with(density) { geometry.baseHeight.toDp() }
                        Image(
                            bitmap = currentRendered.bitmap.asImageBitmap(),
                            contentDescription = "Página ${page + 1}",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .requiredSize(width, height)
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = pan.x,
                                    translationY = pan.y,
                                    transformOrigin = TransformOrigin.Center,
                                ),
                        )
                    }
                    loading -> CircularProgressIndicator()
                    else -> Text("Abrindo PDF…", Modifier.padding(24.dp))
                }
            }
        }
        SnackbarHost(
            hostState = warningHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
        ReaderChromeOverlay(
            state = chrome,
            title = book.title,
            onBack = ::closeReader,
            onSettings = { showSettings = true },
            progress = progressForPage(page, total),
            positionLabel = if (total > 0) "Página ${page + 1} de $total" else "Abrindo PDF",
            bookmarked = bookmarks.containsPosition(currentLocator),
            bookmarkEnabled = total > 0,
            onToggleBookmark = {
                bookmarkVm.toggle(book, currentLocator, "Página ${page + 1}")
                chrome.show()
            },
            onShowBookmarks = { showBookmarks = true },
            topActions = {
                if (reflowState is PdfReflowState.Ready) {
                    TextButton(onClick = { chrome.show(); showExportReport = true }) { Text("EPUB") }
                }
                if (pdfSettings.pageMode != PdfPageMode.READING) Box {
                    TextButton(onClick = { chrome.show(); showZoomMenu = true }) {
                        Text("${(zoomScale * 100).roundToInt()}%")
                    }
                    DropdownMenu(expanded = showZoomMenu, onDismissRequest = { showZoomMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Diminuir 25%") },
                            enabled = zoomScale > 1f,
                            onClick = { persistZoom(zoomScale - 0.25f) },
                        )
                        DropdownMenuItem(
                            text = { Text("Aumentar 25%") },
                            enabled = zoomScale < 4f,
                            onClick = { persistZoom(zoomScale + 0.25f) },
                        )
                        DropdownMenuItem(
                            text = { Text("Redefinir para 100%") },
                            onClick = {
                                persistZoom(1f)
                                val reset = calculatePdfViewportGeometry(
                                    currentRendered?.bitmap?.width ?: 0,
                                    currentRendered?.bitmap?.height ?: 0,
                                    viewport.width,
                                    viewport.height,
                                    pdfSettings.fitMode,
                                    1f,
                                )
                                pan = Offset(reset.initialPanX, reset.initialPanY)
                                showZoomMenu = false
                            },
                        )
                    }
                }
            },
            bottomActions = {
                TextButton(enabled = page > 0, onClick = { chrome.show(); previous() }) { Text("Anterior") }
                TextButton(enabled = page < total - 1, onClick = { chrome.show(); next() }) { Text("Próxima") }
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
                readingOffset = bookmark.locator.blockOrNull()?.coerceAtLeast(0) ?: 0
                if (bookmark.locator.blockOrNull() != null) {
                    settingsVm.setBookPdfPageMode(book.id, PdfPageMode.READING)
                }
                showBookmarks = false
                chrome.show()
            },
        )
    }
    if (showExportReport) {
        val report = extractedDocument?.exportReport()
        AlertDialog(
            onDismissRequest = { showExportReport = false },
            title = { Text("Prévia da exportação EPUB") },
            text = {
                Text(
                    if (report == null) "A análise ainda não terminou."
                    else "${report.reflowablePages} páginas repaginadas\n" +
                        "${report.preservedPages} preservadas como imagem\n" +
                        "${report.imageOnlyPages} sem texto\n" +
                        "${report.damagedPages} suspeitas\n" +
                        "Tamanho estimado: ${report.estimatedBytes / 1_048_576} MB" +
                        if (report.requiresConfirmation) "\n\nRevise as páginas preservadas antes de continuar." else "",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = report != null,
                    onClick = {
                        showExportReport = false
                        exportLauncher.launch("${book.title}.epub")
                    },
                ) { Text(if (report?.requiresConfirmation == true) "Confirmar e exportar" else "Exportar") }
            },
            dismissButton = { TextButton(onClick = { showExportReport = false }) { Text("Cancelar") } },
        )
    }
    when (val result = exportState) {
        PdfExportState.Exporting -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Exportando EPUB") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text("  Preservando o documento…") } },
        )
        is PdfExportState.Complete -> AlertDialog(
            onDismissRequest = reflowVm::clearExportMessage,
            confirmButton = {
                TextButton(onClick = {
                    reflowVm.clearExportMessage()
                    result.importedBook?.let(onOpenExportedEpub)
                }) { Text(if (result.importedBook != null) "Abrir EPUB" else "OK") }
            },
            title = { Text("EPUB exportado e validado") },
            text = { Text(if (result.importedBook != null) "O arquivo foi criado, validado e adicionado à biblioteca." else "O arquivo foi criado e validado sem alterar o PDF original.") },
        )
        is PdfExportState.Failure -> AlertDialog(
            onDismissRequest = reflowVm::clearExportMessage,
            confirmButton = { TextButton(onClick = reflowVm::clearExportMessage) { Text("OK") } },
            title = { Text("Falha na exportação") },
            text = { Text(result.message) },
        )
        PdfExportState.Idle -> Unit
    }
}

private const val RENDER_SCALE = 2
private const val MAX_RENDER_SCALE = 4
private const val MAX_RENDER_PIXELS = 20_000_000L
private const val EDGE_TOLERANCE = 2f

internal fun constrainedRenderSize(width: Int, height: Int, scale: Int): IntSize {
    if (width <= 0 || height <= 0) return IntSize.Zero
    val requestedWidth = width.toLong() * scale.coerceAtLeast(1)
    val requestedHeight = height.toLong() * scale.coerceAtLeast(1)
    val requestedPixels = requestedWidth * requestedHeight
    if (requestedPixels <= MAX_RENDER_PIXELS) return IntSize(requestedWidth.toInt(), requestedHeight.toInt())
    val reduction = sqrt(MAX_RENDER_PIXELS.toDouble() / requestedPixels).toFloat()
    return IntSize(
        (requestedWidth * reduction).roundToInt().coerceAtLeast(1),
        (requestedHeight * reduction).roundToInt().coerceAtLeast(1),
    )
}
