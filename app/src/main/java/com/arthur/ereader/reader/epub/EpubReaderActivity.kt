package com.arthur.ereader.reader.epub

import android.os.Bundle
import android.os.SystemClock
import android.graphics.PointF
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.arthur.ereader.data.BookRepository
import com.arthur.ereader.domain.model.*
import com.arthur.ereader.feature.settings.BookSettingsSheet
import com.arthur.ereader.reader.common.ReaderSettingsViewModel
import com.arthur.ereader.reader.common.ReaderChromeOverlay
import com.arthur.ereader.reader.common.ReaderBookmarksDialog
import com.arthur.ereader.reader.common.rememberReaderChromeState
import com.arthur.ereader.reader.common.isDoubleTap
import com.arthur.ereader.reader.common.containsPosition
import com.arthur.ereader.ui.theme.EreaderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalReadiumApi::class)
@AndroidEntryPoint
class EpubReaderActivity : FragmentActivity() {
    @Inject lateinit var books: BookRepository
    private val settingsVm: ReaderSettingsViewModel by viewModels()
    private val containerId = View.generateViewId()
    private val containerReady = CompletableDeferred<Unit>()
    private var book by mutableStateOf<Book?>(null)
    private var progress by mutableFloatStateOf(0f)
    private var currentLocator by mutableStateOf<ReaderLocator?>(null)
    private var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
    private var error by mutableStateOf<String?>(null)
    private var navigator: EpubNavigatorFragment? = null
    private var pendingTap: PendingTap? = null
    private var pendingTapJob: Job? = null
    private var onSingleContentTap: () -> Unit = {}
    private var onDoubleContentTap: (PointF) -> Unit = {}
    private var closing = false
    private val inputListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            handleContentTap(event.point)
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getLongExtra(EXTRA_BOOK_ID, -1)
        if (id < 0) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this) { closeReader() }
        setContent {
            val global by settingsVm.globalSettings.collectAsStateWithLifecycle()
            EreaderTheme(global.appTheme) { ReaderChrome() }
        }
        lifecycleScope.launch {
            val loaded = books.get(id) ?: run {
                finish()
                return@launch
            }
            book = loaded
            launch {
                books.observeBookmarks(loaded).collect { bookmarks = it }
            }
            openPublication(books.effectiveSettings(loaded.id).epub)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ReaderChrome() {
        val currentBook = book
        val settingsFlow = remember(currentBook?.id, settingsVm) {
            currentBook?.let { settingsVm.effectiveSettings(it.id) }
        }
        val settings by (settingsFlow ?: settingsVm.globalSettings)
            .collectAsStateWithLifecycle(initialValue = GlobalReaderSettings())
        var showSettings by remember { mutableStateOf(false) }
        var showBookmarks by remember { mutableStateOf(false) }
        val chrome = rememberReaderChromeState()
        LaunchedEffect(settings.epub) {
            navigator?.submitPreferences(settings.epub.toReadiumPreferences())
        }
        SideEffect {
            onSingleContentTap = chrome::toggle
            onDoubleContentTap = { point ->
                if (settings.epub.layout == EpubLayoutMode.PAGED) {
                    navigator?.let { currentNavigator ->
                        if (point.x < currentNavigator.publicationView.width / 2f) {
                            currentNavigator.goBackward(animated = true)
                        } else {
                            currentNavigator.goForward(animated = true)
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    FragmentContainerView(context).apply {
                        id = containerId
                        if (!containerReady.isCompleted) containerReady.complete(Unit)
                    }
                },
            )
            error?.let { Text(it, Modifier.padding(24.dp)) }
            ReaderChromeOverlay(
                state = chrome,
                title = currentBook?.title ?: "Leitor EPUB",
                onBack = ::closeReader,
                onSettings = { showSettings = true },
                progress = progress,
                positionLabel = "Progresso da leitura",
                bookmarked = currentLocator?.let(bookmarks::containsPosition) == true,
                bookmarkEnabled = currentLocator != null,
                onToggleBookmark = {
                    val locator = currentLocator ?: return@ReaderChromeOverlay
                    lifecycleScope.launch {
                        books.toggleBookmark(currentBook ?: return@launch, locator, "${(progress * 100).toInt()}%")
                    }
                    chrome.show()
                },
                onShowBookmarks = { showBookmarks = true },
                bottomActions = {
                    if (settings.epub.layout == EpubLayoutMode.PAGED) {
                        TextButton(onClick = { chrome.show(); navigator?.goBackward(animated = true) }) { Text("Anterior") }
                        TextButton(onClick = { chrome.show(); navigator?.goForward(animated = true) }) { Text("Próxima") }
                    }
                },
            )
            }
        if (showSettings && currentBook != null) {
            BookSettingsSheet(currentBook, { showSettings = false }, settingsVm)
        }
        if (showBookmarks) {
            ReaderBookmarksDialog(
                bookmarks = bookmarks,
                onDismiss = { showBookmarks = false },
                onSelect = { bookmark ->
                    runCatching { Locator.fromJSON(JSONObject(bookmark.locator.payload)) }
                        .getOrNull()
                        ?.let { navigator?.go(it, animated = true) }
                    showBookmarks = false
                    chrome.show()
                },
            )
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    private suspend fun openPublication(settings: EpubReaderSettings) {
        val currentBook = book ?: return
        val file = withContext(Dispatchers.IO) {
            File(cacheDir, "epub-${currentBook.id}.epub").also { target ->
                contentResolver.openInputStream(android.net.Uri.parse(currentBook.uri))?.use { input ->
                    target.outputStream().use(input::copyTo)
                } ?: error("Não foi possível acessar este arquivo.")
            }
        }
        val asset = AssetRetriever(contentResolver, DefaultHttpClient()).retrieve(file).getOrNull()
            ?: run {
                error = "Não foi possível abrir este EPUB."
                return
            }
        val publication = PublicationOpener(EpubParser()).open(asset, allowUserInteraction = false).getOrNull()
            ?: run {
                error = "EPUB inválido ou protegido."
                return
            }
        val saved = books.progress(currentBook.id)?.locator?.payload?.let {
            runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
        }
        val factory = EpubNavigatorFactory(publication).createFragmentFactory(
            initialLocator = saved,
            initialPreferences = settings.toReadiumPreferences(),
        )
        val created = factory.instantiate(
            classLoader,
            EpubNavigatorFragment::class.java.name,
        ) as EpubNavigatorFragment
        containerReady.await()
        navigator = created
        supportFragmentManager.beginTransaction().replace(containerId, created, NAVIGATOR_TAG).commitNow()
        created.addInputListener(inputListener)
        lifecycleScope.launch {
            created.currentLocator.collect { locator ->
                val value = locator.locations.totalProgression?.toFloat()?.coerceIn(0f, 1f) ?: 0f
                progress = value
                val readerLocator = ReaderLocator(BookFormat.EPUB, locator.toJSON().toString())
                currentLocator = readerLocator
                books.saveProgress(
                    currentBook.id,
                    readerLocator,
                    value,
                )
            }
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun EpubReaderSettings.toReadiumPreferences() = EpubPreferences(
        fontSize = fontScale.toDouble(),
        lineHeight = lineHeight.toDouble(),
        pageMargins = pageMargins.toDouble(),
        theme = when (theme) {
            EpubThemeMode.LIGHT -> ReadiumTheme.LIGHT
            EpubThemeMode.SEPIA -> ReadiumTheme.SEPIA
            EpubThemeMode.DARK -> ReadiumTheme.DARK
        },
        scroll = layout == EpubLayoutMode.SCROLL,
        publisherStyles = false,
    )

    private fun handleContentTap(point: PointF) {
        val now = SystemClock.uptimeMillis()
        val previous = pendingTap
        val doubleTapSlop = ViewConfiguration.get(this).scaledDoubleTapSlop.toFloat()
        val doubleTap = previous != null && isDoubleTap(
            previousTime = previous.time,
            currentTime = now,
            deltaX = previous.point.x - point.x,
            deltaY = previous.point.y - point.y,
            timeoutMillis = ViewConfiguration.getDoubleTapTimeout().toLong(),
            slop = doubleTapSlop,
        )
        if (doubleTap) {
            pendingTapJob?.cancel()
            pendingTapJob = null
            pendingTap = null
            onDoubleContentTap(point)
        } else {
            pendingTap = PendingTap(now, PointF(point.x, point.y))
            pendingTapJob?.cancel()
            pendingTapJob = lifecycleScope.launch {
                delay(ViewConfiguration.getDoubleTapTimeout().toLong())
                pendingTap = null
                onSingleContentTap()
            }
        }
    }

    private fun closeReader() {
        if (closing) return
        closing = true
        lifecycleScope.launch {
            runCatching {
                val currentBook = book
                val locator = currentLocator
                if (currentBook != null && locator != null) {
                    books.saveProgress(currentBook.id, locator, progress)
                }
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_BOOK_ID = "book_id"
        private const val NAVIGATOR_TAG = "epub_navigator"
    }

    private data class PendingTap(val time: Long, val point: PointF)
}
