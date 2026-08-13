package com.arthur.ereader.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.arthur.ereader.data.ExternalMetadataRepository
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.ExternalMetadataSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExternalMetadataUiState(
    val loading: Boolean = false,
    val results: List<ExternalMetadataSuggestion> = emptyList(),
    val message: String? = null,
    val applied: Boolean = false,
)

@HiltViewModel
class ExternalMetadataViewModel @Inject constructor(
    private val repository: ExternalMetadataRepository,
) : ViewModel() {
    val enabled = repository.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _state = MutableStateFlow(ExternalMetadataUiState())
    val state = _state.asStateFlow()

    fun reset() { _state.value = ExternalMetadataUiState() }
    fun setEnabled(value: Boolean) = viewModelScope.launch { repository.setEnabled(value) }
    fun search(book: Book) = viewModelScope.launch {
        _state.value = ExternalMetadataUiState(loading = true)
        repository.search(book.id).fold(
            onSuccess = { _state.value = ExternalMetadataUiState(results = it, message = if (it.isEmpty()) "Nenhuma sugestão encontrada." else null) },
            onFailure = { _state.value = ExternalMetadataUiState(message = it.message ?: "Falha na consulta externa.") },
        )
    }
    fun apply(book: Book, suggestion: ExternalMetadataSuggestion, includeCover: Boolean) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, message = null)
        repository.apply(book.id, suggestion, includeCover).fold(
            onSuccess = { _state.value = ExternalMetadataUiState(message = "Metadados aplicados.", applied = true) },
            onFailure = { _state.value = _state.value.copy(loading = false, message = it.message ?: "Não foi possível aplicar os metadados.") },
        )
    }
}

@Composable
fun ExternalMetadataDialog(book: Book, onDismiss: () -> Unit, vm: ExternalMetadataViewModel = hiltViewModel()) {
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(book.id) { vm.reset() }
    if (state.applied) LaunchedEffect(Unit) { onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (enabled) "Buscar metadados online" else "Autorizar consulta externa") },
        text = {
            if (!enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("A busca usa a Open Library. Ao pesquisar, título, autor e ISBN deste item serão enviados ao serviço. A biblioteca e a leitura continuam funcionando totalmente offline.")
                    Text("Nenhum arquivo, progresso de leitura ou coleção é enviado.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Consulta manual para “${book.title}”")
                    if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    if (!state.loading && state.results.isEmpty()) Button(onClick = { vm.search(book) }) { Text("Pesquisar na Open Library") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(360.dp)) {
                        items(state.results, key = ExternalMetadataSuggestion::providerId) { suggestion ->
                            SuggestionCard(suggestion) { includeCover -> vm.apply(book, suggestion, includeCover) }
                        }
                    }
                    TextButton(onClick = { vm.setEnabled(false); vm.reset() }) { Text("Desativar consultas externas") }
                }
            }
        },
        confirmButton = {
            if (!enabled) TextButton(onClick = { vm.setEnabled(true) }) { Text("Autorizar") }
            else TextButton(onClick = onDismiss) { Text("Fechar") }
        },
        dismissButton = { if (!enabled) TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun SuggestionCard(suggestion: ExternalMetadataSuggestion, onApply: (Boolean) -> Unit) {
    var includeCover by remember(suggestion.providerId) { mutableStateOf(suggestion.coverUrl != null) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            suggestion.coverUrl?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(64.dp).height(96.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(suggestion.title, style = MaterialTheme.typography.titleSmall)
                suggestion.authors.takeIf(List<String>::isNotEmpty)?.let { Text(it.joinToString(), style = MaterialTheme.typography.bodySmall) }
                Text(listOfNotNull(suggestion.publisher, suggestion.year?.toString(), suggestion.series).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                if (suggestion.coverUrl != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(includeCover, { includeCover = it })
                        Text("Salvar capa para uso offline", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = { onApply(includeCover) }) { Text("Aplicar esta sugestão") }
            }
        }
    }
}
