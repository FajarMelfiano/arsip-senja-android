package com.arsipsenja.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Akses log konflik sinkron (Paket B2). */
@Dao
interface LogKonflikDao {

    @Insert
    suspend fun catat(entri: LogKonflikEntity)

    /** 50 konflik terbaru untuk layar "Log Konflik" di Pengaturan. */
    @Query("SELECT * FROM log_konflik ORDER BY dicatatPada DESC LIMIT 50")
    fun amatiTerbaru(): Flow<List<LogKonflikEntity>>

    /** Hapus catatan lebih tua dari 90 hari. */
    @Query("DELETE FROM log_konflik WHERE dicatatPada < :batasMilis")
    suspend fun hapusLama(batasMilis: Long)
}
