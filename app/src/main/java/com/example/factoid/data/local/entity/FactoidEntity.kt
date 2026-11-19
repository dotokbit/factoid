package com.example.factoid.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "factoids",
    foreignKeys = [
        ForeignKey(
            entity = BoardEntity::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["boardId"]),
        Index(value = ["notebookId"]),
        Index(value = ["isDeleted"]),
        Index(value = ["boardId", "notebookId", "isDeleted"])
    ]
)
data class FactoidEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val boardId: Long,
    val notebookId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)