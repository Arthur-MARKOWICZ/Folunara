package com.arthur.ereader.reader.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arthur.ereader.domain.model.Bookmark

@Stable
class ReaderChromeState internal constructor() {
    var visible by mutableStateOf(false)
        private set

    internal var activityTick by mutableIntStateOf(0)
        private set

    fun toggle() { if (visible) hide() else show() }
    fun show() { visible = true; activityTick++ }
    fun hide() { visible = false }
}

@Composable
fun rememberReaderChromeState(): ReaderChromeState = remember { ReaderChromeState() }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.ReaderChromeOverlay(
    state: ReaderChromeState,
    title: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    progress: Float,
    positionLabel: String,
    bookmarked: Boolean = false,
    bookmarkEnabled: Boolean = false,
    onToggleBookmark: (() -> Unit)? = null,
    onShowBookmarks: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    topActions: @Composable RowScope.() -> Unit = {},
    bottomActions: @Composable RowScope.() -> Unit = {},
) {
    if (state.visible) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            },
            actions = {
                topActions()
                onToggleBookmark?.let { toggle ->
                    IconButton(onClick = toggle, enabled = bookmarkEnabled) {
                        Icon(
                            if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (bookmarked) "Remover marcador" else "Adicionar marcador",
                        )
                    }
                }
                onShowBookmarks?.let { show ->
                    IconButton(onClick = show) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "Ver marcadores")
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Configurações de leitura")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = modifier.align(Alignment.TopCenter),
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = modifier.align(Alignment.BottomCenter),
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(positionLabel, style = MaterialTheme.typography.labelLarge)
                    Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    content = bottomActions,
                )
            }
        }
    }
}

@Composable
fun ReaderBookmarksDialog(
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onSelect: (Bookmark) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marcadores") },
        text = {
            if (bookmarks.isEmpty()) {
                Text("Nenhuma posição foi marcada neste livro.")
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    bookmarks.forEach { bookmark ->
                        TextButton(
                            onClick = { onSelect(bookmark) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(bookmark.title ?: "Posição marcada", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
fun ReadingProgressBar(current: Int?, total: Int?, progress: Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (current != null && total != null) "Página $current de $total" else "Progresso da leitura")
            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%")
        }
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}
