package com.example.factoid.data.local

import com.example.factoid.data.local.entity.BoardEntity
import com.example.factoid.data.local.entity.FactoidEntity
import com.example.factoid.data.local.entity.GroupEntity
import com.example.factoid.data.local.entity.NotebookEntity
import kotlinx.serialization.Serializable

@Serializable
data class DatabaseSnapshot(
    val version: Int = CURRENT_VERSION,
    val boards: List<BoardEntity> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val notebooks: List<NotebookEntity> = emptyList(),
    val factoids: List<FactoidEntity> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
