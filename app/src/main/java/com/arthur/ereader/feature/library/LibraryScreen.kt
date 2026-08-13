package com.arthur.ereader.feature.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.arthur.ereader.data.BookRepository
import com.arthur.ereader.data.LibraryPreferences
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.BookFormat
import com.arthur.ereader.domain.model.ContentType
import com.arthur.ereader.domain.model.LibraryBook
import com.arthur.ereader.domain.model.LibraryLayoutMode
import com.arthur.ereader.domain.model.LibrarySortMode
import com.arthur.ereader.feature.collections.BookCollectionsDialog
import com.arthur.ereader.ui.components.BookCover
import com.arthur.ereader.ui.components.UiStatePanel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

enum class LibraryFilter { TODOS, LIVROS, MANGAS, PDFS, FAVORITOS }

data class LibraryUiState(
    val items: List<LibraryBook> = emptyList(),
    val layout: LibraryLayoutMode = LibraryLayoutMode.GRID,
    val sort: LibrarySortMode = LibrarySortMode.RECENTLY_ADDED,
    val query: String = "",
    val filter: LibraryFilter = LibraryFilter.TODOS,
    val continueReading: LibraryBook? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val books: BookRepository,
    private val preferences: LibraryPreferences,
) : ViewModel() {
    private val filter = MutableStateFlow(LibraryFilter.TODOS)
    private val query = MutableStateFlow("")
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()

    val state = combine(
        books.observeWithProgress(),
        filter,
        query,
        preferences.layout,
        preferences.sort,
    ) { source, selectedFilter, search, layout, sort ->
        val filtered = source.asSequence()
            .filter { item ->
                when (selectedFilter) {
                    LibraryFilter.TODOS -> true
                    LibraryFilter.LIVROS -> item.book.contentType == ContentType.BOOK
                    LibraryFilter.MANGAS -> item.book.contentType in setOf(ContentType.MANGA, ContentType.COMIC)
                    LibraryFilter.PDFS -> item.book.format == BookFormat.PDF
                    LibraryFilter.FAVORITOS -> item.book.favorite
                }
            }
            .filter { item ->
                search.isBlank() || item.book.title.contains(search, ignoreCase = true) ||
                    item.book.author?.contains(search, ignoreCase = true) == true
            }
            .toList()
            .sortedWith(sort.comparator())
        val recent = source.filter { (it.progress?.percentage ?: 0f) in 0.001f..0.999f }
            .maxByOrNull { it.book.lastReadAt ?: it.progress?.updatedAt ?: 0L }
        LibraryUiState(filtered, layout, sort, search, selectedFilter, recent)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch { books.backfillCovers() }
    }

    fun import(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        if (!_importing.compareAndSet(expect = false, update = true)) return@launch
        try {
            _messages.emit(books.importAll(uris).message())
        } finally {
            _importing.value = false
        }
    }
    fun favorite(book: Book) = viewModelScope.launch { books.favorite(book.id, !book.favorite) }
    fun remove(book: Book) = viewModelScope.launch { books.delete(book.id) }
    fun setFilter(value: LibraryFilter) { filter.value = value }
    fun setQuery(value: String) { query.value = value }
    fun setLayout(value: LibraryLayoutMode) = viewModelScope.launch { preferences.setLayout(value) }
    fun setSort(value: LibrarySortMode) = viewModelScope.launch { preferences.setSort(value) }

    private fun LibrarySortMode.comparator(): Comparator<LibraryBook> = when (this) {
        LibrarySortMode.RECENTLY_ADDED -> compareByDescending { it.book.dateAdded }
        LibrarySortMode.LAST_READ -> compareByDescending { it.book.lastReadAt ?: 0L }
        LibrarySortMode.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.book.title }
        LibrarySortMode.AUTHOR -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.book.author ?: "" }
        LibrarySortMode.PROGRESS -> compareByDescending { it.progress?.percentage ?: 0f }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (Book) -> Unit,
    onOpenDrawer: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    var removeCandidate by remember { mutableStateOf<Book?>(null) }
    var collectionCandidate by remember { mutableStateOf<Book?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), vm::import)
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minha biblioteca", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Abrir menu")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!importing) picker.launch(arrayOf("*/*")) },
                icon = {
                    if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Add, contentDescription = null)
                },
                text = { Text(if (importing) "Importando…" else "Importar") },
                expanded = true,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LibraryControls(state, vm)
            state.continueReading?.let { item ->
                ContinueReadingCard(item, onClick = { onOpenBook(item.book) })
            }
            if (state.items.isEmpty()) {
                UiStatePanel(
                    title = if (state.query.isBlank()) "Sua estante está vazia" else "Nenhum livro encontrado",
                    message = if (state.query.isBlank()) "Importe um EPUB, PDF, CBZ ou CBR para começar." else "Tente outro termo ou filtro.",
                    actionLabel = if (state.query.isBlank()) "Importar livro" else null,
                    onAction = if (state.query.isBlank()) ({ if (!importing) picker.launch(arrayOf("*/*")) }) else null,
                    modifier = Modifier.weight(1f),
                )
            } else if (state.layout == LibraryLayoutMode.GRID) {
                LibraryGrid(
                    state.items,
                    onOpen = onOpenBook,
                    onFavorite = vm::favorite,
                    onCollections = { collectionCandidate = it },
                    onRemove = { removeCandidate = it },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LibraryList(
                    state.items,
                    onOpen = onOpenBook,
                    onFavorite = vm::favorite,
                    onCollections = { collectionCandidate = it },
                    onRemove = { removeCandidate = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    removeCandidate?.let { book ->
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            title = { Text("Remover da biblioteca?") },
            text = { Text("“${book.title}” será removido da biblioteca. O arquivo original não será apagado.") },
            confirmButton = {
                TextButton(onClick = { vm.remove(book); removeCandidate = null }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { removeCandidate = null }) { Text("Cancelar") } },
        )
    }
    collectionCandidate?.let { book ->
        BookCollectionsDialog(book = book, onDismiss = { collectionCandidate = null })
    }
}

@Composable
private fun LibraryControls(state: LibraryUiState, vm: LibraryViewModel) {
    var sortMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Buscar por título ou autor") },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                LibraryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { vm.setFilter(filter) },
                        label = { Text(filter.label()) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    LibrarySortMode.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label()) },
                            onClick = { vm.setSort(sort); sortMenu = false },
                            trailingIcon = { if (state.sort == sort) Text("✓") },
                        )
                    }
                }
            }
            IconButton(onClick = {
                vm.setLayout(if (state.layout == LibraryLayoutMode.GRID) LibraryLayoutMode.LIST else LibraryLayoutMode.GRID)
            }) {
                Icon(
                    if (state.layout == LibraryLayoutMode.GRID) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                    contentDescription = if (state.layout == LibraryLayoutMode.GRID) "Exibir lista" else "Exibir grade",
                )
            }
        }
    }
}

