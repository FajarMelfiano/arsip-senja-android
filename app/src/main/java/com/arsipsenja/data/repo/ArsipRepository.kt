package com.arsipsenja.data.repo

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.arsipsenja.data.local.ArsipDatabase
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.JenisTautan
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KueriTampilan
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.local.StatistikBerkas
import com.arsipsenja.data.local.TautanEntity
import com.arsipsenja.data.telegram.TelegramClient
import com.arsipsenja.data.telegram.TelegramException
import com.arsipsenja.data.telegram.keBerkasMentah
import com.arsipsenja.data.telegram.keEntity
import com.arsipsenja.data.work.DownloadWorker
import com.arsipsenja.data.work.UploadWorker
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Sumber kebenaran aplikasi: Room sebagai indeks lokal, TDLib sebagai sumber
 * data sesungguhnya. Semua operasi tingkat tinggi lewat sini.
 */
class ArsipRepository(
    @Suppress("unused") private val context: Context,
    private val db: ArsipDatabase,
    val telegram: TelegramClient,
    private val pengaturan: PengaturanStore,
    private val workManager: WorkManager,
) {

    private val berkasDao = db.berkasDao()
    private val koleksiDao = db.koleksiDao()
    private val tautanDao = db.tautanDao()

    // ---- Aliran data ----

    fun alirBerkas(kueri: KueriTampilan): Flow<List<BerkasArsipEntity>> =
        berkasDao.alirDaftar(kueri.bangun().keSupportQuery())

    fun alirStatistik(): Flow<StatistikBerkas> = berkasDao.alirStatistik()

    fun alirKoleksi(): Flow<List<KoleksiEntity>> = koleksiDao.alirSemua()

    fun alirKoleksi(chatId: Long): Flow<KoleksiEntity?> = koleksiDao.alir(chatId)

    fun alirTautan(): Flow<List<TautanEntity>> = tautanDao.alirSemua()

    fun alirTautanBerkas(berkasId: String): Flow<List<TautanEntity>> =
        tautanDao.alirUntukBerkas(berkasId)

    fun alirBerkasId(id: String): Flow<BerkasArsipEntity?> = berkasDao.alirId(id)

    suspend fun ambilBerkas(id: String): BerkasArsipEntity? = berkasDao.ambilId(id)

    suspend fun tampilanAwal(): KueriTampilan = KueriTampilan(
        urut = pengaturan.urutBerkas.first(),
        arah = pengaturan.arahUrut.first(),
    )

    // ---- Sinkronisasi ----

    /**
     * Sinkronkan semua koleksi yang sudah terdaftar (ambil riwayat terbaru,
     * masukkan berkas baru ke indeks lokal).
     */
    suspend fun sinkronSemua() {
        val koleksi = koleksiDao.alirSemua().first()
        koleksi.forEach { sinkronKoleksi(it.chatId) }
        koleksiDao.hitungUlangSemua()
    }

    /** Sinkron satu koleksi; mengembalikan jumlah berkas baru yang masuk. */
    suspend fun sinkronKoleksi(chatId: Long): Int {
        val sudahAda = berkasDao.semuaId().toHashSet()
        var nomor = berkasDao.jumlah(koleksiId = chatId)
        var dari = 0L
        var baru = 0

        repeat(BATAS_HALAMAN) {
            val pesan = telegram.riwayat(chatId, dari, HALAMAN_RIWAYAT)
            if (pesan.isEmpty()) return@repeat

            val mentah = pesan.mapNotNull { it.keBerkasMentah() }
            val sekarang = System.currentTimeMillis()
            val entri = mentah.mapNotNull { b ->
                val id = BerkasArsipEntity.kunci(b.chatId, b.messageId)
                if (id in sudahAda) return@mapNotNull null
                sudahAda += id
                nomor += 1
                baru += 1
                b.keEntity(koleksiId = chatId, nomorKatalog = nomor, sekarang = sekarang)
            }
            berkasDao.simpanSemua(entri)
            dari = pesan.last().id
            if (pesan.size < HALAMAN_RIWAYAT) return@repeat
        }

        koleksiDao.hitungUlang(chatId)
        koleksiDao.ambil(chatId)?.let {
            koleksiDao.simpan(it.copy(terakhirSinkron = System.currentTimeMillis()))
        }
        return baru
    }

    /** Daftar channel Telegram yang tersedia dan petakan ke koleksi lokal. */
    suspend fun tarikDaftarKoleksi(): List<KoleksiEntity> {
        val channel = telegram.daftarChannel()
        val sekarang = System.currentTimeMillis()
        val entri = channel.map { chat ->
            val lama = koleksiDao.ambil(chat.id)
            KoleksiEntity(
                chatId = chat.id,
                nama = chat.title.orEmpty().ifBlank { "Koleksi ${chat.id}" },
                deskripsi = lama?.deskripsi,
                jumlahBerkas = lama?.jumlahBerkas ?: 0,
                dibuatPada = lama?.dibuatPada ?: sekarang,
                terakhirSinkron = lama?.terakhirSinkron ?: 0L,
            )
        }
        koleksiDao.simpanSemua(entri)
        return entri
    }

    // ---- Koleksi ----

    /** Buat koleksi baru (channel privat di Telegram). */
    suspend fun buatKoleksi(nama: String, deskripsi: String): KoleksiEntity {
        val chat = telegram.buatChannel(nama, deskripsi)
        val koleksi = KoleksiEntity(
            chatId = chat.id,
            nama = chat.title?.takeIf { it.isNotBlank() } ?: nama,
            deskripsi = deskripsi.takeIf { it.isNotBlank() },
            jumlahBerkas = 0,
            dibuatPada = System.currentTimeMillis(),
            terakhirSinkron = System.currentTimeMillis(),
        )
        koleksiDao.simpan(koleksi)
        return koleksi
    }

    suspend fun ubahKoleksi(chatId: Long, nama: String, deskripsi: String) {
        telegram.ubahJudul(chatId, nama)
        telegram.ubahDeskripsi(chatId, deskripsi)
        val lama = koleksiDao.ambil(chatId) ?: return
        koleksiDao.simpan(
            lama.copy(
                nama = nama.trim(),
                deskripsi = deskripsi.trim().ifBlank { null },
            ),
        )
    }

    /**
     * Hapus koleksi. Bila [tinggalkanChannel] true, channel juga ditinggalkan
     * di Telegram (berkas di Telegram tidak ikut terhapus oleh aplikasi ini).
     */
    suspend fun hapusKoleksi(chatId: Long, tinggalkanChannel: Boolean) {
        berkasDao.hapusUntukKoleksi(chatId)
        tautanDao.hapusUntukChat(chatId)
        koleksiDao.hapus(chatId)
        if (tinggalkanChannel) {
            runCatching { telegram.tinggalkanChannel(chatId) }
        }
    }

    suspend fun koleksiTerpilihAtauPertama(): Long? =
        koleksiDao.alirSemua().first().firstOrNull()?.chatId

    // ---- Unggah ----

    /** Antrekan unggah beberapa berkas SAF ke satu koleksi. */
    fun mulaiUnggah(uris: List<Uri>, koleksiId: Long) {
        uris.forEach { uri ->
            val pekerjaan = "unggah-${UUID.randomUUID()}"
            val permintaan = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(
                    workDataOf(
                        UploadWorker.K_URI to uri.toString(),
                        UploadWorker.K_KOLEKSI to koleksiId,
                    ),
                )
                .addTag(pekerjaan)
                .addTag(TAG_UNGGAH)
                .build()
            workManager.enqueueUniqueWork(pekerjaan, ExistingWorkPolicy.APPEND_OR_REPLACE, permintaan)
        }
    }

    // ---- Unduh ----

    suspend fun mulaiUnduh(berkasId: String) {
        val berkas = berkasDao.ambilId(berkasId) ?: return
        val permintaan = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.K_BERKAS to berkasId,
                    DownloadWorker.K_FILE_ID to berkas.tdFileId,
                ),
            )
            .build()
        workManager.enqueueUniqueWork("unduh-$berkasId", ExistingWorkPolicy.APPEND_OR_REPLACE, permintaan)
    }

    suspend fun tandaiLokal(berkasId: String, path: String?) {
        berkasDao.tandaiLokal(berkasId, path)
    }

    // ---- Tautan ----

    /** Buat tautan pesan untuk berkas, lalu catat di indeks tautan. */
    suspend fun buatTautanPesan(berkasId: String): String {
        val berkas = berkasDao.ambilId(berkasId)
            ?: throw TelegramException("Berkas tidak ditemukan")
        val url = telegram.tautanPesan(berkas.chatId, berkas.messageId)
        catatTautan(
            TautanEntity(
                berkasId = berkasId,
                chatId = berkas.chatId,
                messageId = berkas.messageId,
                url = url,
                jenis = JenisTautan.PESAN.name,
                label = berkas.nama,
                dibuatPada = System.currentTimeMillis(),
                dicabut = false,
            ),
        )
        return url
    }

    suspend fun buatTautanUndangan(chatId: Long): String {
        val koleksi = koleksiDao.ambil(chatId)
        val tautan = telegram.buatTautanUndangan(
            chatId = chatId,
            nama = koleksi?.nama ?: "Arsip Senja",
        )
        catatTautan(
            TautanEntity(
                berkasId = null,
                chatId = chatId,
                messageId = 0L,
                url = tautan.inviteLink,
                jenis = JenisTautan.UNDANGAN.name,
                label = koleksi?.nama ?: "Undangan arsip",
                dibuatPada = System.currentTimeMillis(),
                dicabut = tautan.isRevoked,
            ),
        )
        return tautan.inviteLink
    }

    private suspend fun catatTautan(tautan: TautanEntity) {
        if (tautanDao.ambilUrl(tautan.url) == null) {
            tautanDao.simpan(tautan)
        }
    }

    suspend fun cabutTautan(id: Long) {
        val tautan = tautanDao.ambil(id) ?: return
        if (tautan.jenis == JenisTautan.UNDANGAN.name) {
            telegram.cabutTautan(tautan.chatId, tautan.url)
        }
        tautanDao.tandaiDicabut(id)
    }

    suspend fun hapusCatatanTautan(id: Long) {
        tautanDao.hapus(id)
    }

    // ---- Berkas ----

    suspend fun jumlahBerkas(koleksiId: Long): Int = berkasDao.jumlah(koleksiId = koleksiId)

    /** Simpan hasil unggah yang sudah terkonfirmasi server. */
    suspend fun simpanHasilUnggah(entri: BerkasArsipEntity) {
        berkasDao.simpan(entri)
        koleksiDao.hitungUlang(entri.chatId)
    }

    suspend fun hapusBerkas(berkasId: String) {
        berkasDao.hapus(berkasId)
    }

    companion object {
        const val HALAMAN_RIWAYAT = 100
        const val BATAS_HALAMAN = 5

        /** Tag bersama untuk semua pekerjaan unggah (dipantau layar Unggah). */
        const val TAG_UNGGAH = "antrean-unggah"

        /** Nama kerja unik unduhan satu berkas. */
        fun namaKerjaUnduh(berkasId: String) = "unduh-$berkasId"
    }
}
