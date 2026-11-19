package com.example.factoid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.factoid.data.local.entity.GroupEntity
import com.example.factoid.data.local.entity.NotebookEntity
import com.example.factoid.data.repository.BoardContent
import com.example.factoid.data.repository.GroupWithNotebooks
import com.example.factoid.data.repository.SortMode
import com.example.factoid.ui.MainViewModel
import com.example.factoid.ui.components.ActionMenuDialog
import com.example.factoid.ui.components.ColorPickerDialog
import com.example.factoid.ui.components.ConfirmDialog
import com.example.factoid.ui.components.GroupPickerDialog
import com.example.factoid.ui.components.MenuAction
import com.example.factoid.ui.components.QuickAddSheet
import com.example.factoid.ui.components.TextInputDialog
import com.example.factoid.ui.components.paletteColorAt
import com.example.factoid.ui.components.readableContentColor
import kotlinx.coroutines.launch

private sealed interface BoardDialog {
    data object CreateGroup : BoardDialog
    data class GroupMenu(val group: GroupEntity) : BoardDialog
    data class RenameGroup(val group: GroupEntity) : BoardDialog
    data class GroupColor(val group: GroupEntity) : BoardDialog
    data class DeleteGroup(val group: GroupEntity) : BoardDialog
    data class NotebookMenu(val notebook: NotebookEntity) : BoardDialog
    data class RenameNotebook(val notebook: NotebookEntity) : BoardDialog
    data class NotebookColor(val notebook: NotebookEntity) : BoardDialog
    data class NotebookGroup(val notebook: NotebookEntity) : BoardDialog
    data class NewGroupForNotebook(val notebook: NotebookEntity) : BoardDialog
    data class DeleteNotebook(val notebook: NotebookEntity) : BoardDialog
    data object CreateBoard : BoardDialog
    data object RenameBoard : BoardDialog
    data object DeleteBoard : BoardDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    viewModel: MainViewModel,
    onOpenNotebook: (Long) -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val boards by viewModel.boards.collectAsState()
    val activeBoard by viewModel.activeBoard.collectAsState()
    val content by viewModel.boardContent.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val notebooks by viewModel.notebooks.collectAsState()
    val counts by viewModel.notebookFactoidCounts.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val quickAddVisible by viewModel.quickAddVisible.collectAsState()
    val trashCount by viewModel.trashCount.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var dialog by remember { mutableStateOf<BoardDialog?>(null) }

    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    DrawerHeader("Доски")
                    boards.forEach { board ->
                        DrawerItem(
                            label = board.name,
                            selected = board.id == activeBoard?.id,
                            onClick = {
                                closeDrawer()
                                viewModel.switchBoard(board.id)
                            }
                        )
                    }
                    DrawerItem(
                        label = "Создать доску",
                        onClick = {
                            closeDrawer()
                            dialog = BoardDialog.CreateBoard
                        }
                    )
                    if (activeBoard != null) {
                        DrawerItem(
                            label = "Переименовать доску",
                            onClick = {
                                closeDrawer()
                                dialog = BoardDialog.RenameBoard
                            }
                        )
                        DrawerItem(
                            label = "Удалить доску",
                            onClick = {
                                closeDrawer()
                                dialog = BoardDialog.DeleteBoard
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    DrawerHeader("Сортировка")
                    DrawerItem(
                        label = "По алфавиту",
                        selected = sortMode == SortMode.ALPHABETICAL,
                        onClick = { viewModel.setSortMode(SortMode.ALPHABETICAL) }
                    )
                    DrawerItem(
                        label = "По дате создания",
                        selected = sortMode == SortMode.CREATED_AT,
                        onClick = { viewModel.setSortMode(SortMode.CREATED_AT) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                    DrawerItem(
                        label = "Создать группу",
                        onClick = {
                            closeDrawer()
                            dialog = BoardDialog.CreateGroup
                        }
                    )
                    DrawerItem(
                        label = "Хранилище",
                        onClick = {
                            closeDrawer()
                            onOpenInbox()
                        }
                    )
                    DrawerItem(
                        label = "Корзина",
                        badge = if (trashCount > 0) trashCount.toString() else null,
                        onClick = {
                            closeDrawer()
                            onOpenTrash()
                        }
                    )
                    DrawerItem(
                        label = "Настройки",
                        onClick = {
                            closeDrawer()
                            onOpenSettings()
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = activeBoard?.name ?: "Factoid",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Меню")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Default.Search, contentDescription = "Поиск")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { viewModel.showQuickAdd() }) {
                    Icon(Icons.Default.Add, contentDescription = "Быстрый ввод")
                }
            }
        ) { padding ->
            BoardCanvas(
                content = content,
                counts = counts,
                onOpenNotebook = onOpenNotebook,
                onNotebookLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dialog = BoardDialog.NotebookMenu(it)
                },
                onGroupLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dialog = BoardDialog.GroupMenu(it)
                },
                onEmptyLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dialog = BoardDialog.CreateGroup
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }

    if (quickAddVisible) {
        QuickAddSheet(
            notebooks = notebooks,
            onConfirm = { text, destination -> viewModel.quickAdd(text, destination) },
            onDismiss = { viewModel.dismissQuickAdd() }
        )
    }

    val dismiss: () -> Unit = { dialog = null }

    when (val current = dialog) {
        null -> Unit
        BoardDialog.CreateGroup -> TextInputDialog(
            title = "Новая группа",
            label = "Название группы",
            confirmText = "Создать",
            onConfirm = {
                viewModel.createGroup(it)
                dismiss()
            },
            onDismiss = dismiss
        )
        is BoardDialog.GroupMenu -> ActionMenuDialog(
            title = current.group.name,
            actions = listOf(
                MenuAction("Переименовать") { dialog = BoardDialog.RenameGroup(current.group) },
                MenuAction("Изменить цвет") { dialog = BoardDialog.GroupColor(current.group) },
                MenuAction("Удалить", isDestructive = true) {
                    dialog = BoardDialog.DeleteGroup(current.group)
                }
            ),
            onDismiss = dismiss
        )
        is BoardDialog.RenameGroup -> TextInputDialog(
            title = "Переименовать группу",
            label = "Название группы",
            initialValue = current.group.name,
            onConfirm = {
                viewModel.renameGroup(current.group.id, it)
                dismiss()
            },
            onDismiss = dismiss
        )
        is BoardDialog.GroupColor -> ColorPickerDialog(
            title = "Цвет группы",
            selectedIndex = current.group.colorIndex,
            onSelect = {
                viewModel.changeGroupColor(current.group.id, it)
                dismiss()
            },
            onDismiss = dismiss
        )
        is BoardDialog.DeleteGroup -> ConfirmDialog(
            title = "Удалить группу?",
            text = "Будет удалена только рамка группы «${current.group.name}». " +
                    "Блокноты останутся на доске без группы.",
            confirmText = "Удалить",
            onConfirm = {
                viewModel.deleteGroup(current.group.id)
                dismiss()
            },
            onDismiss = dismiss
        )
        is BoardDialog.NotebookMenu -> {
            val notebook = current.notebook
            val actions = buildList {
                add(MenuAction("Переименовать") { dialog = BoardDialog.RenameNotebook(notebook) })
                add(MenuAction("Изменить цвет") { dialog = BoardDialog.NotebookColor(notebook) })
                add(MenuAction("Добавить в группу") { dialog = BoardDialog.NotebookGroup(notebook) })
                if (notebook.groupId != null) {
                    add(
                        MenuAction("Убрать из группы") {
                            viewModel.setNotebookGroup(notebook.id, null)
                            dismiss()
                        }
                    )
                }
                add(
                    MenuAction("Удалить", isDestructive = true) {
                        dialog = BoardDialog.DeleteNotebook(notebook)
                    }
                )
            }
            ActionMenuDialog(
                title = notebook.name,
                actions = actions,
                onDismiss = dismiss
            )
        }
        is BoardDialog.RenameNotebook -> TextInputDialog(
            title = "Переименовать блокнот",
            label = "Название блокнота",
            initialValue = current.notebook.name,
            onConfirm = {
                viewModel.renameNotebook(current.notebook.id, it)
                dismiss()
            },
            onDismiss = dismiss
        )
        is BoardDialog.NotebookColor -> ColorPickerDialog(
            title = "Цвет блокнота",
            selectedIndex = current.notebook.colorIndex,
            onSelect = {
                viewModel.changeNotebookColor(current.notebook.id, it)
                dismiss()
            },
            onDismiss = dismiss
        )
        is BoardDialog.NotebookGroup -> GroupPickerDialog(
            groups = groups,
            currentGroupId = current.notebook.groupId,
            onSelect = {
                viewModel.setNotebookGroup(current.notebook.id, it)
                dismiss()
            },
            onCreateNew = { dialog = BoardDialog.NewGroupForNotebook(current.notebook) },
            onDismiss = dismiss
        )
        is BoardDialog.NewGroupForNotebook -> TextInputDialog(
            title = "Новая группа",
            label = "Название группы",
            confirmText = "Создать",
            onConfirm = {
                viewModel.createGroupForNotebook(current.notebook.id, it)
                dismiss()
            },
            onDismiss = dismiss
        )
        is BoardDialog.DeleteNotebook -> ConfirmDialog(
            title = "Удалить блокнот?",
            text = "Блокнот «${current.notebook.name}» будет удалён, а все его заметки " +
                    "переместятся в Хранилище этой доски.",
            confirmText = "Удалить",
            onConfirm = {
                viewModel.deleteNotebook(current.notebook.id)
                dismiss()
            },
            onDismiss = dismiss
        )
        BoardDialog.CreateBoard -> TextInputDialog(
            title = "Новая доска",
            label = "Название доски",
            confirmText = "Создать",
            onConfirm = {
                viewModel.createBoard(it)
                dismiss()
            },
            onDismiss = dismiss
        )
        BoardDialog.RenameBoard -> activeBoard?.let { board ->
            TextInputDialog(
                title = "Переименовать доску",
                label = "Название доски",
                initialValue = board.name,
                onConfirm = {
                    viewModel.renameBoard(board.id, it)
                    dismiss()
                },
                onDismiss = dismiss
            )
        }
        BoardDialog.DeleteBoard -> activeBoard?.let { board ->
            ConfirmDialog(
                title = "Удалить доску?",
                text = "Доска «${board.name}» будет удалена безвозвратно вместе со всеми её " +
                        "группами, блокнотами и заметками, включая Хранилище и Корзину этой доски.",
                confirmText = "Удалить",
                onConfirm = {
                    viewModel.deleteBoard(board.id)
                    dismiss()
                },
                onDismiss = dismiss
            )
        }
    }
}

@Composable
private fun DrawerHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)
    )
}

