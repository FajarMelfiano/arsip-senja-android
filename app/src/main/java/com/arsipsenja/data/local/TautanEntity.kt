package com.arsipsenja.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tautan berbagi: tautan pesan (per berkas) atau tautan undangan (per koleksi).
 * Tautan undangan bisa dicabut; tautan pesan dicatat untuk disalin.
 */
@Entity(
    tableName = "tautan",
    foreignKeys = [
        ForeignKey(
            entity = BerkasArsipEntity::class,
            parentColumns = ["id"],
            childColumns = ["berkasId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["berkasId"]),
        Index(value = ["chatId"]),
        Index(value = ["url"], unique = true),
    ],
)
data class TautanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null untuk tautan undangan koleksi. */
    val berkasId: String?,
    val chatId: Long,
    val messageId: Long,
    val url: String,
    /** [JenisTautan] sebagai teks. */
    val jenis: String,
    val label: String,
    val dibuatPada: Long,
    val dicabut: Boolean,
)

enum class JenisTautan {
    PESAN,
    UNDANGAN,
}
