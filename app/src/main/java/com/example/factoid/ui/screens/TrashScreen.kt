package com.example.factoid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.factoid.data.local.entity.BoardEntity
import com.example.factoid.data.local.entity.FactoidEntity
import com.example.factoid.data.local.entity.NotebookEntity
import com.example.factoid.ui.MainViewModel
import com.example.factoid.ui.RestoreState
import com.example.factoid.ui.components.ConfirmDialog
import com.example.factoid.ui.components.paletteColorAt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val trash by viewModel.trash.collectAsState()
    val boards by viewModel.boards.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FactoidEntity?>(null) }
    val boardNames = remember(boards) { boards.associate { it.id to it.name } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Корзина",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { confirmEmpty = true },
                        enabled = trash.isNotEmpty()
                    ) {
                        Text("Очистить корзину")
                    }
                }
            )
        }
    ) { padding ->
        if (trash.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Корзина пуста",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trash, key = { it.id }) { factoid ->
                    val boardName = boardNames[factoid.boardId]
                    FactoidCard(
                        factoid = factoid,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        caption = if (boardName != null) {
                            "Доска: $boardName"
                        } else {
                            "Исходная доска удалена"
                        },
                        onClick = {},
                        actions = {
                            TextButton(onClick = { viewModel.startRestore(factoid.id) }) {
                                Text("Восстановить")
                            }
                            TextButton(onClick = { pendingDelete = factoid }) {
                                Text(
                                    text = "Удалить окончательно",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Очистить корзину?",
            text = "Все заметки из корзины будут удалены навсегда. Это действие необратимо.",
            confirmText = "Очистить",
            onConfirm = {
                viewModel.emptyTrash()
                confirmEmpty = false
            },
            onDismiss = { confirmEmpty = false }
        )
    }

    pendingDelete?.let { factoid ->
        ConfirmDialog(
            title = "Удалить окончательно?",
            text = "Заметка будет удалена навсегда. Это действие необратимо.",
            confirmText = "Удалить",
            onConfirm = {
                viewModel.deleteFactoidPermanently(factoid.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    restoreState?.let { state ->
        RestoreDialog(
            state = state,
            onSelectBoard = { viewModel.selectRestoreBoard(it) },
            onConfirm = { viewModel.confirmRestore(it) },
            onDismiss = { viewModel.cancelRestore() }
        )
    }
}

@Composable
private fun RestoreDialog(
    state: RestoreState,
    onSelectBoard: (Long) -> Unit,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val sortedNotebooks = remember(state.notebooks) {
        state.notebooks.sortedBy { it.name.lowercase() }
    }
    val originName = state.boards.firstOrNull { it.id == state.originBoardId }?.name

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Восстановить заметку") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (state.canChooseBoard) {
                    Text(
                        text = "Исходная доска удалена. Выберите доску для восстановления:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    state.boards.forEach { board ->
                        BoardChoiceRow(
                            board = board,
                            selected = board.id == state.selectedBoardId,
                            onClick = { onSelectBoard(board.id) }
                        )
                    }
                } else {
                    Text(
                        text = "Доска: ${originName.orEmpty()}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (state.selectedBoardId == null) {
                    Text(
                        text = "Сначала выберите доску",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Куда восстановить",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TargetRow(
                        label = "Хранилище",
                        color = null,
                        onClick = { onConfirm(null) }
                    )
                    sortedNotebooks.forEach { notebook: NotebookEntity ->
                        TargetRow(
                            label = notebook.name,
                            color = paletteColorAt(notebook.colorIndex),
                            onClick = { onConfirm(notebook.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun BoardChoiceRow(
    board: BoardEntity,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = board.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