@Composable
private fun ContinueReadingCard(item: LibraryBook, onClick: () -> Unit) {
    val progress = item.progress?.percentage ?: 0f
    Column(Modifier.padding(top = 14.dp)) {
        Text(
            "Continue lendo",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Row(Modifier.height(142.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(item.book, Modifier.width(80.dp).fillMaxHeight())
                Column(
                    Modifier.weight(1f).padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(item.book.author ?: item.book.format.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("${(progress * 100).toInt()}% concluído", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    items: List<LibraryBook>,
    onOpen: (Book) -> Unit,
    onFavorite: (Book) -> Unit,
    onCollections: (Book) -> Unit,
    onRemove: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(items, key = { it.book.id }) { item ->
            BookGridItem(item, { onOpen(item.book) }, { onFavorite(item.book) }, { onCollections(item.book) }, { onRemove(item.book) })
        }
    }
}

@Composable
private fun BookGridItem(item: LibraryBook, onOpen: () -> Unit, onFavorite: () -> Unit, onCollections: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val progress = item.progress?.percentage ?: 0f
    Column(Modifier.clickable(onClick = onOpen)) {
        Box {
            BookCover(item.book, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            Box(Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Ações") }
                BookMenu(menu, { menu = false }, item.book.favorite, onFavorite, onCollections, onRemove)
            }
        }
        Text(
            item.book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            item.book.author ?: "Autor desconhecido",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

@Composable
private fun LibraryList(
    items: List<LibraryBook>,
    onOpen: (Book) -> Unit,
    onFavorite: (Book) -> Unit,
    onCollections: (Book) -> Unit,
    onRemove: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier, contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
        items(items, key = { it.book.id }) { item ->
            BookListItem(item, { onOpen(item.book) }, { onFavorite(item.book) }, { onCollections(item.book) }, { onRemove(item.book) })
            HorizontalDivider(Modifier.padding(start = 104.dp))
        }
    }
}

@Composable
private fun BookListItem(item: LibraryBook, onOpen: () -> Unit, onFavorite: () -> Unit, onCollections: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val progress = item.progress?.percentage ?: 0f
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(item.book, Modifier.width(72.dp).height(108.dp))
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.book.author ?: "Autor desconhecido", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                item.book.lastReadAt?.let { "Lido em ${DateFormat.getDateInstance().format(Date(it))}" } ?: "Não iniciado",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Ações") }
            BookMenu(menu, { menu = false }, item.book.favorite, onFavorite, onCollections, onRemove)
        }
    }
}

@Composable
private fun BookMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onCollections: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos") },
            leadingIcon = { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
            onClick = { onDismiss(); onFavorite() },
        )
        DropdownMenuItem(
            text = { Text("Coleções…") },
            leadingIcon = { Icon(Icons.Default.CollectionsBookmark, null) },
            onClick = { onDismiss(); onCollections() },
        )
        DropdownMenuItem(text = { Text("Remover da biblioteca") }, onClick = { onDismiss(); onRemove() })
    }
}

private fun LibraryFilter.label() = when (this) {
    LibraryFilter.TODOS -> "Todos"
    LibraryFilter.LIVROS -> "Livros"
    LibraryFilter.MANGAS -> "Mangás"
    LibraryFilter.PDFS -> "PDFs"
    LibraryFilter.FAVORITOS -> "Favoritos"
}

private fun LibrarySortMode.label() = when (this) {
    LibrarySortMode.RECENTLY_ADDED -> "Adicionados recentemente"
    LibrarySortMode.LAST_READ -> "Última leitura"
    LibrarySortMode.TITLE -> "Título"
    LibrarySortMode.AUTHOR -> "Autor"
    LibrarySortMode.PROGRESS -> "Progresso"
}
