package com.example.factoid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.factoid.data.local.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM note_groups WHERE boardId = :boardId ORDER BY createdAt ASC, id ASC")
    fun observeByBoard(boardId: Long): Flow<List<GroupEntity>>

    @Query("SELECT * FROM note_groups WHERE boardId = :boardId ORDER BY createdAt ASC, id ASC")
    suspend fun getByBoard(boardId: Long): List<GroupEntity>

    @Query("SELECT * FROM note_groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Query("UPDATE note_groups SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE note_groups SET colorIndex = :colorIndex WHERE id = :id")
    suspend fun updateColor(id: Long, colorIndex: Int)

    @Query("DELETE FROM note_groups WHERE id = :id")
    suspend fun deleteById(id: Long)
}