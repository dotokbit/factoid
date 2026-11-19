package com.example.factoid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.factoid.data.local.entity.FactoidEntity
import kotlinx.coroutines.flow.Flow

fun String.escapeForLike(): String = this
    .replace("\\", "\\\\")
    .replace("%", "\\%")
    .replace("_", "\\_")

@Dao
interface FactoidDao {

    @Query(
        "SELECT * FROM factoids " +
                "WHERE notebookId = :notebookId AND isDeleted = 0 " +
                "ORDER BY createdAt DESC, id DESC"
    )
    fun observeByNotebook(notebookId: Long): Flow<List<FactoidEntity>>

    @Query(
        "SELECT * FROM factoids " +
                "WHERE boardId = :boardId AND notebookId IS NULL AND isDeleted = 0 " +
                "ORDER BY createdAt DESC, id DESC"
    )
    fun observeInbox(boardId: Long): Flow<List<FactoidEntity>>

    @Query(
        "SELECT * FROM factoids " +
                "WHERE boardId = :boardId AND isDeleted = 0 " +
                "ORDER BY createdAt DESC, id DESC"
    )
    fun observeActiveByBoard(boardId: Long): Flow<List<FactoidEntity>>

    @Query(
        "SELECT * FROM factoids " +
                "WHERE boardId = :boardId AND isDeleted = 0 " +
                "ORDER BY createdAt DESC, id DESC"
    )
    suspend fun getActiveByBoard(boardId: Long): List<FactoidEntity>

    @Query(
        "SELECT * FROM factoids " +
                "WHERE boardId = :boardId AND isDeleted = 0 " +
                "AND text LIKE '%' || :escapedQuery || '%' ESCAPE '\\' " +
                "ORDER BY updatedAt DESC, id DESC"
    )
    fun search(boardId: Long, escapedQuery: String): Flow<List<FactoidEntity>>

    @Query(
        "SELECT * FROM factoids " +
                "WHERE boardId = :boardId AND isDeleted = 0 " +
                "AND text LIKE '%' || :escapedQuery || '%' ESCAPE '\\' " +
                "ORDER BY updatedAt DESC, id DESC"
    )
    suspend fun searchOnce(boardId: Long, escapedQuery: String): List<FactoidEntity>

    @Query("SELECT * FROM factoids WHERE isDeleted = 1 ORDER BY updatedAt DESC, id DESC")
    fun observeTrash(): Flow<List<FactoidEntity>>

    @Query("SELECT COUNT(*) FROM factoids WHERE isDeleted = 1")
    fun observeTrashCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM factoids " +
                "WHERE notebookId = :notebookId AND isDeleted = 0"
    )
    fun observeCountByNotebook(notebookId: Long): Flow<Int>

    @Query("SELECT * FROM factoids WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FactoidEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(factoid: FactoidEntity): Long

    @Update
    suspend fun update(factoid: FactoidEntity)

    @Query("UPDATE factoids SET text = :text, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateText(id: Long, text: String, updatedAt: Long)

    @Query("UPDATE factoids SET notebookId = :notebookId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun move(id: Long, notebookId: Long?, updatedAt: Long)

    @Query("UPDATE factoids SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveToTrash(id: Long, updatedAt: Long)

    @Query(
        "UPDATE factoids SET isDeleted = 0, boardId = :boardId, " +
                "notebookId = :notebookId, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun restore(id: Long, boardId: Long, notebookId: Long?, updatedAt: Long)

    @Query("DELETE FROM factoids WHERE id = :id AND isDeleted = 1")
    suspend fun deletePermanently(id: Long)

    @Query("DELETE FROM factoids WHERE isDeleted = 1")
    suspend fun emptyTrash()
}