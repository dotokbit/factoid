package com.example.factoid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.factoid.data.local.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NotebookDao {

    @Query("SELECT * FROM notebooks WHERE boardId = :boardId ORDER BY createdAt ASC, id ASC")
    abstract fun observeByBoard(boardId: Long): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE boardId = :boardId ORDER BY createdAt ASC, id ASC")
    abstract suspend fun getByBoard(boardId: Long): List<NotebookEntity>

    @Query("SELECT * FROM notebooks WHERE id = :id LIMIT 1")
    abstract fun observeById(id: Long): Flow<NotebookEntity?>

    @Query("SELECT * FROM notebooks WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE groupId = :groupId ORDER BY createdAt ASC, id ASC")
    abstract suspend fun getByGroup(groupId: Long): List<NotebookEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(notebook: NotebookEntity): Long

    @Update
    abstract suspend fun update(notebook: NotebookEntity)

    @Query("UPDATE notebooks SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("UPDATE notebooks SET colorIndex = :colorIndex WHERE id = :id")
    abstract suspend fun updateColor(id: Long, colorIndex: Int)

    @Query("UPDATE notebooks SET groupId = :groupId WHERE id = :id")
    abstract suspend fun updateGroup(id: Long, groupId: Long?)

    @Query("UPDATE notebooks SET groupId = NULL WHERE groupId = :groupId")
    abstract suspend fun clearGroup(groupId: Long)

    @Query("UPDATE factoids SET notebookId = NULL WHERE notebookId = :notebookId AND isDeleted = 0")
    protected abstract suspend fun moveActiveFactoidsToInbox(notebookId: Long)

    @Query("DELETE FROM notebooks WHERE id = :id")
    protected abstract suspend fun deleteById(id: Long)

    @Transaction
    open suspend fun deleteNotebookMovingFactoidsToInbox(notebookId: Long) {
        moveActiveFactoidsToInbox(notebookId)
        deleteById(notebookId)
    }

    @Transaction
    open suspend fun deleteGroupKeepingNotebooks(groupId: Long) {
        clearGroup(groupId)
        deleteGroupById(groupId)
    }

    @Query("DELETE FROM note_groups WHERE id = :groupId")
    protected abstract suspend fun deleteGroupById(groupId: Long)
}
