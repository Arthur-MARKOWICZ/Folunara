package com.arthur.ereader.feature.organization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.arthur.ereader.data.LibraryPreferences
import com.arthur.ereader.data.OrganizationRepository
import com.arthur.ereader.data.OrganizationReviewGroup
import com.arthur.ereader.data.OrganizedBookSummary
import com.arthur.ereader.data.OrganizationSearchResult
import com.arthur.ereader.data.BookRepository
import com.arthur.ereader.domain.model.AutomationMode
import com.arthur.ereader.domain.model.BookFormat
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.PublicationType
import com.arthur.ereader.domain.model.OrganizationChildType
import com.arthur.ereader.domain.model.Series
import com.arthur.ereader.domain.model.OrganizationRule
import com.arthur.ereader.domain.model.AdvancedOrganizationRule
import com.arthur.ereader.domain.model.RuleCondition
import com.arthur.ereader.domain.model.RuleAction
import com.arthur.ereader.domain.model.RuleActionType
import com.arthur.ereader.domain.model.RuleScope
import com.arthur.ereader.domain.model.RuleField
import com.arthur.ereader.domain.model.RuleMatch
import com.arthur.ereader.domain.model.BookCollection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

data class OrganizationUiState(
    val series: List<Series> = emptyList(),
    val reviewGroups: List<OrganizationReviewGroup> = emptyList(),
    val automationMode: AutomationMode = AutomationMode.ASK,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationViewModel @Inject constructor(
    private val repository: OrganizationRepository,
    private val preferences: LibraryPreferences,
    private val books: BookRepository,
) : ViewModel() {
    val state = combine(repository.observeSeries(), repository.observeReviewGroups(), preferences.automationMode, ::OrganizationUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrganizationUiState())
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    private val query = MutableStateFlow("")
    val searchResults = query.flatMapLatest(repository::search)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules = repository.observeAdvancedRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collections = repository.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setAutomationMode(mode: AutomationMode) = viewModelScope.launch { preferences.setAutomationMode(mode) }
    fun search(value: String) { query.value = value }
    fun approve(group: OrganizationReviewGroup) = viewModelScope.launch {
        repository.approveGroup(group).fold(
            onSuccess = { _messages.emit("${group.bookIds.size} item(ns) organizados em ${group.seriesName}.") },
            onFailure = { _messages.emit(it.message ?: "Não foi possível aprovar o grupo.") },
        )
    }
    fun approveAs(group: OrganizationReviewGroup, seriesName: String) = approve(group.copy(seriesName = seriesName.trim().takeIf(String::isNotBlank)))
    fun createRule(rule: AdvancedOrganizationRule, done: () -> Unit) = viewModelScope.launch {
        repository.createAdvancedRule(rule).fold(
            onSuccess = { done(); _messages.emit("Regra criada.") },
            onFailure = { _messages.emit(it.message ?: "Não foi possível criar a regra.") },
        )
    }
    fun toggleRule(rule: AdvancedOrganizationRule) = viewModelScope.launch { repository.setRuleEnabled(rule.id, !rule.enabled) }
    fun deleteRule(rule: AdvancedOrganizationRule) = viewModelScope.launch { repository.deleteRule(rule.id) }
    fun reprocessLibrary() = viewModelScope.launch { _messages.emit(books.reprocessLibrary().message()) }
    fun createSeries(name: String, year: Int?, publisher: String?, onCreated: (Long) -> Unit) = viewModelScope.launch {
        repository.createSeries(name, year, publisher).fold(
            onSuccess = { id ->
                _messages.emit("Série criada.")
                onCreated(id)
            },
            onFailure = { _messages.emit(it.message ?: "Não foi possível criar a série.") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationScreen(
    onOpenDrawer: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenBook: (Long) -> Unit,
    vm: OrganizationViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var ruleDialog by remember { mutableStateOf(false) }
    var creatingSeries by remember { mutableStateOf(false) }
    var renameGroup by remember { mutableStateOf<OrganizationReviewGroup?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect(snackbar::showSnackbar) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Séries e organização") }, navigationIcon = { IconButton(onOpenDrawer) { Icon(Icons.Default.Menu, "Abrir menu") } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Organização automática", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutomationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.automationMode == mode,
                            onClick = { vm.setAutomationMode(mode) },
                            label = { Text(mode.label()) },
                        )
                    }
                }
                TextButton(onClick = vm::reprocessLibrary) { Icon(Icons.Default.Refresh, null); Text("Reprocessar biblioteca") }
            }
            if (state.reviewGroups.isNotEmpty()) {
                item { Text("Revisão da importação", style = MaterialTheme.typography.titleLarge) }
                items(state.reviewGroups, key = { "${it.seriesName}:${it.importItemIds.firstOrNull()}" }) { group ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, null)
                                Column {
                                    Text(group.seriesName ?: "Série não identificada", style = MaterialTheme.typography.titleMedium)
                                    Text("${group.bookIds.size} itens • confiança ${group.confidence}%")
                                    group.suggestedCollectionName?.let { Text("Coleção sugerida: $it") }
                                }
                            }
                            Button(onClick = { vm.approve(group) }, enabled = group.seriesName != null) {
                                Text(if (group.suggestedCollectionName == null) "Criar/associar série ao grupo" else "Confirmar série e coleção")
                            }
                            TextButton(onClick = { renameGroup = group }) { Icon(Icons.Default.Edit, null); Text("Alterar série") }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Regras avançadas", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { ruleDialog = true }) { Icon(Icons.Default.Add, null); Text("Nova") }
                }
                Text("Maior prioridade é executada primeiro; decisões manuais sempre prevalecem.", style = MaterialTheme.typography.bodySmall)
            }
            items(rules, key = AdvancedOrganizationRule::id) { rule ->
                ListItem(
                    headlineContent = { Text(rule.name) },
                    supportingContent = { Text("${rule.scope.label()} • prioridade ${rule.priority} • ${rule.conditions.size} condição(ões) • ${rule.actions.size} ação(ões)") },
                    leadingContent = { Switch(checked = rule.enabled, onCheckedChange = { vm.toggleRule(rule) }) },
                    trailingContent = { IconButton(onClick = { vm.deleteRule(rule) }) { Icon(Icons.Default.Delete, "Excluir regra") } },
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; vm.search(it) },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { Text("Pesquisar séries") },
                    singleLine = true,
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Séries", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { creatingSeries = true }) { Icon(Icons.Default.Add, null); Text("Nova série") }
                }
            }
            if (query.isNotBlank()) {
                if (searchResults.isEmpty()) item { Text("Nenhum resultado encontrado.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(searchResults, key = { "${it.type}:${it.id}" }) { result ->
                    ListItem(
                        modifier = Modifier.clickable {
                            when (result.type) {
                                OrganizationChildType.COLLECTION -> onOpenCollection(result.id)
                                OrganizationChildType.SERIES -> onOpenSeries(result.id)
                                OrganizationChildType.BOOK -> onOpenBook(result.id)
                            }
                        },
                        leadingContent = { Icon(Icons.Default.Folder, null) },
                        headlineContent = { Text(result.title) },
                        supportingContent = { Text(listOfNotNull(result.type.name, result.context).joinToString(" • ")) },
                    )
                    HorizontalDivider()
                }
            } else {
                if (state.series.isEmpty()) item { Text("Nenhuma série encontrada.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(state.series, key = Series::id) { item ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenSeries(item.id) },
                        leadingContent = { Icon(Icons.Default.Folder, null) },
                        headlineContent = { Text(item.displayName) },
                        supportingContent = { Text("${item.bookCount} itens") },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    if (ruleDialog) RuleFormDialog(collections, onDismiss = { ruleDialog = false }, onSave = vm::createRule)
    if (creatingSeries) {
        CreateSeriesDialog(
            onDismiss = { creatingSeries = false },
            onSave = { name, year, publisher ->
                vm.createSeries(name, year, publisher) { id ->
                    creatingSeries = false
                    onOpenSeries(id)
                }
            },
        )
    }
    renameGroup?.let { group ->
        var value by remember(group.importItemIds) { mutableStateOf(group.seriesName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameGroup = null },
            title = { Text("Alterar série detectada") },
            text = { OutlinedTextField(value, { value = it }, label = { Text("Nome da série") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.approveAs(group, value); renameGroup = null }, enabled = value.isNotBlank()) { Text("Aplicar ao grupo") } },
            dismissButton = { TextButton(onClick = { renameGroup = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CreateSeriesDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int?, String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Criar série") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text("Nome da série") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter(Char::isDigit).take(4) },
                    label = { Text("Ano (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = publisher,
                    onValueChange = { publisher = it.take(120) },
                    label = { Text("Editora (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Após criar, você poderá adicionar livros e vincular a série a coleções.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, year.toIntOrNull(), publisher.trim().takeIf(String::isNotBlank)) }, enabled = name.isNotBlank()) {
                Text("Criar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

data class SeriesDetailUiState(
    val series: Series? = null,
    val books: List<OrganizedBookSummary> = emptyList(),
    val missingNumbers: List<Int> = emptyList(),
    val allBooks: List<Book> = emptyList(),
)

@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: OrganizationRepository,
    private val books: BookRepository,
) : ViewModel() {
    private val seriesId: Long = checkNotNull(savedStateHandle["seriesId"])
    private val missing = MutableStateFlow(emptyList<Int>())
    val collections = repository.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collectionIds = repository.observeCollectionParents(OrganizationChildType.SERIES, seriesId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    val state = combine(repository.observeSeries(seriesId), repository.observeSeriesBooks(seriesId), missing, books.observe(), ::SeriesDetailUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState())
    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()
    init { viewModelScope.launch { missing.value = repository.missingNumbers(seriesId) } }
    fun toggleCollection(collectionId: Long) = viewModelScope.launch {
        if (collectionId in collectionIds.value) {
            repository.removeRelation(collectionId, OrganizationChildType.SERIES, seriesId)
        } else {
            repository.addRelation(collectionId, OrganizationChildType.SERIES, seriesId)
        }
    }
    fun reprocess() = viewModelScope.launch {
        _messages.emit(books.reprocessSeries(seriesId).message())
        missing.value = repository.missingNumbers(seriesId)
    }
    fun updateSeries(displayName: String, year: Int?, publisher: String?, done: () -> Unit) = viewModelScope.launch {
        repository.updateSeries(seriesId, displayName, year, publisher).fold(
            onSuccess = { done(); _messages.emit("Série atualizada.") },
            onFailure = { _messages.emit(it.message ?: "Não foi possível atualizar a série.") },
        )
    }
    fun removeFromSeries(bookId: Long) = viewModelScope.launch {
        repository.removeBookFromSeries(bookId)
        _messages.emit("Item removido da série; a decisão manual será preservada.")
    }
    fun addBooks(bookIds: Set<Long>, done: () -> Unit) = viewModelScope.launch {
        repository.addBooksToSeries(seriesId, bookIds).fold(
            onSuccess = {
                missing.value = repository.missingNumbers(seriesId)
                done()
                _messages.emit("${bookIds.size} livro(s) adicionado(s) manualmente à série.")
            },
            onFailure = { _messages.emit(it.message ?: "Não foi possível adicionar os livros à série.") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    vm: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val collectionIds by vm.collectionIds.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf(false) }
    var addingBooks by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf<BookFormat?>(null) }
    var type by remember { mutableStateOf<PublicationType?>(null) }
    val books = state.books.filter { (format == null || it.format == format) && (type == null || it.publicationType == type) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(
            title = { Text(state.series?.displayName ?: "Série", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = {
                IconButton(onClick = { addingBooks = true }) { Icon(Icons.Default.Add, "Adicionar livros à série") }
                IconButton(onClick = vm::reprocess) { Icon(Icons.Default.Refresh, "Reprocessar série") }
                IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Editar série") }
            },
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            if (state.missingNumbers.isNotEmpty()) item {
                Text("Possível edição ausente: ${state.missingNumbers.joinToString { "#$it" }}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (collections.isNotEmpty()) item {
                Text("Coleções", style = MaterialTheme.typography.labelLarge)
                Text("A mesma série pode pertencer a várias coleções.", style = MaterialTheme.typography.bodySmall)
                Column {
                    collections.forEach { collection ->
                        FilterChip(
                            selected = collection.id in collectionIds,
                            onClick = { vm.toggleCollection(collection.id) },
                            label = { Text(collection.name) },
                        )
                    }
                }
            }
            item {
                Text("Formato", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BookFormat.entries.forEach { option ->
                        FilterChip(selected = format == option, onClick = { format = option.takeUnless { it == format } }, label = { Text(option.name) })
                    }
                }
                Text("Tipo", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PublicationType.entries.forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option.takeUnless { it == type } }, label = { Text(option.shortLabel()) })
                    }
                }
            }
            items(books, key = OrganizedBookSummary::id) { book ->
                ListItem(
                    modifier = Modifier.clickable { onOpenBook(book.id) },
                    headlineContent = { Text(book.title) },
                    supportingContent = { Text(listOfNotNull(book.author, book.volume?.let { "Vol. ${it.pretty()}" }, book.number?.let { "#${it.pretty()}" }, book.publisher, book.isbn?.let { "ISBN $it" }, book.publicationType.shortLabel(), book.format.name).joinToString(" • ")) },
                    trailingContent = { IconButton(onClick = { vm.removeFromSeries(book.id) }) { Icon(Icons.Default.Delete, "Remover da série") } },
                )
                HorizontalDivider()
            }
        }
    }
    if (editing) {
        val series = state.series
        var displayName by remember(series?.id) { mutableStateOf(series?.displayName.orEmpty()) }
        var year by remember(series?.id) { mutableStateOf(series?.year?.toString().orEmpty()) }
        var publisher by remember(series?.id) { mutableStateOf(series?.publisher.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Editar série") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(displayName, { displayName = it }, label = { Text("Nome de exibição") }, singleLine = true)
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Ano") }, singleLine = true)
                    OutlinedTextField(publisher, { publisher = it }, label = { Text("Editora") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { vm.updateSeries(displayName, year.toIntOrNull(), publisher) { editing = false } }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } },
        )
    }
    if (addingBooks) {
        SeriesBookPickerDialog(
            seriesName = state.series?.displayName ?: "Série",
            books = state.allBooks.filterNot { candidate -> state.books.any { it.id == candidate.id } },
            onDismiss = { addingBooks = false },
            onConfirm = { selected -> vm.addBooks(selected) { addingBooks = false } },
        )
    }
}

@Composable
private fun SeriesBookPickerDialog(
    seriesName: String,
    books: List<Book>,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selected by remember(books) { mutableStateOf(emptySet<Long>()) }
    var query by remember { mutableStateOf("") }
    val filtered = books.filter {
        query.isBlank() || it.title.contains(query, ignoreCase = true) || it.author?.contains(query, ignoreCase = true) == true
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar livros à série") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(seriesName, style = MaterialTheme.typography.titleSmall)
                Text("Livros que já pertencem a outra série serão movidos. A escolha manual será preservada.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar por título ou autor") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (books.isEmpty()) {
                    Text("Todos os livros da biblioteca já pertencem a esta série.")
                } else if (filtered.isEmpty()) {
                    Text("Nenhum livro encontrado.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(filtered, key = Book::id) { book ->
                            val checked = book.id in selected
                            ListItem(
                                modifier = Modifier.clickable { selected = if (checked) selected - book.id else selected + book.id },
                                headlineContent = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(listOfNotNull(book.author, book.seriesId?.let { "Já está em outra série" }).joinToString(" • "))
                                },
                                leadingContent = { Checkbox(checked, null) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }, enabled = selected.isNotEmpty()) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun AutomationMode.label() = when (this) {
    AutomationMode.AUTOMATIC -> "Automático"
    AutomationMode.ASK -> "Perguntar"
    AutomationMode.DISABLED -> "Desativado"
}

@Composable
private fun RuleFormDialog(
    collections: List<BookCollection>,
    onDismiss: () -> Unit,
    onSave: (AdvancedOrganizationRule, () -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("0") }
    var scope by remember { mutableStateOf(RuleScope.LIBRARY) }
    var scopeValue by remember { mutableStateOf("") }
    var conditions by remember { mutableStateOf(listOf(RuleCondition(field = RuleField.SERIES, match = RuleMatch.EQUALS, value = ""))) }
    var actions by remember(collections) {
        mutableStateOf(listOf(if (collections.isEmpty()) RuleAction(type = RuleActionType.CREATE_COLLECTION, collectionName = "") else RuleAction(type = RuleActionType.ADD_TO_COLLECTION, targetCollectionId = collections.first().id)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova regra avançada") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(priority, { priority = it.filter { char -> char.isDigit() || char == '-' }.take(5) }, label = { Text("Prioridade (-1000 a 1000)") }, singleLine = true)
                Text("Escopo", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuleScope.entries.forEach { option -> FilterChip(selected = scope == option, onClick = { scope = option }, label = { Text(option.label()) }) }
                }
                if (scope == RuleScope.FOLDER) OutlinedTextField(scopeValue, { scopeValue = it }, label = { Text("Pasta ou trecho do caminho") }, singleLine = true)
                Text("Todas as condições abaixo devem corresponder", style = MaterialTheme.typography.labelLarge)
                conditions.forEachIndexed { index, condition ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RuleField.entries.forEach { option -> FilterChip(selected = condition.field == option, onClick = { conditions = conditions.updated(index, condition.copy(field = option)) }, label = { Text(option.label()) }) }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RuleMatch.entries.forEach { option -> FilterChip(selected = condition.match == option, onClick = { conditions = conditions.updated(index, condition.copy(match = option)) }, label = { Text(option.label()) }) }
                            }
                            OutlinedTextField(condition.value, { conditions = conditions.updated(index, condition.copy(value = it)) }, label = { Text("Valor") }, singleLine = true)
                            if (conditions.size > 1) TextButton(onClick = { conditions = conditions.filterIndexed { i, _ -> i != index } }) { Text("Remover condição") }
                        }
                    }
                }
                TextButton(onClick = { conditions = conditions + RuleCondition(field = RuleField.TITLE, match = RuleMatch.CONTAINS, value = "") }) { Icon(Icons.Default.Add, null); Text("Adicionar condição") }
                Text("Ações executadas em sequência", style = MaterialTheme.typography.labelLarge)
                actions.forEachIndexed { index, action ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RuleActionType.entries.forEach { option ->
                                    FilterChip(selected = action.type == option, onClick = {
                                        actions = actions.updated(index, if (option == RuleActionType.CREATE_COLLECTION) RuleAction(type = option, collectionName = "") else RuleAction(type = option, targetCollectionId = collections.firstOrNull()?.id))
                                    }, label = { Text(option.label()) })
                                }
                            }
                            if (action.type == RuleActionType.CREATE_COLLECTION) {
                                OutlinedTextField(action.collectionName.orEmpty(), { actions = actions.updated(index, action.copy(collectionName = it)) }, label = { Text("Nome da coleção") }, singleLine = true)
                            } else {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    collections.forEach { collection -> FilterChip(selected = action.targetCollectionId == collection.id, onClick = { actions = actions.updated(index, action.copy(targetCollectionId = collection.id)) }, label = { Text(collection.name) }) }
                                }
                                if (collections.isEmpty()) Text("Crie uma coleção ou escolha a ação Criar coleção.", color = MaterialTheme.colorScheme.error)
                            }
                            if (actions.size > 1) TextButton(onClick = { actions = actions.filterIndexed { i, _ -> i != index } }) { Text("Remover ação") }
                        }
                    }
                }
                TextButton(onClick = { actions = actions + if (collections.isEmpty()) RuleAction(type = RuleActionType.CREATE_COLLECTION, collectionName = "") else RuleAction(type = RuleActionType.ADD_TO_COLLECTION, targetCollectionId = collections.first().id) }) { Icon(Icons.Default.Add, null); Text("Adicionar ação") }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(AdvancedOrganizationRule(name = name, scope = scope, scopeValue = scopeValue, priority = priority.toIntOrNull() ?: 0, conditions = conditions, actions = actions), onDismiss) },
                enabled = name.isNotBlank() && conditions.all { it.value.isNotBlank() } && actions.all { it.type == RuleActionType.CREATE_COLLECTION && !it.collectionName.isNullOrBlank() || it.type != RuleActionType.CREATE_COLLECTION && it.targetCollectionId != null },
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun RuleField.label() = when (this) {
    RuleField.SERIES -> "Série"
    RuleField.PUBLISHER -> "Editora"
    RuleField.FORMAT -> "Formato"
    RuleField.CONTENT_TYPE -> "Conteúdo"
    RuleField.AUTHOR -> "Autor"
    RuleField.TITLE -> "Título"
    RuleField.ISBN -> "ISBN"
    RuleField.YEAR -> "Ano"
    RuleField.PUBLICATION_TYPE -> "Publicação"
}

private fun RuleMatch.label() = when (this) {
    RuleMatch.EQUALS -> "é igual a"
    RuleMatch.CONTAINS -> "contém"
    RuleMatch.NOT_EQUALS -> "é diferente de"
    RuleMatch.STARTS_WITH -> "começa com"
    RuleMatch.REGEX -> "corresponde à regex"
    RuleMatch.GREATER_OR_EQUAL -> "é maior ou igual"
    RuleMatch.LESS_OR_EQUAL -> "é menor ou igual"
}

private fun RuleScope.label() = when (this) {
    RuleScope.LIBRARY -> "Biblioteca"
    RuleScope.COMICS -> "HQs"
    RuleScope.MANGA -> "Mangás"
    RuleScope.EPUB -> "EPUB"
    RuleScope.PDF -> "PDF"
    RuleScope.FOLDER -> "Pasta"
    RuleScope.IMPORT -> "Importação"
}

private fun RuleActionType.label() = when (this) {
    RuleActionType.ADD_TO_COLLECTION -> "Adicionar à coleção"
    RuleActionType.REMOVE_FROM_COLLECTION -> "Remover da coleção"
    RuleActionType.CREATE_COLLECTION -> "Criar coleção"
}

private fun <T> List<T>.updated(index: Int, value: T) = mapIndexed { current, item -> if (current == index) value else item }

private fun PublicationType.shortLabel() = when (this) {
    PublicationType.NORMAL -> "Normal"
    PublicationType.ANNUAL -> "Annual"
    PublicationType.SPECIAL -> "Special"
    PublicationType.ONE_SHOT -> "One-shot"
    PublicationType.VOLUME -> "Volume"
}

private fun Double.pretty() = if (this % 1.0 == 0.0) toInt().toString() else toString()