@Composable
private fun DrawerItem(
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    badge: String? = null
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        selected = selected,
        onClick = onClick,
        badge = badge?.let { value -> { Text(value) } },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardCanvas(
    content: BoardContent,
    counts: Map<Long, Int>,
    onOpenNotebook: (Long) -> Unit,
    onNotebookLongPress: (NotebookEntity) -> Unit,
    onGroupLongPress: (GroupEntity) -> Unit,
    onEmptyLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = ((maxWidth.value - 56f) / 110f).toInt().coerceIn(2, 6)
        val isEmpty = content.groups.isEmpty() && content.ungroupedNotebooks.isEmpty()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onEmptyLongPress() })
                }
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isEmpty) {
                Text(
                    text = "Здесь пока пусто. Нажмите «+», чтобы добавить заметку, " +
                            "или удерживайте пустое место, чтобы создать группу.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp)
                )
            }

            content.groups.forEach { item ->
                GroupFrame(
                    item = item,
                    columns = columns,
                    counts = counts,
                    onOpenNotebook = onOpenNotebook,
                    onNotebookLongPress = onNotebookLongPress,
                    onGroupLongPress = onGroupLongPress
                )
            }

            if (content.ungroupedNotebooks.isNotEmpty()) {
                NotebookGrid(
                    notebooks = content.ungroupedNotebooks,
                    columns = columns,
                    counts = counts,
                    onOpenNotebook = onOpenNotebook,
                    onNotebookLongPress = onNotebookLongPress
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupFrame(
    item: GroupWithNotebooks,
    columns: Int,
    counts: Map<Long, Int>,
    onOpenNotebook: (Long) -> Unit,
    onNotebookLongPress: (NotebookEntity) -> Unit,
    onGroupLongPress: (GroupEntity) -> Unit
) {
    val group = item.group
    val color = paletteColorAt(group.colorIndex)
    val shape = RoundedCornerShape(20.dp)
    val sizeModifier = if (item.notebooks.isEmpty()) {
        Modifier.widthIn(min = 160.dp)
    } else {
        Modifier.fillMaxWidth()
    }

    Column(
        modifier = sizeModifier
            .clip(shape)
            .background(color.copy(alpha = 0.18f), shape)
            .border(2.dp, color, shape)
            .combinedClickable(
                onClick = {},
                onLongClick = { onGroupLongPress(group) }
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = group.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.notebooks.isNotEmpty()) {
            NotebookGrid(
                notebooks = item.notebooks,
                columns = columns,
                counts = counts,
                onOpenNotebook = onOpenNotebook,
                onNotebookLongPress = onNotebookLongPress
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookGrid(
    notebooks: List<NotebookEntity>,
    columns: Int,
    counts: Map<Long, Int>,
    onOpenNotebook: (Long) -> Unit,
    onNotebookLongPress: (NotebookEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        notebooks.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { notebook ->
                    NotebookTile(
                        notebook = notebook,
                        count = counts[notebook.id] ?: 0,
                        onClick = { onOpenNotebook(notebook.id) },
                        onLongClick = { onNotebookLongPress(notebook) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotebookTile(
    notebook: NotebookEntity,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = paletteColorAt(notebook.colorIndex)
    val textColor = readableContentColor(background)
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "Блокнот ${notebook.name}, заметок: $count" }
            .padding(10.dp)
    ) {
        Text(
            text = notebook.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = textColor.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}