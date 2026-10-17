package com.arsipsenja.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Akses log operasi mutasi tertunda (Paket B1). */
@Dao
interface OperasiTertundaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun sisip(operasi: OperasiTertundaEntity)

    @Query("SELECT * FROM operasi_tertunda WHERE status = 'PENDING' ORDER BY dibuatPada ASC")
    suspend fun ambilTertunda(): List<OperasiTertundaEntity>

    /** True bila ada operasi PENDING untuk target ini — dipakai deteksi konflik. */
    @Query(
        "SELECT COUNT(*) > 0 FROM operasi_tertunda " +
            "WHERE status = 'PENDING' AND targetId = :targetId",
    )
    suspend fun adaTertundaUntuk(targetId: String): Boolean

    @Query(
        "UPDATE operasi_tertunda SET status = :status WHERE id = :id",
    )
    suspend fun ubahStatus(id: String, status: String)

    @Query(
        "UPDATE operasi_tertunda SET status = :status, percobaan = percobaan + 1 WHERE id = :id",
    )
    suspend fun tandaiGagal(id: String, status: String = StatusOperasi.GAGAL)

    /** Naikkan hitungan percobaan tanpa mengubah status (tetap PENDING). */
    @Query("UPDATE operasi_tertunda SET percobaan = percobaan + 1 WHERE id = :id")
    suspend fun tambahPercobaan(id: String)

    /** Jumlah operasi menunggu — untuk indikator "menunggu sinkron" di UI. */
    @Query("SELECT COUNT(*) FROM operasi_tertunda WHERE status = 'PENDING'")
    fun hitungTertunda(): Flow<Int>

    /** Bersihkan riwayat lama yang sudah selesai/dibatalkan (>30 hari). */
    @Query(
        "DELETE FROM operasi_tertunda WHERE status != 'PENDING' " +
            "AND dibuatPada < :batasMilis",
    )
    suspend fun hapusSelesaiLama(batasMilis: Long)
}
