package com.example.factoid.data.backup

import kotlinx.serialization.json.Json

object BackupJson {
    val instance: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = false
    }
}

data class BackupSummary(
    val boards: Int,
    val groups: Int,
    val notebooks: Int,
    val factoids: Int
)

sealed class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Io(message: String, cause: Throwable? = null) : BackupException(message, cause)
    class InvalidFormat(message: String, cause: Throwable? = null) : BackupException(message, cause)
    class UnsupportedVersion(val version: Int) :
        BackupException("Неподдерживаемая версия резервной копии: $version")
    class IntegrityViolation(message: String) : BackupException(message)
    class Database(message: String, cause: Throwable? = null) : BackupException(message, cause)
}
