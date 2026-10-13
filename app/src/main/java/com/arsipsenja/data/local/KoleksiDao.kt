package com.arsipsenja.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface KoleksiDao {

    @Query("SELECT * FROM koleksi ORDER BY nama COLLATE NOCASE ASC")
    fun alirSemua(): Flow<List<KoleksiEntity>>

    @Query("SELECT * FROM koleksi WHERE chatId = :chatId")
    fun alir(chatId: Long): Flow<KoleksiEntity?>

    @Query("SELECT * FROM koleksi WHERE chatId = :chatId")
    suspend fun ambil(chatId: Long): KoleksiEntity?

    @Upsert
    suspend fun simpan(koleksi: KoleksiEntity)

    @Upsert
    suspend fun simpanSemua(koleksi: List<KoleksiEntity>)

    @Query("DELETE FROM koleksi WHERE chatId = :chatId")
    suspend fun hapus(chatId: Long)

    @Query(
        """
        UPDATE koleksi SET jumlahBerkas =
            (SELECT COUNT(*) FROM berkas_arsip WHERE koleksiId = :chatId)
        WHERE chatId = :chatId
        """,
    )
    suspend fun hitungUlang(chatId: Long)

    @Query(
        """
        UPDATE koleksi SET jumlahBerkas =
            (SELECT COUNT(*) FROM berkas_arsip WHERE koleksiId = koleksi.chatId)
        """,
    )
    suspend fun hitungUlangSemua()
}
