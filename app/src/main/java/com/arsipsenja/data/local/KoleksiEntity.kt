package com.arsipsenja.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Koleksi = channel/supergroup privat di Telegram yang dipakai sebagai rak arsip.
 */
@Entity(tableName = "koleksi")
data class KoleksiEntity(
    @PrimaryKey val chatId: Long,
    val nama: String,
    val deskripsi: String?,
    val jumlahBerkas: Int,
    val dibuatPada: Long,
    val terakhirSinkron: Long,
)
