package com.example.factoid.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import com.example.factoid.ui.components.TextInputDialog
import com.example.factoid.data.local.entity.FactoidEntity
import com.example.factoid.data.local.entity.NotebookEntity
import com.example.factoid.ui.MainViewModel
import com.example.factoid.ui.components.ActionMenuDialog
import com.example.factoid.ui.components.MenuAction
import com.example.factoid.ui.components.paletteColorAt
import com.example.factoid.ui.components.readableContentColor

internal enum class FactoidStep { MENU, EDIT, MOVE }

@Stable
internal class FactoidActionsState {
    var factoid by mutableStateOf<FactoidEntity?>(null)
    var step by mutableStateOf(FactoidStep.MENU)

    fun open(value: FactoidEntity) {
        factoid = value
        step = FactoidStep.MENU
    }

    fun close() {
        factoid = null
    }
}

@Composable
internal fun rememberFactoidActionsState(): FactoidActionsState = remember { FactoidActionsState() }

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FactoidCard(
    factoid: FactoidEntity,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    scrollable: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    val innerModifier = if (scrollable) {
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { handler ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        handler()
                    }
                }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = innerModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = factoid.text,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
internal fun TargetRow(
    label: String,
    color: Color?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (color != null) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun FactoidEditDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by rememberSaveable(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать заметку") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= 200) value = it },
                label = { Text("Текст заметки") },
                minLines = 3,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
internal fun MoveTargetDialog(
    title: String,
    notebooks: List<NotebookEntity>,
    showInbox: Boolean,
    onSelectInbox: () -> Unit,
    onSelectNotebook: (NotebookEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val sorted = remember(notebooks) { notebooks.sortedBy { it.name.lowercase() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (showInbox) {
                    TargetRow(label = "Хранилище", color = null, onClick = onSelectInbox)
                }
                sorted.forEach { notebook ->
                    TargetRow(
                        label = notebook.name,
                        color = paletteColorAt(notebook.colorIndex),
                        onClick = { onSelectNotebook(notebook) }
                    )
                }
                if (!showInbox && sorted.isEmpty()) {
                    Text(
                        text = "Нет доступных блокнотов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
internal fun FactoidActionsDialogs(
    state: FactoidActionsState,
    viewModel: MainViewModel,
    notebooks: List<NotebookEntity>,
    allowMoveToInbox: Boolean
) {
    val factoid = state.factoid ?: return
    when (state.step) {
        FactoidStep.MENU -> ActionMenuDialog(
            title = "Заметка",
            actions = listOf(
                MenuAction("Редактировать") { state.step = FactoidStep.EDIT },
                MenuAction("Переместить") { state.step = FactoidStep.MOVE },
                MenuAction("Удалить", isDestructive = true) {
                    viewModel.deleteFactoid(factoid.id)
                    state.close()
                }
            ),
            onDismiss = { state.close() }
        )
        FactoidStep.EDIT -> FactoidEditDialog(
            initialText = factoid.text,
            onConfirm = {
                viewModel.editFactoid(factoid.id, it)
                state.close()
            },
            onDismiss = { state.close() }
        )
        FactoidStep.MOVE -> MoveTargetDialog(
            title = "Переместить",
            notebooks = notebooks.filter { it.id != factoid.notebookId },
            showInbox = allowMoveToInbox && factoid.notebookId != null,
            onSelectInbox = {
                viewModel.moveFactoidToInbox(factoid.id)
                state.close()
            },
            onSelectNotebook = {
                viewModel.moveFactoid(factoid.id, it.id)
                state.close()
            },
            onDismiss = { state.close() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val notebook by viewModel.selectedNotebook.collectAsState()
    val factoids by viewModel.notebookFactoids.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val state = rememberFactoidActionsState()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    val containerColor = notebook?.let { paletteColorAt(it.colorIndex) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    val contentColor = notebook?.let { readableContentColor(containerColor) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = notebook?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            if (notebook != null) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить заметку")
                }
            }
        }

    ) { padding ->
        if (factoids.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "В этом блокноте пока нет заметок",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(factoids, key = { it.id }) { factoid ->
                    FactoidCard(
                        factoid = factoid,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        modifier = Modifier.aspectRatio(1f),
                        scrollable = true,
                        onClick = {},
                        onLongClick = { state.open(factoid) }
                    )
                }
            }
        }
    }

    FactoidActionsDialogs(
        state = state,
        viewModel = viewModel,
        notebooks = notebooks,
        allowMoveToInbox = true
    )
    if (showAddDialog) {
        val currentNotebook = notebook
        if (currentNotebook != null) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Новая заметка") },
                text = {
                    var newText by rememberSaveable { mutableStateOf("") }
                    Column {
                        OutlinedTextField(
                            value = newText,
                            onValueChange = { if (it.length <= 200) newText = it },
                            label = { Text("Текст заметки") },
                            supportingText = { Text("${newText.length} / 200") },
                            minLines = 3,
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddDialog = false }) {
                                Text("Отмена")
                            }
                            TextButton(
                                onClick = {
                                    viewModel.addFactoidToNotebook(currentNotebook.id, newText)
                                    showAddDialog = false
                                },
                                enabled = newText.isNotBlank()
                            ) {
                                Text("Добавить")
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }

}

