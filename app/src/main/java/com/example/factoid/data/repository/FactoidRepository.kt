package com.example.factoid.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.factoid.data.backup.BackupManager
import com.example.factoid.data.backup.BackupSummary
import com.example.factoid.data.local.AppDatabase
import com.example.factoid.data.local.dao.escapeForLike
import com.example.factoid.data.local.entity.BoardEntity
import com.example.factoid.data.local.entity.FactoidEntity
import com.example.factoid.data.local.entity.GroupEntity
import com.example.factoid.data.local.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SortMode {
    ALPHABETICAL,
    CREATED_AT
}

data class GroupWithNotebooks(
    val group: GroupEntity,
    val notebooks: List<NotebookEntity>
)

data class BoardContent(
    val groups: List<GroupWithNotebooks>,
    val ungroupedNotebooks: List<NotebookEntity>
)

sealed class QuickAddDestination {
    object Inbox : QuickAddDestination()
    data class ExistingNotebook(val notebookId: Long) : QuickAddDestination()
    data class NewNotebook(val name: String) : QuickAddDestination()
}

class FactoidRepository(
    private val database: AppDatabase,
    private val backupManager: BackupManager
) {
    private val boardDao = database.boardDao()
    private val groupDao = database.groupDao()
    private val notebookDao = database.notebookDao()
    private val factoidDao = database.factoidDao()

    private val initMutex = Mutex()

    fun observeBoards(): Flow<List<BoardEntity>> = boardDao.observeAll()

    fun observeBoard(boardId: Long): Flow<BoardEntity?> = boardDao.observeById(boardId)

    suspend fun getBoards(): List<BoardEntity> = boardDao.getAll()

    suspend fun getBoard(boardId: Long): BoardEntity? = boardDao.getById(boardId)

    suspend fun ensureInitialBoard(): Long = initMutex.withLock {
        database.withTransaction {
            val first = boardDao.getFirst()
            if (first != null && boardDao.count() > 0) {
                first.id
            } else {
                boardDao.insert(BoardEntity(name = DEFAULT_BOARD_NAME, createdAt = now()))
            }
        }
    }

    suspend fun resolveActiveBoardId(preferredBoardId: Long?): Long {
        if (preferredBoardId != null && boardDao.getById(preferredBoardId) != null) {
            return preferredBoardId
        }
        return ensureInitialBoard()
    }

    suspend fun createBoard(name: String): Long =
        boardDao.insert(BoardEntity(name = name.trim().ifEmpty { DEFAULT_BOARD_NAME }, createdAt = now()))

    suspend fun renameBoard(boardId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        boardDao.rename(boardId, trimmed)
    }

    suspend fun deleteBoard(boardId: Long) {
        database.withTransaction {
            val trashed = factoidDao.observeTrashOnce().filter { it.boardId == boardId }
            trashed.forEach { factoidDao.deletePermanently(it.id) }
            boardDao.deleteById(boardId)
        }
    }

    fun observeBoardContent(boardId: Long, sortMode: SortMode): Flow<BoardContent> =
        combine(
            groupDao.observeByBoard(boardId),
            notebookDao.observeByBoard(boardId)
        ) { groups, notebooks ->
            buildBoardContent(groups, notebooks, sortMode)
        }

    fun observeGroups(boardId: Long): Flow<List<GroupEntity>> = groupDao.observeByBoard(boardId)

    suspend fun getGroups(boardId: Long): List<GroupEntity> = groupDao.getByBoard(boardId)

    fun observeNotebooks(boardId: Long): Flow<List<NotebookEntity>> = notebookDao.observeByBoard(boardId)

    suspend fun getNotebooks(boardId: Long): List<NotebookEntity> = notebookDao.getByBoard(boardId)

    fun observeNotebook(notebookId: Long): Flow<NotebookEntity?> = notebookDao.observeById(notebookId)

    suspend fun getNotebook(notebookId: Long): NotebookEntity? = notebookDao.getById(notebookId)

    suspend fun createNotebook(
        boardId: Long,
        name: String,
        colorIndex: Int? = null,
        groupId: Long? = null
    ): Long = database.withTransaction {
        insertNotebook(boardId, name, colorIndex, groupId)
    }

    suspend fun renameNotebook(notebookId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        notebookDao.rename(notebookId, trimmed)
    }

    suspend fun changeNotebookColor(notebookId: Long, colorIndex: Int) {
        notebookDao.updateColor(notebookId, normalizeColor(colorIndex))
    }

    suspend fun setNotebookGroup(notebookId: Long, groupId: Long?): Boolean =
        database.withTransaction {
            val notebook = notebookDao.getById(notebookId) ?: return@withTransaction false
            if (groupId != null) {
                val group = groupDao.getById(groupId) ?: return@withTransaction false
                if (group.boardId != notebook.boardId) return@withTransaction false
            }
            notebookDao.updateGroup(notebookId, groupId)
            true
        }

    suspend fun createGroupForNotebook(notebookId: Long, groupName: String): Long? =
        database.withTransaction {
            val notebook = notebookDao.getById(notebookId) ?: return@withTransaction null
            val groupId = insertGroup(notebook.boardId, groupName, null)
            notebookDao.updateGroup(notebookId, groupId)
            groupId
        }

    suspend fun deleteNotebook(notebookId: Long) {
        notebookDao.deleteNotebookMovingFactoidsToInbox(notebookId)
    }

    suspend fun createGroup(boardId: Long, name: String, colorIndex: Int? = null): Long =
        database.withTransaction {
            insertGroup(boardId, name, colorIndex)
        }

    suspend fun renameGroup(groupId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        groupDao.rename(groupId, trimmed)
    }

    suspend fun changeGroupColor(groupId: Long, colorIndex: Int) {
        groupDao.updateColor(groupId, normalizeColor(colorIndex))
    }

    suspend fun deleteGroup(groupId: Long) {
        notebookDao.deleteGroupKeepingNotebooks(groupId)
    }

    fun observeNotebookFactoids(notebookId: Long): Flow<List<FactoidEntity>> =
        factoidDao.observeByNotebook(notebookId)

    fun observeNotebookFactoidCount(notebookId: Long): Flow<Int> =
        factoidDao.observeCountByNotebook(notebookId)

    fun observeInbox(boardId: Long): Flow<List<FactoidEntity>> = factoidDao.observeInbox(boardId)

    fun observeActiveFactoids(boardId: Long): Flow<List<FactoidEntity>> =
        factoidDao.observeActiveByBoard(boardId)

    suspend fun getFactoid(factoidId: Long): FactoidEntity? = factoidDao.getById(factoidId)

    suspend fun addFactoid(boardId: Long, text: String, notebookId: Long? = null): Long? =
        database.withTransaction {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@withTransaction null
            if (boardDao.getById(boardId) == null) return@withTransaction null
            if (notebookId != null) {
                val notebook = notebookDao.getById(notebookId) ?: return@withTransaction null
                if (notebook.boardId != boardId) return@withTransaction null
            }
            insertFactoid(boardId, trimmed, notebookId)
        }

    suspend fun quickAdd(boardId: Long, text: String, destination: QuickAddDestination): Long? =
        database.withTransaction {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@withTransaction null
            if (boardDao.getById(boardId) == null) return@withTransaction null
            when (destination) {
                is QuickAddDestination.Inbox -> insertFactoid(boardId, trimmed, null)
                is QuickAddDestination.ExistingNotebook -> {
                    val notebook = notebookDao.getById(destination.notebookId)
                        ?: return@withTransaction null
                    if (notebook.boardId != boardId) return@withTransaction null
                    insertFactoid(boardId, trimmed, notebook.id)
                }
                is QuickAddDestination.NewNotebook -> {
                    val notebookId = insertNotebook(boardId, destination.name, null, null)
                    insertFactoid(boardId, trimmed, notebookId)
                }
            }
        }

    suspend fun editFactoid(factoidId: Long, text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val factoid = factoidDao.getById(factoidId) ?: return false
        if (factoid.isDeleted) return false
        factoidDao.updateText(factoidId, trimmed, now())
        return true
    }

    suspend fun moveFactoid(factoidId: Long, targetNotebookId: Long?): Boolean =
        database.withTransaction {
            val factoid = factoidDao.getById(factoidId) ?: return@withTransaction false
            if (factoid.isDeleted) return@withTransaction false
            if (targetNotebookId != null) {
                val notebook = notebookDao.getById(targetNotebookId) ?: return@withTransaction false
                if (notebook.boardId != factoid.boardId) return@withTransaction false
            }
            factoidDao.move(factoidId, targetNotebookId, now())
            true
        }

    suspend fun moveFactoidToInbox(factoidId: Long): Boolean = moveFactoid(factoidId, null)

    suspend fun deleteFactoid(factoidId: Long) {
        factoidDao.moveToTrash(factoidId, now())
    }

    fun observeSearch(boardId: Long, query: String): Flow<List<FactoidEntity>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return flowOf(emptyList())
        return factoidDao.search(boardId, trimmed.escapeForLike())
    }

    suspend fun searchOnce(boardId: Long, query: String): List<FactoidEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return factoidDao.searchOnce(boardId, trimmed.escapeForLike())
    }

    fun observeTrash(): Flow<List<FactoidEntity>> = factoidDao.observeTrash()

    fun observeTrashCount(): Flow<Int> = factoidDao.observeTrashCount()

    suspend fun getOriginBoard(factoidId: Long): BoardEntity? {
        val factoid = factoidDao.getById(factoidId) ?: return null
        return boardDao.getById(factoid.boardId)
    }

    suspend fun restoreFactoid(factoidId: Long, targetBoardId: Long, targetNotebookId: Long?): Boolean =
        database.withTransaction {
            val factoid = factoidDao.getById(factoidId) ?: return@withTransaction false
            if (!factoid.isDeleted) return@withTransaction false
            if (boardDao.getById(targetBoardId) == null) return@withTransaction false
            if (targetNotebookId != null) {
                val notebook = notebookDao.getById(targetNotebookId) ?: return@withTransaction false
                if (notebook.boardId != targetBoardId) return@withTransaction false
            }
            factoidDao.restore(factoidId, targetBoardId, targetNotebookId, now())
            true
        }

    suspend fun deleteFactoidPermanently(factoidId: Long) {
        factoidDao.deletePermanently(factoidId)
    }

    suspend fun emptyTrash() {
        factoidDao.emptyTrash()
    }

    suspend fun exportToUri(uri: Uri): Result<BackupSummary> = backupManager.exportToUri(uri)

    suspend fun exportToJson(): Result<String> = backupManager.exportToJson()

    suspend fun importFromUri(uri: Uri): Result<BackupSummary> {
        val result = backupManager.importFromUri(uri)
        if (result.isSuccess) {
            ensureInitialBoard()
        }
        return result
    }

    private suspend fun insertNotebook(
        boardId: Long,
        name: String,
        colorIndex: Int?,
        groupId: Long?
    ): Long {
        val color = colorIndex?.let { normalizeColor(it) }
            ?: (notebookDao.getByBoard(boardId).size % PALETTE_SIZE)
        return notebookDao.insert(
            NotebookEntity(
                boardId = boardId,
                name = name.trim().ifEmpty { DEFAULT_NOTEBOOK_NAME },
                colorIndex = color,
                createdAt = now(),
                groupId = groupId
            )
        )
    }

    private suspend fun insertGroup(boardId: Long, name: String, colorIndex: Int?): Long {
        val color = colorIndex?.let { normalizeColor(it) }
            ?: (groupDao.getByBoard(boardId).size % PALETTE_SIZE)
        return groupDao.insert(
            GroupEntity(
                boardId = boardId,
                name = name.trim().ifEmpty { DEFAULT_GROUP_NAME },
                colorIndex = color,
                createdAt = now()
            )
        )
    }

    private suspend fun insertFactoid(boardId: Long, text: String, notebookId: Long?): Long {
        val timestamp = now()
        return factoidDao.insert(
            FactoidEntity(
                text = text,
                boardId = boardId,
                notebookId = notebookId,
                createdAt = timestamp,
                updatedAt = timestamp,
                isDeleted = false
            )
        )
    }

    private suspend fun com.example.factoid.data.local.dao.FactoidDao.observeTrashOnce(): List<FactoidEntity> =
        observeTrash().first()


    private fun buildBoardContent(
        groups: List<GroupEntity>,
        notebooks: List<NotebookEntity>,
        sortMode: SortMode
    ): BoardContent {
        val groupIds = groups.mapTo(HashSet()) { it.id }
        val notebookComparator = comparator<NotebookEntity>(sortMode, { it.name }, { it.createdAt }, { it.id })
        val groupComparator = comparator<GroupEntity>(sortMode, { it.name }, { it.createdAt }, { it.id })

        val byGroup = notebooks
            .filter { it.groupId != null && it.groupId in groupIds }
            .groupBy { it.groupId!! }
        val ungrouped = notebooks
            .filter { it.groupId == null || it.groupId !in groupIds }
            .sortedWith(notebookComparator)

        val sortedGroups = groups.sortedWith(groupComparator).map { group ->
            GroupWithNotebooks(
                group = group,
                notebooks = (byGroup[group.id] ?: emptyList()).sortedWith(notebookComparator)
            )
        }
        return BoardContent(groups = sortedGroups, ungroupedNotebooks = ungrouped)
    }

    private fun <T> comparator(
        sortMode: SortMode,
        name: (T) -> String,
        createdAt: (T) -> Long,
        id: (T) -> Long
    ): Comparator<T> = when (sortMode) {
        SortMode.ALPHABETICAL -> Comparator<T> { a, b ->
            val byName = String.CASE_INSENSITIVE_ORDER.compare(name(a), name(b))
            if (byName != 0) byName else {
                val byDate = createdAt(a).compareTo(createdAt(b))
                if (byDate != 0) byDate else id(a).compareTo(id(b))
            }
        }
        SortMode.CREATED_AT -> Comparator<T> { a, b ->
            val byDate = createdAt(a).compareTo(createdAt(b))
            if (byDate != 0) byDate else id(a).compareTo(id(b))
        }
    }

    private fun normalizeColor(colorIndex: Int): Int = Math.floorMod(colorIndex, PALETTE_SIZE)

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        const val DEFAULT_BOARD_NAME = "Моя доска"
        const val DEFAULT_NOTEBOOK_NAME = "Новый блокнот"
        const val DEFAULT_GROUP_NAME = "Новая группа"
        const val PALETTE_SIZE = 12

        fun create(context: Context): FactoidRepository {
            val database = AppDatabase.getInstance(context.applicationContext)
            return FactoidRepository(
                database = database,
                backupManager = BackupManager(context.applicationContext, database.backupDao())
            )
        }
    }
}