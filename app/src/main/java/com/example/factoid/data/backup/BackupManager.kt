package com.example.factoid.data.backup

import android.content.Context
import android.net.Uri
import com.example.factoid.data.local.DatabaseSnapshot
import com.example.factoid.data.local.dao.BackupDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class BackupManager(
    context: Context,
    private val backupDao: BackupDao
) {
    private val appContext: Context = context.applicationContext

    suspend fun exportToJson(): Result<String> = withContext(Dispatchers.IO) {
        guarded {
            encode(backupDao.exportSnapshot())
        }
    }

    suspend fun exportToUri(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        guarded {
            val snapshot = backupDao.exportSnapshot()
            val json = encode(snapshot)
            val stream = appContext.contentResolver.openOutputStream(uri, "wt")
                ?: throw BackupException.Io("Не удалось открыть файл для записи")
            stream.use {
                it.write(json.toByteArray(Charsets.UTF_8))
                it.flush()
            }
            snapshot.toSummary()
        }
    }

    suspend fun importFromUri(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        guarded {
            val stream = appContext.contentResolver.openInputStream(uri)
                ?: throw BackupException.Io("Не удалось открыть файл для чтения")
            val bytes = stream.use { readLimited(it) }
            val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
            val snapshot = decode(text)
            validate(snapshot)
            try {
                backupDao.replaceAll(snapshot)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw BackupException.Database("Не удалось записать данные в базу", e)
            }
            snapshot.toSummary()
        }
    }

    suspend fun validateJson(json: String): Result<BackupSummary> = withContext(Dispatchers.Default) {
        guarded {
            val snapshot = decode(json.removePrefix("\uFEFF"))
            validate(snapshot)
            snapshot.toSummary()
        }
    }

    private fun encode(snapshot: DatabaseSnapshot): String =
        try {
            BackupJson.instance.encodeToString(DatabaseSnapshot.serializer(), snapshot)
        } catch (e: SerializationException) {
            throw BackupException.InvalidFormat("Не удалось сформировать JSON", e)
        }

    private fun decode(text: String): DatabaseSnapshot {
        if (text.isBlank()) throw BackupException.InvalidFormat("Файл пуст")
        return try {
            BackupJson.instance.decodeFromString(DatabaseSnapshot.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException.InvalidFormat("Файл не является корректной резервной копией", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException.InvalidFormat("Файл не является корректной резервной копией", e)
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_IMPORT_BYTES) {
                throw BackupException.InvalidFormat("Файл слишком большой")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun validate(snapshot: DatabaseSnapshot) {
        if (snapshot.version != DatabaseSnapshot.CURRENT_VERSION) {
            throw BackupException.UnsupportedVersion(snapshot.version)
        }

        val boardIds = collectUniqueIds("Доски", snapshot.boards.map { it.id })
        collectUniqueIds("Группы", snapshot.groups.map { it.id })
        collectUniqueIds("Блокноты", snapshot.notebooks.map { it.id })
        collectUniqueIds("Заметки", snapshot.factoids.map { it.id })

        val groupBoard = HashMap<Long, Long>()
        for (group in snapshot.groups) {
            if (group.boardId !in boardIds) {
                throw BackupException.IntegrityViolation(
                    "Группа ${group.id} ссылается на несуществующую доску ${group.boardId}"
                )
            }
            checkColor("Группа ${group.id}", group.colorIndex)
            groupBoard[group.id] = group.boardId
        }

        val notebookBoard = HashMap<Long, Long>()
        for (notebook in snapshot.notebooks) {
            if (notebook.boardId !in boardIds) {
                throw BackupException.IntegrityViolation(
                    "Блокнот ${notebook.id} ссылается на несуществующую доску ${notebook.boardId}"
                )
            }
            checkColor("Блокнот ${notebook.id}", notebook.colorIndex)
            val groupId = notebook.groupId
            if (groupId != null) {
                val groupBoardId = groupBoard[groupId]
                    ?: throw BackupException.IntegrityViolation(
                        "Блокнот ${notebook.id} ссылается на несуществующую группу $groupId"
                    )
                if (groupBoardId != notebook.boardId) {
                    throw BackupException.IntegrityViolation(
                        "Блокнот ${notebook.id} и его группа $groupId принадлежат разным доскам"
                    )
                }
            }
            notebookBoard[notebook.id] = notebook.boardId
        }

        for (factoid in snapshot.factoids) {
            if (factoid.boardId !in boardIds) {
                throw BackupException.IntegrityViolation(
                    "Заметка ${factoid.id} ссылается на несуществующую доску ${factoid.boardId}"
                )
            }
            val notebookId = factoid.notebookId
            if (notebookId != null) {
                val notebookBoardId = notebookBoard[notebookId]
                    ?: throw BackupException.IntegrityViolation(
                        "Заметка ${factoid.id} ссылается на несуществующий блокнот $notebookId"
                    )
                if (notebookBoardId != factoid.boardId) {
                    throw BackupException.IntegrityViolation(
                        "Заметка ${factoid.id} и её блокнот $notebookId принадлежат разным доскам"
                    )
                }
            }
        }
    }

    private fun collectUniqueIds(label: String, ids: List<Long>): Set<Long> {
        val result = HashSet<Long>(ids.size * 2)
        for (id in ids) {
            if (id <= 0L) {
                throw BackupException.IntegrityViolation("$label: недопустимый идентификатор $id")
            }
            if (!result.add(id)) {
                throw BackupException.IntegrityViolation("$label: дублирующийся идентификатор $id")
            }
        }
        return result
    }

    private fun checkColor(label: String, colorIndex: Int) {
        if (colorIndex !in 0 until PALETTE_SIZE) {
            throw BackupException.IntegrityViolation("$label: недопустимый индекс цвета $colorIndex")
        }
    }

    private fun DatabaseSnapshot.toSummary(): BackupSummary =
        BackupSummary(
            boards = boards.size,
            groups = groups.size,
            notebooks = notebooks.size,
            factoids = factoids.size
        )

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(BackupException.Io("Ошибка чтения или записи файла", e))
        } catch (e: SecurityException) {
            Result.failure(BackupException.Io("Нет доступа к файлу", e))
        } catch (e: Exception) {
            Result.failure(BackupException.Database("Непредвиденная ошибка резервного копирования", e))
        }

    private companion object {
        const val PALETTE_SIZE = 12
        const val BUFFER_SIZE = 8 * 1024
        const val MAX_IMPORT_BYTES = 64L * 1024L * 1024L
    }
}

