package com.example.factoid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.factoid.data.local.entity.BoardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {

    @Query("SELECT * FROM boards ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<BoardEntity>

    @Query("SELECT * FROM boards WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<BoardEntity?>

    @Query("SELECT * FROM boards WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BoardEntity?

    @Query("SELECT * FROM boards ORDER BY createdAt ASC, id ASC LIMIT 1")
    suspend fun getFirst(): BoardEntity?

    @Query("SELECT COUNT(*) FROM boards")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(board: BoardEntity): Long

    @Update
    suspend fun update(board: BoardEntity)

    @Query("UPDATE boards SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM boards WHERE id = :id")
    suspend fun deleteById(id: Long)
}