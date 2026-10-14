package com.arsipsenja.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface BerkasArsipDao {

    @Query("SELECT COUNT(*) AS jumlah, COALESCE(SUM(ukuran), 0) AS total FROM berkas_arsip")
    fun alirStatistik(): Flow<StatistikBerkas>

    /**
     * Statistik khusus koleksi pribadi: berkas milik koleksi impor
     * (asalImpor = 1, dicocokkan lewat koleksiId/chatId + topicId) dikecualikan
     * agar angka Dasbor tidak tercampur hasil tarikan.
     */
    @Query(
        """
        SELECT COUNT(*) AS jumlah, COALESCE(SUM(ukuran), 0) AS total FROM berkas_arsip b
        WHERE NOT EXISTS (
            SELECT 1 FROM koleksi k
            WHERE k.chatId = COALESCE(b.koleksiId, b.chatId)
              AND k.topicId = b.topicId
              AND k.asalImpor = 1
        )
        """,
    )
    fun alirStatistikPribadi(): Flow<StatistikBerkas>

    @Query("SELECT * FROM berkas_arsip WHERE id = :id")
    fun alirId(id: String): Flow<BerkasArsipEntity?>

    @Query("SELECT * FROM berkas_arsip WHERE id = :id")
    suspend fun ambilId(id: String): BerkasArsipEntity?

    @RawQuery(observedEntities = [BerkasArsipEntity::class])
    fun alirDaftar(query: SupportSQLiteQuery): Flow<List<BerkasArsipEntity>>

    @Query("SELECT COUNT(*) FROM berkas_arsip WHERE koleksiId = :koleksiId AND topicId = :topicId")
    fun alirJumlah(koleksiId: Long, topicId: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM berkas_arsip WHERE koleksiId = :koleksiId AND topicId = :topicId")
    suspend fun jumlah(koleksiId: Long, topicId: Int): Int

    @Query(
        """
        SELECT * FROM berkas_arsip
        WHERE (:koleksiId IS NULL OR koleksiId = :koleksiId)
        ORDER BY nomorKatalog DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun potongan(koleksiId: Long?, limit: Int, offset: Int): List<BerkasArsipEntity>

    @Query("SELECT id FROM berkas_arsip")
    suspend fun semuaId(): List<String>

    @Upsert
    suspend fun simpan(berkas: BerkasArsipEntity)

    @Upsert
    suspend fun simpanSemua(berkas: List<BerkasArsipEntity>)

    @Query("UPDATE berkas_arsip SET tdLocalPath = :path WHERE id = :id")
    suspend fun tandaiLokal(id: String, path: String?)

    @Query("UPDATE berkas_arsip SET thumbPath = :path WHERE id = :id")
    suspend fun aturThumb(id: String, path: String?)

    @Query("UPDATE berkas_arsip SET messageId = :messageId, id = :idBaru WHERE id = :idLama")
    suspend fun perbaikiId(idLama: String, idBaru: String, messageId: Long)

    @Query("DELETE FROM berkas_arsip WHERE koleksiId = :koleksiId AND topicId = :topicId")
    suspend fun hapusUntukKoleksi(koleksiId: Long, topicId: Int)

    @Query("DELETE FROM berkas_arsip WHERE id = :id")
    suspend fun hapus(id: String)
}
