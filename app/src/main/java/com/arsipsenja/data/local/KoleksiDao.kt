package com.arsipsenja.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KoleksiDao {

    @Query("SELECT * FROM koleksi ORDER BY nama COLLATE NOCASE ASC")
    fun alirSemua(): Flow<List<KoleksiEntity>>

    /**
     * Koleksi pribadi (buatan pengguna): sumber Dasbor, Pohon, Unggah, Tautan.
     * Hasil tarik-impor TIDAK termasuk di sini.
     */
    @Query("SELECT * FROM koleksi WHERE asalImpor = 0 ORDER BY nama COLLATE NOCASE ASC")
    fun alirPribadi(): Flow<List<KoleksiEntity>>

    /** Koleksi hasil "Tarik dari Telegram": hanya untuk tab Impor. */
    @Query("SELECT * FROM koleksi WHERE asalImpor = 1 ORDER BY nama COLLATE NOCASE ASC")
    fun alirImpor(): Flow<List<KoleksiEntity>>

    @Query("SELECT * FROM koleksi WHERE chatId = :chatId AND topicId = :topicId")
    fun alir(chatId: Long, topicId: Int): Flow<KoleksiEntity?>

    @Query("SELECT * FROM koleksi WHERE chatId = :chatId AND topicId = :topicId")
    suspend fun ambil(chatId: Long, topicId: Int): KoleksiEntity?

    /** Daftar pribadi sekali-baca (untuk rute unggah & perbaikan topik). */
    @Query("SELECT * FROM koleksi WHERE asalImpor = 0 ORDER BY nama COLLATE NOCASE ASC")
    suspend fun ambilSemuaPribadi(): List<KoleksiEntity>

    /** Daftar impor sekali-baca (untuk sinkron tab Impor). */
    @Query("SELECT * FROM koleksi WHERE asalImpor = 1 ORDER BY nama COLLATE NOCASE ASC")
    suspend fun ambilSemuaImpor(): List<KoleksiEntity>

    @Upsert
    suspend fun simpan(koleksi: KoleksiEntity)

    @Upsert
    suspend fun simpanSemua(koleksi: List<KoleksiEntity>)

    @Query("DELETE FROM koleksi WHERE chatId = :chatId AND topicId = :topicId")
    suspend fun hapus(chatId: Long, topicId: Int)

    @Query(
        """
        UPDATE koleksi SET jumlahBerkas =
            (SELECT COUNT(*) FROM berkas_arsip
             WHERE koleksiId = :chatId AND topicId = :topicId)
        WHERE chatId = :chatId AND topicId = :topicId
        """,
    )
    suspend fun hitungUlang(chatId: Long, topicId: Int)

    @Query(
        """
        UPDATE koleksi SET jumlahBerkas =
            (SELECT COUNT(*) FROM berkas_arsip b
             WHERE b.koleksiId = koleksi.chatId AND b.topicId = koleksi.topicId)
        """,
    )
    suspend fun hitungUlangSemua()
}
