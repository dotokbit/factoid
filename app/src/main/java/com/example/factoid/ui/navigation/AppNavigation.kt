package com.example.factoid.ui.navigation

import android.content.Intent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.factoid.ui.MainViewModel
import com.example.factoid.ui.UiEvent
import com.example.factoid.ui.screens.BoardScreen
import com.example.factoid.ui.screens.InboxScreen
import com.example.factoid.ui.screens.NotebookScreen
import com.example.factoid.ui.screens.SearchScreen
import com.example.factoid.ui.screens.SettingsScreen
import com.example.factoid.ui.screens.TrashScreen
import com.example.factoid.ui.screens.AboutScreen
import kotlinx.coroutines.launch

object Routes {
    const val NOTEBOOK_ARG = "notebookId"
    const val BOARD = "board"
    const val NOTEBOOK = "notebook/{$NOTEBOOK_ARG}"
    const val INBOX = "inbox"
    const val SEARCH = "search"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun notebook(notebookId: Long): String = "notebook/$notebookId"
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isReady by viewModel.isReady.collectAsState()
    val isBackupBusy by viewModel.isBackupBusy.collectAsState()
    val pendingImportUri by viewModel.pendingImportUri.collectAsState()

    LaunchedEffect(viewModel, navController) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    scope.launch { snackbarHostState.showSnackbar(event.text) }
                }
                is UiEvent.OpenNotebook -> {
                    navController.leaveSearchIfNeeded()
                    navController.navigate(Routes.notebook(event.notebookId)) {
                        launchSingleTop = true
                    }
                }
                is UiEvent.OpenInbox -> {
                    navController.leaveSearchIfNeeded()
                    navController.navigate(Routes.INBOX) {
                        launchSingleTop = true
                    }
                }
                is UiEvent.ShareBackup -> {
                    try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "factoid-backup.json")
                            putExtra(Intent.EXTRA_TEXT, event.json)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    } catch (e: Exception) {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        scope.launch {
                            snackbarHostState.showSnackbar("Не удалось отправить резервную копию")
                        }
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isReady) {
            NavHost(
                navController = navController,
                startDestination = Routes.BOARD,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() }
            ) {
                composable(Routes.BOARD) {
                    BoardScreen(
                        viewModel = viewModel,
                        onOpenNotebook = { id ->
                            navController.navigate(Routes.notebook(id)) { launchSingleTop = true }
                        },
                        onOpenInbox = {
                            navController.navigate(Routes.INBOX) { launchSingleTop = true }
                        },
                        onOpenSearch = {
                            viewModel.openSearch()
                            navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                        },
                        onOpenTrash = {
                            navController.navigate(Routes.TRASH) { launchSingleTop = true }
                        },
                        onOpenSettings = {
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        },
                        onOpenAbout = {
                            navController.navigate(Routes.ABOUT) { launchSingleTop = true }
                        }
                    )
                }

                composable(
                    route = Routes.NOTEBOOK,
                    arguments = listOf(
                        navArgument(Routes.NOTEBOOK_ARG) { type = NavType.LongType }
                    )
                ) { entry ->
                    val notebookId = entry.arguments?.getLong(Routes.NOTEBOOK_ARG)
                        ?: return@composable
                    val notebook by viewModel.selectedNotebook.collectAsState()
                    var wasLoaded by remember(notebookId) { mutableStateOf(false) }

                    LaunchedEffect(notebookId) {
                        viewModel.openNotebook(notebookId)
                    }
                    LaunchedEffect(notebook, notebookId) {
                        val current = notebook
                        if (current != null && current.id == notebookId) {
                            wasLoaded = true
                        } else if (current == null && wasLoaded) {
                            navController.popBackStack()
                        }
                    }
                    DisposableEffect(notebookId) {
                        onDispose { viewModel.closeNotebook() }
                    }

                    NotebookScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.INBOX) {
                    InboxScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SEARCH) {
                    DisposableEffect(Unit) {
                        onDispose { viewModel.closeSearch() }
                    }
                    SearchScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.TRASH) {
                    TrashScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                }
            }

        if (isBackupBusy) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
        )
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Импорт данных") },
            text = {
                Text(
                    "Все текущие данные (доски, группы, блокноты, заметки и корзина) " +
                            "будут полностью заменены данными из выбранного файла. " +
                            "Это действие необратимо. Если файл окажется некорректным, " +
                            "текущие данные останутся без изменений."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImport() }) {
                    Text("Заменить данные")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImport() }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun NavHostController.leaveSearchIfNeeded() {
    if (currentBackStackEntry?.destination?.route == Routes.SEARCH) {
        popBackStack()
    }
}