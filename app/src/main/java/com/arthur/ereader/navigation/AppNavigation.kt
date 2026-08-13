package com.arthur.ereader.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arthur.ereader.data.BookRepository
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.BookFormat
import com.arthur.ereader.feature.about.AboutScreen
import com.arthur.ereader.feature.collections.CollectionDetailScreen
import com.arthur.ereader.feature.collections.CollectionsScreen
import com.arthur.ereader.feature.home.HomeScreen
import com.arthur.ereader.feature.library.LibraryScreen
import com.arthur.ereader.feature.organization.OrganizationScreen
import com.arthur.ereader.feature.organization.SeriesDetailScreen
import com.arthur.ereader.feature.settings.SettingsScreen
import com.arthur.ereader.reader.comic.ComicReaderScreen
import com.arthur.ereader.reader.epub.EpubReaderScreen
import com.arthur.ereader.reader.pdf.PdfReaderScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

object AppRoute {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val COLLECTIONS = "collections"
    const val COLLECTION_DETAIL = "collections/{collectionId}"
    const val ORGANIZATION = "organization"
    const val SERIES_DETAIL = "series/{seriesId}"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val READER = "reader/{bookId}"

    fun collection(id: Long) = "collections/$id"
    fun series(id: Long) = "series/$id"
    fun reader(id: Long) = "reader/$id"
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = AppRoute.HOME) {
        composable(AppRoute.HOME) {
            DrawerDestination(AppRoute.HOME, navController) { openDrawer ->
                HomeScreen(
                    onOpenDrawer = openDrawer,
                    onOpenBook = { navController.navigate(AppRoute.reader(it.id)) },
                    onOpenLibrary = { navController.navigateTop(AppRoute.LIBRARY) },
                    onOpenCollections = { navController.navigateTop(AppRoute.COLLECTIONS) },
                )
            }
        }
        composable(AppRoute.LIBRARY) {
            DrawerDestination(AppRoute.LIBRARY, navController) { openDrawer ->
                LibraryScreen(
                    onOpenBook = { navController.navigate(AppRoute.reader(it.id)) },
                    onOpenDrawer = openDrawer,
                )
            }
        }
        composable(AppRoute.COLLECTIONS) {
            DrawerDestination(AppRoute.COLLECTIONS, navController) { openDrawer ->
                CollectionsScreen(
                    onOpenDrawer = openDrawer,
                    onOpenCollection = { navController.navigate(AppRoute.collection(it)) },
                )
            }
        }
        composable(AppRoute.ORGANIZATION) {
            DrawerDestination(AppRoute.ORGANIZATION, navController) { openDrawer ->
                OrganizationScreen(
                    onOpenDrawer = openDrawer,
                    onOpenSeries = { navController.navigate(AppRoute.series(it)) },
                    onOpenCollection = { navController.navigate(AppRoute.collection(it)) },
                    onOpenBook = { navController.navigate(AppRoute.reader(it)) },
                )
            }
        }
        composable(
            route = AppRoute.SERIES_DETAIL,
            arguments = listOf(navArgument("seriesId") { type = NavType.LongType }),
        ) {
            SeriesDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { navController.navigate(AppRoute.reader(it)) },
            )
        }
        composable(
            route = AppRoute.COLLECTION_DETAIL,
            arguments = listOf(navArgument("collectionId") { type = NavType.LongType }),
        ) {
            CollectionDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenBook = { navController.navigate(AppRoute.reader(it.id)) },
                onOpenSeries = { navController.navigate(AppRoute.series(it)) },
            )
        }
        composable(AppRoute.SETTINGS) {
            DrawerDestination(AppRoute.SETTINGS, navController) { openDrawer ->
                SettingsScreen(
                    onBack = { navController.popBackStack(AppRoute.HOME, inclusive = false) },
                    onOpenDrawer = openDrawer,
                )
            }
        }
        composable(AppRoute.ABOUT) {
            DrawerDestination(AppRoute.ABOUT, navController) { openDrawer -> AboutScreen(openDrawer) }
        }
        composable(
            route = AppRoute.READER,
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) {
            ReaderRoute(
                onBack = { navController.popBackStack() },
                onOpenBook = { book ->
                    navController.popBackStack()
                    navController.navigate(AppRoute.reader(book.id))
                },
            )
        }
    }
}

private data class DrawerItem(val route: String, val label: String, val icon: ImageVector)

private val drawerItems = listOf(
    DrawerItem(AppRoute.HOME, "Início", Icons.Default.Home),
    DrawerItem(AppRoute.LIBRARY, "Biblioteca", Icons.Default.LibraryBooks),
    DrawerItem(AppRoute.COLLECTIONS, "Coleções", Icons.Default.CollectionsBookmark),
    DrawerItem(AppRoute.ORGANIZATION, "Séries", Icons.Default.FolderCopy),
    DrawerItem(AppRoute.SETTINGS, "Configurações", Icons.Default.Settings),
    DrawerItem(AppRoute.ABOUT, "Sobre", Icons.Default.Info),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerDestination(
    currentRoute: String,
    navController: NavHostController,
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Text("E-reader", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(24.dp))
                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        icon = { Icon(item.icon, null) },
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigateTop(item.route)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) { content { scope.launch { drawerState.open() } } }
    BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }
}

private fun NavHostController.navigateTop(route: String) {
    if (route == AppRoute.HOME) {
        popBackStack(AppRoute.HOME, inclusive = false)
    } else {
        navigate(route) {
            popUpTo(AppRoute.HOME) { inclusive = false }
            launchSingleTop = true
        }
    }
}

data class ReaderRouteUiState(val book: Book? = null, val loaded: Boolean = false)

@HiltViewModel
class ReaderRouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val books: BookRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderRouteUiState())
    val state = _state.asStateFlow()

    init {
        val id: Long = checkNotNull(savedStateHandle["bookId"])
        viewModelScope.launch { _state.value = ReaderRouteUiState(books.get(id), loaded = true) }
    }
}

@Composable
private fun ReaderRoute(
    onBack: () -> Unit,
    onOpenBook: (Book) -> Unit,
    vm: ReaderRouteViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val book = state.book
    when {
        book != null -> when (book.format) {
            BookFormat.PDF -> PdfReaderScreen(book, onBack, onOpenBook)
            BookFormat.CBZ -> ComicReaderScreen(book, onBack)
            BookFormat.EPUB -> EpubReaderScreen(book, onBack)
        }
        state.loaded -> LaunchedEffect(Unit) { onBack() }
        else -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.padding(24.dp)) }
    }
}
