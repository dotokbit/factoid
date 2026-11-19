package com.example.factoid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.factoid.data.local.DatabaseSnapshot
import com.example.factoid.data.local.entity.BoardEntity
import com.example.factoid.data.local.entity.FactoidEntity
import com.example.factoid.data.local.entity.GroupEntity
import com.example.factoid.data.local.entity.NotebookEntity

@Dao
abstract class BackupDao {

    @Query("SELECT * FROM boards ORDER BY id ASC")
    protected abstract suspend fun readBoards(): List<BoardEntity>

    @Query("SELECT * FROM note_groups ORDER BY id ASC")
    protected abstract suspend fun readGroups(): List<GroupEntity>

    @Query("SELECT * FROM notebooks ORDER BY id ASC")
    protected abstract suspend fun readNotebooks(): List<NotebookEntity>

    @Query("SELECT * FROM factoids ORDER BY id ASC")
    protected abstract suspend fun readFactoids(): List<FactoidEntity>

    @Query("DELETE FROM factoids")
    protected abstract suspend fun deleteAllFactoids()

    @Query("DELETE FROM notebooks")
    protected abstract suspend fun deleteAllNotebooks()

    @Query("DELETE FROM note_groups")
    protected abstract suspend fun deleteAllGroups()

    @Query("DELETE FROM boards")
    protected abstract suspend fun deleteAllBoards()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertBoards(items: List<BoardEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertGroups(items: List<GroupEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertNotebooks(items: List<NotebookEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertFactoids(items: List<FactoidEntity>)

    @Transaction
    open suspend fun exportSnapshot(): DatabaseSnapshot = DatabaseSnapshot(
        version = DatabaseSnapshot.CURRENT_VERSION,
        boards = readBoards(),
        groups = readGroups(),
        notebooks = readNotebooks(),
        factoids = readFactoids()
    )

    @Transaction
    open suspend fun clearAll() {
        deleteAllFactoids()
        deleteAllNotebooks()
        deleteAllGroups()
        deleteAllBoards()
    }

    @Transaction
    open suspend fun replaceAll(snapshot: DatabaseSnapshot) {
        clearAll()
        insertBoards(snapshot.boards)
        insertGroups(snapshot.groups)
        insertNotebooks(snapshot.notebooks)
        insertFactoids(snapshot.factoids)
    }
}