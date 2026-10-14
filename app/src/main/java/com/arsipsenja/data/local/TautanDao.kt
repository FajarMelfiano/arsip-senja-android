package com.arsipsenja.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TautanDao {

    @Query("SELECT * FROM tautan ORDER BY dibuatPada DESC")
    fun alirSemua(): Flow<List<TautanEntity>>

    @Query("SELECT * FROM tautan WHERE berkasId = :berkasId ORDER BY dibuatPada DESC")
    fun alirUntukBerkas(berkasId: String): Flow<List<TautanEntity>>

    @Query("SELECT * FROM tautan WHERE url = :url LIMIT 1")
    suspend fun ambilUrl(url: String): TautanEntity?

    @Query("SELECT * FROM tautan WHERE id = :id")
    suspend fun ambil(id: Long): TautanEntity?

    @Upsert
    suspend fun simpan(tautan: TautanEntity)

    @Query("UPDATE tautan SET dicabut = 1 WHERE id = :id")
    suspend fun tandaiDicabut(id: Long)

    @Query("DELETE FROM tautan WHERE id = :id")
    suspend fun hapus(id: Long)

    @Query("DELETE FROM tautan WHERE chatId = :chatId")
    suspend fun hapusUntukChat(chatId: Long)

    @Query(
        """
        DELETE FROM tautan WHERE berkasId IN
            (SELECT id FROM berkas_arsip WHERE koleksiId = :chatId AND topicId = :topicId)
        """,
    )
    suspend fun hapusUntukKoleksi(chatId: Long, topicId: Int)
}
