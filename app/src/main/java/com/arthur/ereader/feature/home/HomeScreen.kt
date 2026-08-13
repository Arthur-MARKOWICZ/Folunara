package com.arthur.ereader.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.arthur.ereader.data.BookRepository
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.LibraryBook
import com.arthur.ereader.ui.components.BookCover
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val reading: List<LibraryBook> = emptyList(),
    val recent: List<LibraryBook> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(private val books: BookRepository) : ViewModel() {
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    val state = books.observeWithProgress().map { source ->
        HomeUiState(
            reading = source.filter { (it.progress?.percentage ?: 0f) in 0.001f..0.999f }
                .sortedByDescending { it.book.lastReadAt ?: it.progress?.updatedAt ?: 0L }
                .take(5),
            recent = source.sortedByDescending { it.book.dateAdded }.take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun import(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        if (!_importing.compareAndSet(expect = false, update = true)) return@launch
        try {
            _messages.emit(books.importAll(uris).message())
        } finally {
            _importing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenCollections: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), vm::import)
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Início") },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Abrir menu") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!importing) picker.launch(arrayOf("*/*")) },
                icon = {
                    if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Add, null)
                },
                text = { Text(if (importing) "Importando…" else "Importar") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ElevatedCard(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) { Icon(Icons.Default.LibraryBooks, null); Text("Biblioteca", style = MaterialTheme.typography.titleMedium) }
                    }
                    ElevatedCard(onClick = onOpenCollections, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) { Icon(Icons.Default.CollectionsBookmark, null); Text("Coleções", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
            if (state.reading.isNotEmpty()) {
                item { HomeSectionHeader("Continue lendo", onOpenLibrary) }
                item { BookRow(state.reading, onOpenBook, showProgress = true) }
            }
            if (state.recent.isNotEmpty()) {
                item { HomeSectionHeader("Adicionados recentemente", onOpenLibrary) }
                item { BookRow(state.recent, onOpenBook, showProgress = false) }
            }
            if (state.reading.isEmpty() && state.recent.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sua biblioteca está vazia", style = MaterialTheme.typography.headlineSmall)
                        Text("Importe um EPUB, PDF, CBZ ou CBR para começar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onSeeAll) { Text("Ver todos") }
    }
}

@Composable
private fun BookRow(items: List<LibraryBook>, onOpenBook: (Book) -> Unit, showProgress: Boolean) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items, key = { it.book.id }) { item ->
            Column(Modifier.width(132.dp).clickable { onOpenBook(item.book) }) {
                BookCover(item.book, Modifier.fillMaxWidth().height(198.dp))
                Text(item.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                if (showProgress) {
                    val progress = item.progress?.percentage ?: 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
