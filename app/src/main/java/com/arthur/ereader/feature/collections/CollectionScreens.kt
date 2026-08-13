package com.arthur.ereader.feature.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.arthur.ereader.data.BookRepository
import com.arthur.ereader.data.CollectionRepository
import com.arthur.ereader.data.LibraryPreferences
import com.arthur.ereader.data.OrganizationRepository
import com.arthur.ereader.domain.model.*
import com.arthur.ereader.ui.components.BookCover
import com.arthur.ereader.ui.components.UiStatePanel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionsUiState(val collections: List<BookCollection> = emptyList())

@HiltViewModel
class CollectionsViewModel @Inject constructor(private val repository: CollectionRepository) : ViewModel() {
    val state = repository.observeCollections()
        .map(::CollectionsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionsUiState())

    fun create(name: String, description: String, color: CollectionColor, result: (Result<Long>) -> Unit) =
        viewModelScope.launch { result(repository.create(name, description, color)) }

    fun update(item: BookCollection, name: String, description: String, color: CollectionColor, result: (Result<Unit>) -> Unit) =
        viewModelScope.launch { result(repository.update(item.id, name, description, color)) }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onOpenDrawer: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    vm: CollectionsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BookCollection?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BookCollection?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coleções") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Abrir menu") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Nova coleção") },
            )
        },
    ) { padding ->
        if (state.collections.isEmpty()) {
            UiStatePanel(
                title = "Organize sua biblioteca",
                message = "Crie coleções para reunir livros por tema, objetivo ou momento de leitura.",
                actionLabel = "Criar coleção",
                onAction = { creating = true },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.collections, key = { it.id }) { collection ->
                    CollectionCard(
                        collection,
                        onOpen = { onOpenCollection(collection.id) },
                        onEdit = { editing = collection },
                        onDelete = { deleting = collection },
                    )
                }
            }
        }
    }
    if (creating) {
        CollectionFormDialog(onDismiss = { creating = false }) { name, description, color, showError ->
            vm.create(name, description, color) { result ->
                result.onSuccess { creating = false }.onFailure { showError(it.message ?: "Não foi possível criar a coleção.") }
            }
        }
    }
    editing?.let { item ->
        CollectionFormDialog(item = item, onDismiss = { editing = null }) { name, description, color, showError ->
            vm.update(item, name, description, color) { result ->
                result.onSuccess { editing = null }.onFailure { showError(it.message ?: "Não foi possível editar a coleção.") }
            }
        }
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir coleção?") },
            text = { Text("Os livros de “${item.name}” continuarão na Biblioteca.") },
            confirmButton = { TextButton(onClick = { vm.delete(item.id); deleting = null }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CollectionCard(item: BookCollection, onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).background(item.color.displayColor(), MaterialTheme.shapes.small))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                if (item.description.isNotBlank()) {
                    Text(item.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${item.bookCount} ${if (item.bookCount == 1) "livro" else "livros"}", style = MaterialTheme.typography.labelMedium)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Ações") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

data class CollectionDetailUiState(
    val collection: BookCollection? = null,
    val books: List<LibraryBook> = emptyList(),
    val allBooks: List<LibraryBook> = emptyList(),
    val series: List<Series> = emptyList(),
    val allSeries: List<Series> = emptyList(),
    val sort: LibrarySortMode = LibrarySortMode.RECENTLY_ADDED,
)

private enum class CollectionBookAction { ADD, REMOVE }

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collections: CollectionRepository,
    private val organization: OrganizationRepository,
    private val preferences: LibraryPreferences,
    books: BookRepository,
) : ViewModel() {
    private val collectionId: Long = checkNotNull(savedStateHandle["collectionId"])
    private val seriesState = combine(
        organization.observeSeriesInCollection(collectionId),
        organization.observeSeries(),
    ) { selected, all -> selected to all }
    val state = combine(
        collections.observeCollection(collectionId),
        collections.observeBooks(collectionId),
        books.observeWithProgress(),
        preferences.sort,
        seriesState,
    ) { collection, selectedBooks, allBooks, sort, (selectedSeries, allSeries) ->
        CollectionDetailUiState(collection, selectedBooks.sortedWith(sort.comparator()), allBooks, selectedSeries, allSeries, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionDetailUiState())

    fun setSort(value: LibrarySortMode) = viewModelScope.launch { preferences.setSort(value) }
    fun addBooks(ids: Set<Long>, done: () -> Unit) = viewModelScope.launch {
        val currentIds = state.value.books.mapTo(mutableSetOf()) { it.book.id }
        collections.setBooksInCollection(collectionId, currentIds + ids)
        done()
    }

    fun removeBooks(ids: Set<Long>, done: () -> Unit) = viewModelScope.launch {
        val currentIds = state.value.books.mapTo(mutableSetOf()) { it.book.id }
        collections.setBooksInCollection(collectionId, currentIds - ids)
        done()
    }

    fun saveSeries(ids: Set<Long>, done: () -> Unit) = viewModelScope.launch {
        organization.setSeriesInCollection(collectionId, ids).onSuccess { done() }
    }

    private fun LibrarySortMode.comparator(): Comparator<LibraryBook> = when (this) {
        LibrarySortMode.RECENTLY_ADDED -> compareByDescending { it.book.dateAdded }
        LibrarySortMode.LAST_READ -> compareByDescending { it.book.lastReadAt ?: 0L }
        LibrarySortMode.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.book.title }
        LibrarySortMode.AUTHOR -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.book.author ?: "" }
        LibrarySortMode.PROGRESS -> compareByDescending { it.progress?.percentage ?: 0f }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onOpenSeries: (Long) -> Unit,
    vm: CollectionDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var bookAction by remember { mutableStateOf<CollectionBookAction?>(null) }
    var editingSeries by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    bookAction?.let { action ->
        CollectionBookPickerScreen(
            collectionName = state.collection?.name ?: "Coleção",
            books = when (action) {
                CollectionBookAction.ADD -> state.allBooks.filterNot { candidate -> state.books.any { it.book.id == candidate.book.id } }
                CollectionBookAction.REMOVE -> state.books
            },
            action = action,
            onCancel = { bookAction = null },
            onConfirm = { selected ->
                when (action) {
                    CollectionBookAction.ADD -> vm.addBooks(selected) { bookAction = null }
                    CollectionBookAction.REMOVE -> vm.removeBooks(selected) { bookAction = null }
                }
            },
        )
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.collection?.name ?: "Coleção", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            LibrarySortMode.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label()) },
                                    trailingIcon = { if (sort == state.sort) Icon(Icons.Default.Check, null) },
                                    onClick = { vm.setSort(sort); sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = { bookAction = CollectionBookAction.ADD }) {
                        Icon(Icons.Default.PlaylistAdd, "Adicionar livros")
                    }
                    IconButton(onClick = { editingSeries = true }) {
                        Icon(Icons.Default.AddLink, "Adicionar séries")
                    }
                    if (state.books.isNotEmpty()) {
                        IconButton(onClick = { bookAction = CollectionBookAction.REMOVE }) {
                            Icon(Icons.Default.PlaylistRemove, "Remover livros")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.books.isEmpty() && state.series.isEmpty()) {
            UiStatePanel(
                title = "Coleção vazia",
                message = "Adicione livros ou séries da sua Biblioteca.",
                actionLabel = "Adicionar livros",
                onAction = { bookAction = CollectionBookAction.ADD },
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (state.series.isNotEmpty()) {
                    item { Text("Séries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
                    items(state.series, key = { "series-${it.id}" }) { series ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenSeries(series.id) },
                            headlineContent = { Text(series.displayName) },
                            supportingContent = { Text("${series.bookCount} livro(s)${series.publisher?.let { " • $it" }.orEmpty()}") },
                            leadingContent = { Icon(Icons.Default.CollectionsBookmark, null) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, "Abrir série") },
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
                if (state.books.isNotEmpty()) item { Text("Livros avulsos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
                items(state.books, key = { it.book.id }) { item ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenBook(item.book) },
                        headlineContent = { Text(item.book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(item.book.author ?: item.book.format.name) },
                        leadingContent = { BookCover(item.book, Modifier.width(48.dp).height(72.dp)) },
                        trailingContent = { Text("${((item.progress?.percentage ?: 0f) * 100).toInt()}%") },
                    )
                    HorizontalDivider(Modifier.padding(start = 80.dp))
                }
            }
        }
    }
    if (editingSeries) {
        CollectionSeriesPickerDialog(
            collectionName = state.collection?.name ?: "Coleção",
            series = state.allSeries,
            selectedIds = state.series.mapTo(mutableSetOf()) { it.id },
            onDismiss = { editingSeries = false },
            onConfirm = { selected -> vm.saveSeries(selected) { editingSeries = false } },
        )
    }
}

@Composable
private fun CollectionSeriesPickerDialog(
    collectionName: String,
    series: List<Series>,
    selectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selected by remember(selectedIds) { mutableStateOf(selectedIds) }
    var query by remember { mutableStateOf("") }
    val filtered = series.filter {
        query.isBlank() || it.displayName.contains(query, ignoreCase = true) || it.publisher?.contains(query, ignoreCase = true) == true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Séries da coleção") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(collectionName, style = MaterialTheme.typography.titleSmall)
                Text("Uma série pode pertencer a várias coleções.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar série") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    series.isEmpty() -> Text("Nenhuma série foi criada ainda.")
                    filtered.isEmpty() -> Text("Nenhuma série encontrada.")
                    else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(filtered, key = Series::id) { item ->
                            val checked = item.id in selected
                            ListItem(
                                modifier = Modifier.clickable { selected = if (checked) selected - item.id else selected + item.id },
                                headlineContent = { Text(item.displayName) },
                                supportingContent = { Text("${item.bookCount} livro(s)") },
                                leadingContent = { Checkbox(checked, null) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

data class BookCollectionsUiState(
    val collections: List<BookCollection> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val loaded: Boolean = false,
)

@HiltViewModel
class BookCollectionsViewModel @Inject constructor(private val repository: CollectionRepository) : ViewModel() {
    private val bookId = MutableStateFlow<Long?>(null)
    val state = bookId.filterNotNull().flatMapLatest { id ->
        combine(repository.observeCollections(), repository.observeCollectionIdsForBook(id)) { collections, selected ->
            BookCollectionsUiState(collections, selected, loaded = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookCollectionsUiState())

    fun load(id: Long) { bookId.value = id }
    fun save(ids: Set<Long>, done: () -> Unit) = viewModelScope.launch {
        repository.setCollectionsForBook(bookId.value ?: return@launch, ids)
        done()
    }
    fun createAndAssign(name: String, description: String, color: CollectionColor, result: (Result<Long>) -> Unit) = viewModelScope.launch {
        val created = repository.create(name, description, color)
        created.onSuccess { id ->
            repository.setCollectionsForBook(bookId.value ?: return@onSuccess, state.value.selectedIds + id)
        }
        result(created)
    }
}

@Composable
fun BookCollectionsDialog(
    book: Book,
    onDismiss: () -> Unit,
    vm: BookCollectionsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selected by remember(book.id) { mutableStateOf(emptySet<Long>()) }
    var dirty by remember(book.id) { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(book.id) { vm.load(book.id) }
    LaunchedEffect(state.loaded, state.selectedIds) {
        if (state.loaded && !dirty) selected = state.selectedIds
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Coleções de “${book.title}”", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                TextButton(onClick = { creating = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Nova coleção") }
                if (state.collections.isEmpty()) Text("Nenhuma coleção criada.")
                state.collections.forEach { collection ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            dirty = true
                            selected = if (collection.id in selected) selected - collection.id else selected + collection.id
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = collection.id in selected, onCheckedChange = null)
                        Box(Modifier.size(12.dp).background(collection.color.displayColor(), MaterialTheme.shapes.small))
                        Text(collection.name, Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.save(selected, onDismiss) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    if (creating) {
        CollectionFormDialog(onDismiss = { creating = false }) { name, description, color, showError ->
            vm.createAndAssign(name, description, color) { result ->
                result.onSuccess { selected = selected + it; dirty = true; creating = false }
                    .onFailure { showError(it.message ?: "Não foi possível criar a coleção.") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionBookPickerScreen(
    collectionName: String,
    books: List<LibraryBook>,
    action: CollectionBookAction,
    onCancel: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(books, query) {
        books.filter { item ->
            query.isBlank() || item.book.title.contains(query, ignoreCase = true) ||
                item.book.author?.contains(query, ignoreCase = true) == true
        }
    }
    BackHandler(onBack = onCancel)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (action == CollectionBookAction.ADD) "Adicionar livros" else "Remover livros")
                        Text(collectionName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancelar") }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (selected.isEmpty()) "Nenhum selecionado" else "${selected.size} selecionado${if (selected.size == 1) "" else "s"}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    OutlinedButton(onClick = onCancel) { Text("Cancelar") }
                    Button(
                        onClick = { onConfirm(selected) },
                        enabled = selected.isNotEmpty(),
                        colors = if (action == CollectionBookAction.REMOVE) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            )
                        } else ButtonDefaults.buttonColors(),
                    ) {
                        Text(if (action == CollectionBookAction.ADD) "Adicionar" else "Remover")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("Buscar por título ou autor") },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            when {
                books.isEmpty() -> UiStatePanel(
                    title = if (action == CollectionBookAction.ADD) "Todos os livros já foram adicionados" else "Coleção vazia",
                    message = if (action == CollectionBookAction.ADD) {
                        "Não há outros livros disponíveis na Biblioteca para esta coleção."
                    } else {
                        "Não há livros para remover desta coleção."
                    },
                    modifier = Modifier.weight(1f),
                )
                filtered.isEmpty() -> UiStatePanel(
                    title = "Nenhum livro encontrado",
                    message = "Tente buscar por outro título ou autor.",
                    modifier = Modifier.weight(1f),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(148.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(filtered, key = { it.book.id }) { item ->
                        val isSelected = item.book.id in selected
                        Column(
                            Modifier.clickable {
                                selected = if (isSelected) selected - item.book.id else selected + item.book.id
                            },
                        ) {
                            Box {
                                BookCover(
                                    item.book,
                                    Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                                        .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier),
                                )
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                ) {
                                    Icon(
                                        if (isSelected) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = if (isSelected) "Selecionado" else "Selecionar",
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(7.dp).size(20.dp),
                                    )
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
                                item.book.author ?: item.book.format.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionFormDialog(
    item: BookCollection? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, CollectionColor, (String) -> Unit) -> Unit,
) {
    var name by remember(item?.id) { mutableStateOf(item?.name.orEmpty()) }
    var description by remember(item?.id) { mutableStateOf(item?.description.orEmpty()) }
    var color by remember(item?.id) { mutableStateOf(item?.color ?: CollectionColor.BLUE) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "Nova coleção" else "Editar coleção") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(60); error = null }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(description, { description = it.take(240) }, label = { Text("Descrição (opcional)") }, minLines = 2, maxLines = 4)
                Text("Cor", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CollectionColor.entries.forEach { option ->
                        Surface(
                            modifier = Modifier.size(if (color == option) 34.dp else 30.dp).clickable { color = option },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = option.displayColor(),
                            border = if (color == option) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                        ) {}
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, description, color) { error = it } }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun CollectionColor.displayColor(): Color = when (this) {
    CollectionColor.RED -> MaterialTheme.colorScheme.error
    CollectionColor.ORANGE -> Color(0xFFE06C31)
    CollectionColor.AMBER -> Color(0xFFD69E00)
    CollectionColor.GREEN -> Color(0xFF388E3C)
    CollectionColor.TEAL -> Color(0xFF00897B)
    CollectionColor.BLUE -> MaterialTheme.colorScheme.primary
    CollectionColor.INDIGO -> Color(0xFF5C6BC0)
    CollectionColor.PURPLE -> Color(0xFF8E5BB7)
}

private fun LibrarySortMode.label() = when (this) {
    LibrarySortMode.RECENTLY_ADDED -> "Adicionados recentemente"
    LibrarySortMode.LAST_READ -> "Última leitura"
    LibrarySortMode.TITLE -> "Título"
    LibrarySortMode.AUTHOR -> "Autor"
    LibrarySortMode.PROGRESS -> "Progresso"
}
