package com.example.factoid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.factoid.data.local.dao.BackupDao
import com.example.factoid.data.local.dao.BoardDao
import com.example.factoid.data.local.dao.FactoidDao
import com.example.factoid.data.local.dao.GroupDao
import com.example.factoid.data.local.dao.NotebookDao
import com.example.factoid.data.local.entity.BoardEntity
import com.example.factoid.data.local.entity.FactoidEntity
import com.example.factoid.data.local.entity.GroupEntity
import com.example.factoid.data.local.entity.NotebookEntity

@Database(
    entities = [
        BoardEntity::class,
        GroupEntity::class,
        NotebookEntity::class,
        FactoidEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun boardDao(): BoardDao
    abstract fun groupDao(): GroupDao
    abstract fun notebookDao(): NotebookDao
    abstract fun factoidDao(): FactoidDao
    abstract fun backupDao(): BackupDao

    companion object {
        private const val DATABASE_NAME = "factoid.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
    }
}