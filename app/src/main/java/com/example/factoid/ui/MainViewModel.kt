package com.example.factoid.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.factoid.data.backup.BackupSummary
import com.example.factoid.data.local.entity.BoardEntity
import com.example.factoid.data.local.entity.FactoidEntity
import com.example.factoid.data.local.entity.GroupEntity
import com.example.factoid.data.local.entity.NotebookEntity
import com.example.factoid.data.repository.BoardContent
import com.example.factoid.data.repository.FactoidRepository
import com.example.factoid.data.repository.QuickAddDestination
import com.example.factoid.data.repository.SortMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiEvent {
    data class Message(val text: String, val isError: Boolean = false) : UiEvent
    data class OpenNotebook(val notebookId: Long) : UiEvent
    data object OpenInbox : UiEvent
    data class ShareBackup(val json: String) : UiEvent
}

data class RestoreState(
    val factoidId: Long,
    val boards: List<BoardEntity>,
    val originBoardId: Long?,
    val selectedBoardId: Long?,
    val notebooks: List<NotebookEntity>
) {
    val canChooseBoard: Boolean get() = originBoardId == null
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(private val repository: FactoidRepository) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _activeBoardId = MutableStateFlow<Long?>(null)
    val activeBoardId: StateFlow<Long?> = _activeBoardId.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.ALPHABETICAL)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _selectedNotebookId = MutableStateFlow<Long?>(null)
    val selectedNotebookId: StateFlow<Long?> = _selectedNotebookId.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _quickAddVisible = MutableStateFlow(false)
    val quickAddVisible: StateFlow<Boolean> = _quickAddVisible.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreState?>(null)
    val restoreState: StateFlow<RestoreState?> = _restoreState.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _pendingImportUri = MutableStateFlow<Uri?>(null)
    val pendingImportUri: StateFlow<Uri?> = _pendingImportUri.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    val boards: StateFlow<List<BoardEntity>> = repository.observeBoards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val activeBoard: StateFlow<BoardEntity?> = combine(_activeBoardId, boards) { id, list ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    val boardContent: StateFlow<BoardContent> =
        combine(_activeBoardId.filterNotNull(), _sortMode) { id, mode -> id to mode }
            .flatMapLatest { (id, mode) -> repository.observeBoardContent(id, mode) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT),
                BoardContent(emptyList(), emptyList())
            )

    val groups: StateFlow<List<GroupEntity>> = _activeBoardId.filterNotNull()
        .flatMapLatest { repository.observeGroups(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val notebooks: StateFlow<List<NotebookEntity>> = _activeBoardId.filterNotNull()
        .flatMapLatest { repository.observeNotebooks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val notebookFactoidCounts: StateFlow<Map<Long, Int>> = _activeBoardId.filterNotNull()
        .flatMapLatest { repository.observeActiveFactoids(it) }
        .map { list ->
            list.mapNotNull { it.notebookId }.groupingBy { it }.eachCount()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyMap())

    val selectedNotebook: StateFlow<NotebookEntity?> = _selectedNotebookId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeNotebook(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    val notebookFactoids: StateFlow<List<FactoidEntity>> = _selectedNotebookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeNotebookFactoids(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val inbox: StateFlow<List<FactoidEntity>> = _activeBoardId.filterNotNull()
        .flatMapLatest { repository.observeInbox(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val searchResults: StateFlow<List<FactoidEntity>> =
        combine(
            _activeBoardId.filterNotNull(),
            _searchQuery.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()
        ) { id, query -> id to query }
            .flatMapLatest { (id, query) -> repository.observeSearch(id, query) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val trash: StateFlow<List<FactoidEntity>> = repository.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    val trashCount: StateFlow<Int> = repository.observeTrashCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), 0)

    init {
        viewModelScope.launch {
            try {
                _activeBoardId.value = repository.ensureInitialBoard()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitMessage("Не удалось открыть базу данных", true)
            } finally {
                _isReady.value = true
            }
        }
        viewModelScope.launch {
            repository.observeBoards().collect { list ->
                val current = _activeBoardId.value
                if (current != null && list.none { it.id == current }) {
                    try {
                        _activeBoardId.value = repository.resolveActiveBoardId(null)
                        resetBoardScopedState()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emitMessage("Не удалось выбрать доску", true)
                    }
                }
            }
        }
    }

    fun switchBoard(boardId: Long) {
        if (_activeBoardId.value == boardId) return
        launchCatching("Не удалось переключить доску") {
            if (repository.getBoard(boardId) == null) {
                emitMessage("Доска не найдена", true)
                return@launchCatching
            }
            resetBoardScopedState()
            _activeBoardId.value = boardId
        }
    }

    fun createBoard(name: String) {
        launchCatching("Не удалось создать доску") {
            val id = repository.createBoard(name)
            resetBoardScopedState()
            _activeBoardId.value = id
        }
    }

    fun renameBoard(boardId: Long, name: String) {
        if (name.isBlank()) {
            emitMessage("Введите название доски", true)
            return
        }
        launchCatching("Не удалось переименовать доску") {
            repository.renameBoard(boardId, name)
        }
    }

    fun deleteBoard(boardId: Long) {
        launchCatching("Не удалось удалить доску") {
            val wasActive = _activeBoardId.value == boardId
            repository.deleteBoard(boardId)
            if (wasActive) {
                _activeBoardId.value = repository.resolveActiveBoardId(null)
                resetBoardScopedState()
            }
            emitMessage("Доска удалена")
        }
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun createGroup(name: String, colorIndex: Int? = null) {
        val boardId = _activeBoardId.value ?: return
        launchCatching("Не удалось создать группу") {
            repository.createGroup(boardId, name, colorIndex)
        }
    }

    fun renameGroup(groupId: Long, name: String) {
        if (name.isBlank()) {
            emitMessage("Введите название группы", true)
            return
        }
        launchCatching("Не удалось переименовать группу") {
            repository.renameGroup(groupId, name)
        }
    }

    fun changeGroupColor(groupId: Long, colorIndex: Int) {
        launchCatching("Не удалось изменить цвет группы") {
            repository.changeGroupColor(groupId, colorIndex)
        }
    }

    fun deleteGroup(groupId: Long) {
        launchCatching("Не удалось удалить группу") {
            repository.deleteGroup(groupId)
        }
    }

    fun createNotebook(name: String, colorIndex: Int? = null, groupId: Long? = null) {
        val boardId = _activeBoardId.value ?: return
        launchCatching("Не удалось создать блокнот") {
            repository.createNotebook(boardId, name, colorIndex, groupId)
        }
    }

    fun renameNotebook(notebookId: Long, name: String) {
        if (name.isBlank()) {
            emitMessage("Введите название блокнота", true)
            return
        }
        launchCatching("Не удалось переименовать блокнот") {
            repository.renameNotebook(notebookId, name)
        }
    }

    fun changeNotebookColor(notebookId: Long, colorIndex: Int) {
        launchCatching("Не удалось изменить цвет блокнота") {
            repository.changeNotebookColor(notebookId, colorIndex)
        }
    }

    fun setNotebookGroup(notebookId: Long, groupId: Long?) {
        launchCatching("Не удалось изменить группу блокнота") {
            if (!repository.setNotebookGroup(notebookId, groupId)) {
                emitMessage("Не удалось изменить группу блокнота", true)
            }
        }
    }

    fun createGroupForNotebook(notebookId: Long, groupName: String) {
        launchCatching("Не удалось создать группу") {
            if (repository.createGroupForNotebook(notebookId, groupName) == null) {
                emitMessage("Не удалось создать группу", true)
            }
        }
    }

    fun deleteNotebook(notebookId: Long) {
        launchCatching("Не удалось удалить блокнот") {
            repository.deleteNotebook(notebookId)
            if (_selectedNotebookId.value == notebookId) {
                _selectedNotebookId.value = null
            }
            emitMessage("Блокнот удалён, заметки перемещены в Хранилище")
        }
    }

    fun openNotebook(notebookId: Long) {
        _selectedNotebookId.value = notebookId
    }

    fun closeNotebook() {
        _selectedNotebookId.value = null
    }

    fun showQuickAdd() {
        _quickAddVisible.value = true
    }

    fun dismissQuickAdd() {
        _quickAddVisible.value = false
    }

    fun quickAdd(text: String, destination: QuickAddDestination) {
        val boardId = _activeBoardId.value ?: return
        if (text.isBlank()) {
            emitMessage("Введите текст заметки", true)
            return
        }
        launchCatching("Не удалось добавить заметку") {
            val id = repository.quickAdd(boardId, text, destination)
            if (id == null) {
                emitMessage("Не удалось добавить заметку", true)
            } else {
                _quickAddVisible.value = false
            }
        }
    }

    fun addFactoidToNotebook(notebookId: Long, text: String) {
        val boardId = _activeBoardId.value ?: return
        if (text.isBlank()) {
            emitMessage("Введите текст заметки", true)
            return
        }
        launchCatching("Не удалось добавить заметку") {
            if (repository.addFactoid(boardId, text, notebookId) == null) {
                emitMessage("Не удалось добавить заметку", true)
            }
        }
    }

    fun editFactoid(factoidId: Long, text: String) {
        if (text.isBlank()) {
            emitMessage("Текст заметки не может быть пустым", true)
            return
        }
        launchCatching("Не удалось сохранить заметку") {
            if (!repository.editFactoid(factoidId, text)) {
                emitMessage("Не удалось сохранить заметку", true)
            }
        }
    }

    fun moveFactoid(factoidId: Long, targetNotebookId: Long?) {
        launchCatching("Не удалось переместить заметку") {
            if (!repository.moveFactoid(factoidId, targetNotebookId)) {
                emitMessage("Не удалось переместить заметку", true)
            }
        }
    }

    fun moveFactoidToInbox(factoidId: Long) {
        launchCatching("Не удалось переместить заметку") {
            if (!repository.moveFactoidToInbox(factoidId)) {
                emitMessage("Не удалось переместить заметку", true)
            }
        }
    }

    fun deleteFactoid(factoidId: Long) {
        launchCatching("Не удалось удалить заметку") {
            repository.deleteFactoid(factoidId)
            emitMessage("Заметка перемещена в корзину")
        }
    }

    fun openSearch() {
        _isSearchActive.value = true
    }

    fun closeSearch() {
        _isSearchActive.value = false
        _searchQuery.value = ""
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSearchResultSelected(factoid: FactoidEntity) {
        closeSearch()
        val notebookId = factoid.notebookId
        if (notebookId == null) {
            _events.trySend(UiEvent.OpenInbox)
        } else {
            _selectedNotebookId.value = notebookId
            _events.trySend(UiEvent.OpenNotebook(notebookId))
        }
    }

    fun startRestore(factoidId: Long) {
        launchCatching("Не удалось подготовить восстановление") {
            if (repository.getFactoid(factoidId) == null) {
                emitMessage("Заметка не найдена", true)
                return@launchCatching
            }
            val allBoards = repository.getBoards()
            if (allBoards.isEmpty()) {
                emitMessage("Нет доступных досок для восстановления", true)
                return@launchCatching
            }
            val origin = repository.getOriginBoard(factoidId)
            val selected = origin?.id
            val targetNotebooks =
                if (selected != null) repository.getNotebooks(selected) else emptyList()
            _restoreState.value = RestoreState(
                factoidId = factoidId,
                boards = allBoards,
                originBoardId = origin?.id,
                selectedBoardId = selected,
                notebooks = targetNotebooks
            )
        }
    }

    fun selectRestoreBoard(boardId: Long) {
        val state = _restoreState.value ?: return
        if (!state.canChooseBoard) return
        launchCatching("Не удалось загрузить блокноты") {
            val targetNotebooks = repository.getNotebooks(boardId)
            _restoreState.value = state.copy(selectedBoardId = boardId, notebooks = targetNotebooks)
        }
    }

    fun confirmRestore(targetNotebookId: Long?) {
        val state = _restoreState.value ?: return
        val boardId = state.selectedBoardId
        if (boardId == null) {
            emitMessage("Выберите доску для восстановления", true)
            return
        }
        launchCatching("Не удалось восстановить заметку") {
            if (repository.restoreFactoid(state.factoidId, boardId, targetNotebookId)) {
                _restoreState.value = null
                emitMessage("Заметка восстановлена")
            } else {
                emitMessage("Не удалось восстановить заметку", true)
            }
        }
    }

    fun cancelRestore() {
        _restoreState.value = null
    }

    fun deleteFactoidPermanently(factoidId: Long) {
        launchCatching("Не удалось удалить заметку") {
            repository.deleteFactoidPermanently(factoidId)
        }
    }

    fun emptyTrash() {
        launchCatching("Не удалось очистить корзину") {
            repository.emptyTrash()
            emitMessage("Корзина очищена")
        }
    }

    fun exportBackup(uri: Uri) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                repository.exportToUri(uri).fold(
                    onSuccess = { emitMessage("Экспорт выполнен: ${describeSummary(it)}") },
                    onFailure = { emitMessage("Ошибка экспорта: ${describeError(it)}", true) }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun shareBackup() {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                repository.exportToJson().fold(
                    onSuccess = { _events.trySend(UiEvent.ShareBackup(it)) },
                    onFailure = { emitMessage("Ошибка экспорта: ${describeError(it)}", true) }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun requestImport(uri: Uri) {
        if (_isBackupBusy.value) return
        _pendingImportUri.value = uri
    }

    fun cancelImport() {
        _pendingImportUri.value = null
    }

    fun confirmImport() {
        val uri = _pendingImportUri.value ?: return
        _pendingImportUri.value = null
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                repository.importFromUri(uri).fold(
                    onSuccess = { summary ->
                        try {
                            _activeBoardId.value = repository.resolveActiveBoardId(null)
                            resetBoardScopedState()
                            _selectedNotebookId.value = null
                            _restoreState.value = null
                            emitMessage("Импорт выполнен: ${describeSummary(summary)}")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emitMessage("Импорт выполнен, но не удалось открыть доску", true)
                        }
                    },
                    onFailure = {
                        emitMessage(
                            "Импорт не выполнен, текущие данные сохранены: ${describeError(it)}",
                            true
                        )
                    }
                )
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    private fun resetBoardScopedState() {
        _selectedNotebookId.value = null
        _isSearchActive.value = false
        _searchQuery.value = ""
        _quickAddVisible.value = false
    }

    private fun describeSummary(summary: BackupSummary): String =
        "досок ${summary.boards}, групп ${summary.groups}, " +
                "блокнотов ${summary.notebooks}, заметок ${summary.factoids}"

    private fun describeError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: "неизвестная ошибка"

    private fun emitMessage(text: String, isError: Boolean = false) {
        _events.trySend(UiEvent.Message(text, isError))
    }

    private fun launchCatching(errorMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emitMessage(errorMessage, true)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}

class MainViewModelFactory(
    private val repository: FactoidRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return MainViewModel(repository) as T
    }
}