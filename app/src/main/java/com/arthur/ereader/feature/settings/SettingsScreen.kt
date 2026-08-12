package com.arthur.ereader.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arthur.ereader.domain.model.*
import com.arthur.ereader.reader.common.ReaderSettingsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    vm: ReaderSettingsViewModel = hiltViewModel(),
) {
    val settings by vm.globalSettings.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf<SettingsCategory?>(null) }
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.title ?: "Configurações") },
                navigationIcon = {
                    IconButton(onClick = { if (category == null) onOpenDrawer() else category = null }) {
                        Icon(
                            if (category == null) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (category == null) "Abrir menu" else "Voltar",
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (category == null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Personalize a aparência do aplicativo e defina como cada formato deve abrir.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(SettingsCategory.entries) { item ->
                    SettingsCategoryCard(item, onClick = { category = item })
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (category == SettingsCategory.APPEARANCE) item {
                SettingsSection("Aparência") {
                    ChoiceSetting(
                        title = "Tema do aplicativo",
                        value = settings.appTheme,
                        options = listOf(
                            AppThemeMode.SYSTEM to "Sistema",
                            AppThemeMode.LIGHT to "Claro",
                            AppThemeMode.DARK to "Escuro",
                        ),
                        onValueChange = vm::setAppTheme,
                    )
                }
            }
            if (category == SettingsCategory.EPUB) item {
                SettingsSection("EPUB", onReset = vm::resetGlobalEpub) {
                    EpubControls(
                        value = settings.epub,
                        onFont = { vm.setGlobalEpub(settings.epub.copy(fontScale = it)) },
                        onLineHeight = { vm.setGlobalEpub(settings.epub.copy(lineHeight = it)) },
                        onMargins = { vm.setGlobalEpub(settings.epub.copy(pageMargins = it)) },
                        onTheme = { vm.setGlobalEpub(settings.epub.copy(theme = it)) },
                        onLayout = { vm.setGlobalEpub(settings.epub.copy(layout = it)) },
                    )
                }
            }
            if (category == SettingsCategory.PDF) item {
                SettingsSection("PDF", onReset = vm::resetGlobalPdf) {
                    PdfControls(
                        value = settings.pdf,
                        onPageMode = { vm.setGlobalPdf(settings.pdf.copy(pageMode = it)) },
                        onFitMode = { vm.setGlobalPdf(settings.pdf.copy(fitMode = it)) },
                        onReadingFont = { vm.setGlobalPdf(settings.pdf.copy(reading = settings.pdf.reading.copy(fontScale = it))) },
                        onReadingLineHeight = { vm.setGlobalPdf(settings.pdf.copy(reading = settings.pdf.reading.copy(lineHeight = it))) },
                        onReadingMargins = { vm.setGlobalPdf(settings.pdf.copy(reading = settings.pdf.reading.copy(pageMargins = it))) },
                        onReadingTheme = { vm.setGlobalPdf(settings.pdf.copy(reading = settings.pdf.reading.copy(theme = it))) },
                        onReadingLayout = { vm.setGlobalPdf(settings.pdf.copy(reading = settings.pdf.reading.copy(layout = it))) },
                    )
                }
            }
            if (category == SettingsCategory.COMICS) item {
                SettingsSection("Quadrinhos", onReset = vm::resetGlobalComic) {
                    ComicControls(
                        value = settings.comic,
                        onDirection = { vm.setGlobalComic(settings.comic.copy(direction = it)) },
                        onDisplayMode = { vm.setGlobalComic(settings.comic.copy(displayMode = it)) },
                        onFitMode = { vm.setGlobalComic(settings.comic.copy(fitMode = it)) },
                    )
                }
            }
        }
    }
}

private enum class SettingsCategory(
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    APPEARANCE("Aparência", "Tema claro, escuro ou igual ao sistema", Icons.Default.Palette),
    EPUB("EPUB", "Texto, espaçamento, margens e paginação", Icons.AutoMirrored.Filled.MenuBook),
    PDF("PDF", "Modo de exibição, ajuste e leitura repaginada", Icons.Default.PictureAsPdf),
    COMICS("Quadrinhos", "Direção, navegação e ajuste das páginas", Icons.Default.CollectionsBookmark),
}

@Composable
private fun SettingsCategoryCard(category: SettingsCategory, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ListItem(
            headlineContent = { Text(category.title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(category.description) },
            leadingContent = {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSettingsSheet(
    book: Book,
    onDismiss: () -> Unit,
    vm: ReaderSettingsViewModel,
) {
    val global by vm.globalSettings.collectAsStateWithLifecycle()
    val effectiveFlow = remember(book.id, vm) { vm.effectiveSettings(book.id) }
    val overridesFlow = remember(book.id, vm) { vm.bookOverrides(book.id) }
    val effective by effectiveFlow.collectAsStateWithLifecycle(initialValue = global)
    val bookOverrides by overridesFlow.collectAsStateWithLifecycle(initialValue = BookReaderOverrides(book.id))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Configurações do livro", style = MaterialTheme.typography.titleLarge)
            Text(book.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Os itens marcados como padrão acompanham as configurações globais.",
                style = MaterialTheme.typography.bodySmall,
            )
            when (book.format) {
                BookFormat.EPUB -> EpubControls(
                    value = effective.epub,
                    inherited = EpubInheritance(global.epub, bookOverrides),
                    onFont = { vm.setBookEpubFont(book.id, it) },
                    onLineHeight = { vm.setBookEpubLineHeight(book.id, it) },
                    onMargins = { vm.setBookEpubMargins(book.id, it) },
                    onTheme = { vm.setBookEpubTheme(book.id, it) },
                    onLayout = { vm.setBookEpubLayout(book.id, it) },
                    onClearFont = { vm.setBookEpubFont(book.id, null) },
                    onClearLineHeight = { vm.setBookEpubLineHeight(book.id, null) },
                    onClearMargins = { vm.setBookEpubMargins(book.id, null) },
                    onClearTheme = { vm.setBookEpubTheme(book.id, null) },
                    onClearLayout = { vm.setBookEpubLayout(book.id, null) },
                )
                BookFormat.PDF -> PdfControls(
                    value = effective.pdf,
                    inherited = PdfInheritance(global.pdf, bookOverrides),
                    onPageMode = {
                        vm.setBookPdfPageMode(book.id, it)
                        vm.setBookPdfZoom(book.id, 1f)
                    },
                    onFitMode = {
                        vm.setBookPdfFitMode(book.id, it)
                        vm.setBookPdfZoom(book.id, 1f)
                    },
                    onClearPageMode = { vm.setBookPdfPageMode(book.id, null) },
                    onClearFitMode = { vm.setBookPdfFitMode(book.id, null) },
                    onReadingFont = { vm.setBookPdfReadingFont(book.id, it) },
                    onReadingLineHeight = { vm.setBookPdfReadingLineHeight(book.id, it) },
                    onReadingMargins = { vm.setBookPdfReadingMargins(book.id, it) },
                    onReadingTheme = { vm.setBookPdfReadingTheme(book.id, it) },
                    onReadingLayout = { vm.setBookPdfReadingLayout(book.id, it) },
                    onClearReadingFont = { vm.setBookPdfReadingFont(book.id, null) },
                    onClearReadingLineHeight = { vm.setBookPdfReadingLineHeight(book.id, null) },
                    onClearReadingMargins = { vm.setBookPdfReadingMargins(book.id, null) },
                    onClearReadingTheme = { vm.setBookPdfReadingTheme(book.id, null) },
                    onClearReadingLayout = { vm.setBookPdfReadingLayout(book.id, null) },
                )
                BookFormat.CBZ -> ComicControls(
                    value = effective.comic,
                    inherited = ComicInheritance(global.comic, bookOverrides),
                    onDirection = { vm.setBookComicDirection(book.id, it) },
                    onDisplayMode = { vm.setBookComicDisplayMode(book.id, it) },
                    onFitMode = { vm.setBookComicFitMode(book.id, it) },
                    onClearDirection = { vm.setBookComicDirection(book.id, null) },
                    onClearDisplayMode = { vm.setBookComicDisplayMode(book.id, null) },
                    onClearFitMode = { vm.setBookComicFitMode(book.id, null) },
                )
            }
            TextButton(
                onClick = { vm.resetBookFormat(book.id, book.format) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restaurar padrões do livro") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    onReset: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                onReset?.let { TextButton(onClick = it) { Text("Restaurar") } }
            }
            content()
        }
    }
}

private data class EpubInheritance(val global: EpubReaderSettings, val overrides: BookReaderOverrides)
private data class PdfInheritance(val global: PdfReaderSettings, val overrides: BookReaderOverrides)
private data class ComicInheritance(val global: ComicReaderSettings, val overrides: BookReaderOverrides)

@Composable
private fun EpubControls(
    value: EpubReaderSettings,
    inherited: EpubInheritance? = null,
    onFont: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onMargins: (Float) -> Unit,
    onTheme: (EpubThemeMode) -> Unit,
    onLayout: (EpubLayoutMode) -> Unit,
    onClearFont: (() -> Unit)? = null,
    onClearLineHeight: (() -> Unit)? = null,
    onClearMargins: (() -> Unit)? = null,
    onClearTheme: (() -> Unit)? = null,
    onClearLayout: (() -> Unit)? = null,
) {
    NumberSetting(
        "Tamanho da fonte",
        "${(value.fontScale * 100).toInt()}%",
        inherited?.let { it.overrides.epubFontScale == null },
        { onFont((value.fontScale - 0.1f).coerceIn(0.7f, 2f)) },
        { onFont((value.fontScale + 0.1f).coerceIn(0.7f, 2f)) },
        onClearFont,
    )
    NumberSetting(
        "Altura da linha",
        oneDecimal(value.lineHeight),
        inherited?.let { it.overrides.epubLineHeight == null },
        { onLineHeight((value.lineHeight - 0.1f).coerceIn(1f, 2f)) },
        { onLineHeight((value.lineHeight + 0.1f).coerceIn(1f, 2f)) },
        onClearLineHeight,
    )
    NumberSetting(
        "Margens",
        oneDecimal(value.pageMargins),
        inherited?.let { it.overrides.epubPageMargins == null },
        { onMargins((value.pageMargins - 0.25f).coerceIn(0f, 4f)) },
        { onMargins((value.pageMargins + 0.25f).coerceIn(0f, 4f)) },
        onClearMargins,
    )
    ChoiceSetting(
        "Tema da página",
        value.theme,
        listOf(EpubThemeMode.LIGHT to "Claro", EpubThemeMode.SEPIA to "Sépia", EpubThemeMode.DARK to "Escuro"),
        onTheme,
        inherited?.let { it.overrides.epubTheme == null },
        onClearTheme,
    )
    ChoiceSetting(
        "Modo de leitura",
        value.layout,
        listOf(EpubLayoutMode.PAGED to "Paginado", EpubLayoutMode.SCROLL to "Rolagem"),
        onLayout,
        inherited?.let { it.overrides.epubLayout == null },
        onClearLayout,
    )
}

@Composable
private fun PdfControls(
    value: PdfReaderSettings,
    inherited: PdfInheritance? = null,
    onPageMode: (PdfPageMode) -> Unit,
    onFitMode: (FitMode) -> Unit,
    onReadingFont: (Float) -> Unit,
    onReadingLineHeight: (Float) -> Unit,
    onReadingMargins: (Float) -> Unit,
    onReadingTheme: (EpubThemeMode) -> Unit,
    onReadingLayout: (EpubLayoutMode) -> Unit,
    onClearPageMode: (() -> Unit)? = null,
    onClearFitMode: (() -> Unit)? = null,
    onClearReadingFont: (() -> Unit)? = null,
    onClearReadingLineHeight: (() -> Unit)? = null,
    onClearReadingMargins: (() -> Unit)? = null,
    onClearReadingTheme: (() -> Unit)? = null,
    onClearReadingLayout: (() -> Unit)? = null,
) {
    Text(
        "PDF preserva a tipografia original. Use Ajustar e o controle de ampliação no leitor para aumentar texto e página.",
        style = MaterialTheme.typography.bodySmall,
    )
    ChoiceSetting(
        "Exibição",
        value.pageMode,
        listOf(
            PdfPageMode.ORIGINAL to "Original",
            PdfPageMode.CONTENT_FIT to "Content Fit",
            PdfPageMode.READING to "Repaginado (experimental)",
        ),
        onPageMode,
        inherited?.let { it.overrides.pdfPageMode == null },
        onClearPageMode,
    )
    ChoiceSetting(
        "Ajustar",
        value.fitMode,
        fitOptions,
        onFitMode,
        inherited?.let { it.overrides.pdfFitMode == null },
        onClearFitMode,
    )
    if (value.pageMode == PdfPageMode.READING) {
        Text(
            "Somente páginas de prosa com alta confiança são repaginadas. Tabelas, fórmulas, colunas e imagens permanecem integrais.",
            style = MaterialTheme.typography.bodySmall,
        )
        NumberSetting(
            "Tamanho do texto repaginado",
            "${(value.reading.fontScale * 100).toInt()}%",
            inherited?.let { it.overrides.pdfReadingFontScale == null },
            { onReadingFont((value.reading.fontScale - 0.1f).coerceIn(0.7f, 2f)) },
            { onReadingFont((value.reading.fontScale + 0.1f).coerceIn(0.7f, 2f)) },
            onClearReadingFont,
        )
        NumberSetting(
            "Altura da linha repaginada",
            oneDecimal(value.reading.lineHeight),
            inherited?.let { it.overrides.pdfReadingLineHeight == null },
            { onReadingLineHeight((value.reading.lineHeight - 0.1f).coerceIn(1f, 2f)) },
            { onReadingLineHeight((value.reading.lineHeight + 0.1f).coerceIn(1f, 2f)) },
            onClearReadingLineHeight,
        )
        NumberSetting(
            "Margens repaginadas",
            oneDecimal(value.reading.pageMargins),
            inherited?.let { it.overrides.pdfReadingPageMargins == null },
            { onReadingMargins((value.reading.pageMargins - 0.25f).coerceIn(0f, 4f)) },
            { onReadingMargins((value.reading.pageMargins + 0.25f).coerceIn(0f, 4f)) },
            onClearReadingMargins,
        )
        ChoiceSetting(
            "Tema repaginado",
            value.reading.theme,
            listOf(EpubThemeMode.LIGHT to "Claro", EpubThemeMode.SEPIA to "Sépia", EpubThemeMode.DARK to "Escuro"),
            onReadingTheme,
            inherited?.let { it.overrides.pdfReadingTheme == null },
            onClearReadingTheme,
        )
        ChoiceSetting(
            "Navegação repaginada",
            value.reading.layout,
            listOf(EpubLayoutMode.SCROLL to "Rolagem", EpubLayoutMode.PAGED to "Paginada"),
            onReadingLayout,
            inherited?.let { it.overrides.pdfReadingLayout == null },
            onClearReadingLayout,
        )
    }
}

@Composable
private fun ComicControls(
    value: ComicReaderSettings,
    inherited: ComicInheritance? = null,
    onDirection: (ReadingDirection) -> Unit,
    onDisplayMode: (ComicDisplayMode) -> Unit,
    onFitMode: (FitMode) -> Unit,
    onClearDirection: (() -> Unit)? = null,
    onClearDisplayMode: (() -> Unit)? = null,
    onClearFitMode: (() -> Unit)? = null,
) {
    ChoiceSetting(
        "Direção",
        value.direction,
        listOf(ReadingDirection.LTR to "LTR", ReadingDirection.RTL to "RTL"),
        onDirection,
        inherited?.let { it.overrides.comicDirection == null },
        onClearDirection,
    )
    ChoiceSetting(
        "Navegação",
        value.displayMode,
        listOf(ComicDisplayMode.PAGED to "Paginada", ComicDisplayMode.VERTICAL to "Vertical"),
        onDisplayMode,
        inherited?.let { it.overrides.comicDisplayMode == null },
        onClearDisplayMode,
    )
    ChoiceSetting(
        "Ajustar",
        value.fitMode,
        fitOptions,
        onFitMode,
        inherited?.let { it.overrides.comicFitMode == null },
        onClearFitMode,
    )
}

private val fitOptions = listOf(
    FitMode.PAGE to "Página",
    FitMode.WIDTH to "Largura",
    FitMode.HEIGHT to "Altura",
)

@Composable
private fun NumberSetting(
    title: String,
    value: String,
    inherited: Boolean? = null,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onUseDefault: (() -> Unit)? = null,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(title)
                if (inherited == true) Text("Padrão global", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = onDecrease) { Text("−") }
                Text(value, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
                TextButton(onClick = onIncrease) { Text("+") }
            }
        }
        if (inherited == false && onUseDefault != null) {
            TextButton(onClick = onUseDefault) { Text("Usar padrão") }
        }
    }
}

@Composable
private fun <T> ChoiceSetting(
    title: String,
    value: T,
    options: List<Pair<T, String>>,
    onValueChange: (T) -> Unit,
    inherited: Boolean? = null,
    onUseDefault: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title)
        if (inherited == true) Text("Padrão global", style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (option, label) ->
                FilterChip(
                    selected = value == option,
                    onClick = { onValueChange(option) },
                    label = { Text(label) },
                )
            }
        }
        if (inherited == false && onUseDefault != null) {
            TextButton(onClick = onUseDefault) { Text("Usar padrão") }
        }
    }
}

private fun oneDecimal(value: Float) = String.format(Locale.getDefault(), "%.1f", value)
