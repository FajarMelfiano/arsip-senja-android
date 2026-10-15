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

    /**
     * Hitung baris dengan nama + ukuran + chat yang sama dalam jendela
     * waktu. Dipakai guard idempoten retry [UploadWorker]: kiriman yang
     * sebenarnya sudah sampai (terindeks batch realtime) tidak dikirim ulang.
     */
    @Query(
        """
        SELECT COUNT(*) FROM berkas_arsip
        WHERE nama = :nama AND ukuran = :ukuran
          AND (koleksiId = :chatId OR chatId = :chatId)
          AND dibuatPada > :sejak
        """,
    )
    suspend fun hitungMirip(nama: String, ukuran: Long, chatId: Long, sejak: Long): Int

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

    // =====================================================================
    // Integritas & duplikat
    // =====================================================================

    /**
     * Cari berkas lain di vault yang punya hash isi sama persis.
     *
     * Inilah yang membuat arsip *content-addressed*: bila pengguna mengunggah
     * berkas yang sama dua kali, kiriman kedua bisa dikenali dan pengguna
     * diberi tahu alih-alih menyimpan dua salinan.
     *
     * @param kecualikanId id berkas saat ini; agar berkas tidak mencocoki diri sendiri.
     */
    @Query(
        """
        SELECT * FROM berkas_arsip
        WHERE hashSha256 = :hash AND id != :kecualikanId
        LIMIT 1
        """,
    )
    suspend fun cariDuplikatHash(hash: String, kecualikanId: String): BerkasArsipEntity?

    /** Apakah hash ini sudah ada di vault? Dipakai guard sebelum mengunggah. */
    @Query("SELECT COUNT(*) FROM berkas_arsip WHERE hashSha256 = :hash")
    suspend fun jumlahHash(hash: String): Int

    /** Baris yang punya hash tetapi belum pernah diverifikasi isinya. */
    @Query("SELECT * FROM berkas_arsip WHERE hashSha256 IS NOT NULL AND sudahDiverifikasi = 0 LIMIT :batas")
    suspend fun ambilBelumTerverifikasi(batas: Int): List<BerkasArsipEntity>

    @Query("UPDATE berkas_arsip SET sudahDiverifikasi = :ya WHERE id = :id")
    suspend fun tandaiTerverifikasi(id: String, ya: Boolean)

    @Query("UPDATE berkas_arsip SET sudahDiverifikasi = 0 WHERE hashSha256 IS NOT NULL AND sudahDiverifikasi = 1")
    suspend fun resetVerifikasi(): Int

    // =====================================================================
    // Kelompok potongan (berkas besar)
    // =====================================================================

    /** Semua potongan milik satu kelompok, terurut indeks. */
    @Query("SELECT * FROM berkas_arsip WHERE grupId = :grupId AND grupId IS NOT NULL ORDER BY indeksPotongan ASC")
    suspend fun ambilGrup(grupId: String): List<BerkasArsipEntity>

    /** Apakah kelompok ini sudah lengkapterisi di vault? */
    @Query("SELECT COUNT(*) FROM berkas_arsip WHERE grupId = :grupId")
    suspend fun jumlahPotonganGrup(grupId: String): Int

    // =====================================================================
    // Rekonsiliasi: penyelarasan Room dengan isi sebenarnya di Telegram
    // =====================================================================

    /**
     * Tandai seluruh baris satu koleksi sebagai "perlu dipastikan" (1).
     *
     * Dipakai sebagai langkah pertama rekonsiliasi. Baris yang benar-benar
     * masih ada di Telegram akan dikembalikan ke 0 saat pesannya ditemukan
     * dalam penyapuan; sisa yang bernilai 1 inilah yang hilang.
     *
     * Pendekatan **tandai-lalu-sapu** dipilih karena tidak butuh daftar `IN`
     * yang bisa berisi puluhan ribu id — biayanya tetap O(jumlah pesan),
     * bukan O(jumlah baris arsip).
     */
    @Query(
        """
        UPDATE berkas_arsip SET statusSinkron = 1
        WHERE koleksiId = :koleksiId AND topicId = :topicId
        """,
    )
    suspend fun tandaiPerluDicek(koleksiId: Long, topicId: Int): Int

    /** Kembalikan satu baris ke status "pasti masih ada" (0). */
    @Query("UPDATE berkas_arsip SET statusSinkron = 0 WHERE id = :id")
    suspend fun tandaiMasihAda(id: String)

    /**
     * Tandai satu baris sebagai hilang karena pesannya dihapus di Telegram.
     *
     * Baris sengaja tidak dihapus: salinan lokal pengguna mungkin masih ada
     * dan masih berguna. Yang hilang adalah salinan di vault, dan itu perlu
     * ditampilkan secara jujur.
     */
    @Query("UPDATE berkas_arsip SET statusSinkron = 1 WHERE id = :id")
    suspend fun tandaiHilang(id: String)

    /**
     * Tulis ulang hanya kolom yang berasal dari Telegram.
     *
     * Dipakai saat pesan diedit. Sengaja BUKAN @Upsert: @Upsert menimpa
     * seluruh kolom, sehingga `tdLocalPath` dan `thumbPath` — hasil kerja
     * lokal pengguna — ikut terhapus dan berkas yang sudah diunduh langsung
     * kembali terlihat seperti belum diunduh.
     */
    @Query(
        """
        UPDATE berkas_arsip SET
            nama = :nama,
            ukuran = :ukuran,
            mime = :mime,
            tipe = :tipe,
            deskripsi = :deskripsi,
            tdFileId = :tdFileId,
            statusSinkron = 0
        WHERE id = :id
        """,
    )
    suspend fun perbaruiDariTelegram(
        id: String,
        nama: String,
        ukuran: Long,
        mime: String,
        tipe: String,
        deskripsi: String?,
        tdFileId: Int,
    )

    /** How many rows yang setelah penyapuan tetap bernilai 1 → hilang. */
    @Query(
        """
        SELECT COUNT(*) FROM berkas_arsip
        WHERE statusSinkron = 1 AND koleksiId = :koleksiId AND topicId = :topicId
        """,
    )
    suspend fun jumlahHilang(koleksiId: Long, topicId: Int): Int

    /** Baris yang hilang, untuk ditampilkan/dipilih pengguna. */
    @Query("SELECT * FROM berkas_arsip WHERE statusSinkron = 1 ORDER BY tanggal DESC LIMIT :batas")
    fun alirHilang(batas: Int): Flow<List<BerkasArsipEntity>>

    /** Jumlah baris yang hilang di seluruh vault. */
    @Query("SELECT COUNT(*) FROM berkas_arsip WHERE statusSinkron = 1")
    fun alirJumlahHilang(): Flow<Int>

    /** Buang permanen baris hilang yang sudah tidak punya berkas lokal. */
    @Query("DELETE FROM berkas_arsip WHERE statusSinkron = 1 AND tdLocalPath IS NULL")
    suspend fun hapusHilangTanpaSalinan(): Int

    // =====================================================================
    // Sinkron: high-water-mark
    // =====================================================================

    /**
     * Message id tertinggi yang sudah terindeks untuk satu koleksi.
     *
     * Telegram memberi message id yang selalu menaik per chat, jadi message
     * baru di koleksi ini pasti lebih besar dari nilai ini. Ini menggantikan
     * `semuaId()` yang menyedot seluruh tabel setiap penyapuan.
     */
    @Query(
        """
        SELECT MAX(messageId) FROM berkas_arsip
        WHERE koleksiId = :koleksiId AND topicId = :topicId
        """,
    )
    suspend fun messageIdTertinggi(koleksiId: Long, topicId: Int): Long?

    /** Apakah id ini sudah terindeks? Menggantikan pemindaian seluruh tabel. */
    @Query("SELECT EXISTS(SELECT 1 FROM berkas_arsip WHERE id = :id)")
    suspend fun sudahAda(id: String): Boolean

    /** Jumlah baris yang sudah terindeks untuk sekumpulan id (satu query). */
    @Query("SELECT COUNT(*) FROM berkas_arsip WHERE id IN (:daftarId)")
    suspend fun jumlahDariId(daftarId: List<String>): Int
}
