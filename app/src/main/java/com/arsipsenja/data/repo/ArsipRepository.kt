package com.arsipsenja.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.arsipsenja.data.local.ArsipDatabase
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.JenisTautan
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KueriTampilan
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.local.StatistikBerkas
import com.arsipsenja.data.local.TautanEntity
import com.arsipsenja.data.telegram.TelegramClient
import com.arsipsenja.data.telegram.TelegramException
import com.arsipsenja.data.telegram.keBerkasMentah
import com.arsipsenja.data.telegram.keEntity
import com.arsipsenja.data.work.DownloadWorker
import com.arsipsenja.data.work.UploadWorker
import com.arsipsenja.util.TipeFile
import android.provider.OpenableColumns
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Sumber kebenaran aplikasi: Room sebagai indeks lokal, TDLib sebagai sumber
 * data sesungguhnya. Semua operasi tingkat tinggi lewat sini.
 *
 * Koleksi ada dua macam: channel lama (topicId = 0, kompatibel ke belakang)
 * dan topik forum di dalam grup vault (topicId != 0).
 */
class ArsipRepository(
    private val context: Context,
    private val db: ArsipDatabase,
    val telegram: TelegramClient,
    private val pengaturan: PengaturanStore,
    private val workManager: WorkManager,
) {

    private val tag = "ArsipRepository"
    private val berkasDao = db.berkasDao()
    private val koleksiDao = db.koleksiDao()
    private val tautanDao = db.tautanDao()

    // ---- Aliran data ----

    fun alirBerkas(kueri: KueriTampilan): Flow<List<BerkasArsipEntity>> =
        berkasDao.alirDaftar(kueri.bangun().keSupportQuery())

    fun alirStatistik(): Flow<StatistikBerkas> = berkasDao.alirStatistik()

    fun alirKoleksi(): Flow<List<KoleksiEntity>> = koleksiDao.alirSemua()

    fun alirKoleksi(kunci: KunciKoleksi): Flow<KoleksiEntity?> =
        koleksiDao.alir(kunci.chatId, kunci.topicId)

    fun alirTautan(): Flow<List<TautanEntity>> = tautanDao.alirSemua()

    fun alirTautanBerkas(berkasId: String): Flow<List<TautanEntity>> =
        tautanDao.alirUntukBerkas(berkasId)

    fun alirBerkasId(id: String): Flow<BerkasArsipEntity?> = berkasDao.alirId(id)

    suspend fun ambilBerkas(id: String): BerkasArsipEntity? = berkasDao.ambilId(id)

    suspend fun tampilanAwal(): KueriTampilan = KueriTampilan(
        urut = pengaturan.urutBerkas.first(),
        arah = pengaturan.arahUrut.first(),
    )

    // ---- Vault ----

    /** Kandidat grup vault untuk dipilih pengguna di Pengaturan. */
    data class KandidatVault(
        val chatId: Long,
        val nama: String,
        /** True bila grup sudah mode forum (topik bisa dipakai langsung). */
        val forumSiap: Boolean,
    )

    /** Daftar supergroup (bukan channel); tiap grup dites dukung forum atau tidak. */
    suspend fun daftarKandidatVault(): List<KandidatVault> {
        val grup = telegram.daftarSupergroup()
        return grup.map { chat ->
            val siap = try {
                telegram.daftarTopik(chat.id)
                true
            } catch (e: Exception) {
                Log.i(tag, "Grup ${chat.id} belum mode forum: ${e.message}")
                false
            }
            KandidatVault(
                chatId = chat.id,
                nama = chat.title.orEmpty().ifBlank { "Grup ${chat.id}" },
                forumSiap = siap,
            )
        }
    }

    suspend fun vaultTerpilih(): Long? = pengaturan.vaultSekarang()

    /**
     * Pilih vault baru. Grup lama yang belum mode forum diaktifkan otomatis
     * (membutuhkan hak pemilik grup) agar topik bisa dibuat.
     *
     * @return true bila mode forum baru saja diaktifkan untuk grup ini.
     * @throws TelegramException bila aktivasi forum gagal.
     */
    suspend fun pilihVault(chatId: Long): Boolean {
        val perluForum = try {
            telegram.daftarTopik(chatId)
            false
        } catch (e: TelegramException) {
            if (!bukanForum(e)) throw e
            telegram.aktifkanForum(chatId)
            true
        }
        pengaturan.setVaultChatId(chatId)
        return perluForum
    }

    /** True bila galat TDLib berarti "chat ini bukan forum". */
    private fun bukanForum(e: TelegramException): Boolean =
        e.pesan.contains("forum", ignoreCase = true) ||
            e.pesan.contains("CHAT_NOT_FORUM", ignoreCase = true)

    /** Buat grup vault forum baru dan langsung jadikan vault aktif. */
    suspend fun buatVaultBaru(nama: String = NAMA_VAULT_BAWAAN): Long {
        val chat = telegram.buatVault(nama)
        pengaturan.setVaultChatId(chat.id)
        return chat.id
    }

    /**
     * Kembalikan vault aktif; buat otomatis bernama "Arsip Senja" bila
     * pengguna belum pernah memilih.
     */
    suspend fun pastikanVault(): Long =
        pengaturan.vaultSekarang() ?: buatVaultBaru()

    // ---- Sinkronisasi ----

    /**
     * Sinkronkan semua koleksi yang sudah terdaftar (ambil riwayat terbaru,
     * masukkan berkas baru ke indeks lokal).
     */
    suspend fun sinkronSemua() {
        val koleksi = koleksiDao.alirSemua().first()
        koleksi.forEach { sinkronKoleksi(it.kunci) }
        koleksiDao.hitungUlangSemua()
    }

    /** Sinkron satu koleksi; mengembalikan jumlah berkas baru yang masuk. */
    suspend fun sinkronKoleksi(kunci: KunciKoleksi): Int {
        val baru = tarikRiwayatBaru(kunci)
        koleksiDao.hitungUlang(kunci.chatId, kunci.topicId)
        koleksiDao.ambil(kunci.chatId, kunci.topicId)?.let {
            koleksiDao.simpan(it.copy(terakhirSinkron = System.currentTimeMillis()))
        }
        return baru
    }

    private suspend fun tarikRiwayatBaru(kunci: KunciKoleksi): Int {
        val sudahAda = berkasDao.semuaId().toHashSet()
        var nomor = berkasDao.jumlah(kunci.chatId, kunci.topicId)
        var dari = 0L
        var baru = 0
        val sekarang = System.currentTimeMillis()

        repeat(BATAS_HALAMAN) {
            val pesan = if (kunci.adalahTopik) {
                telegram.riwayatTopik(kunci.chatId, kunci.topicId, dari, HALAMAN_RIWAYAT)
            } else {
                telegram.riwayat(kunci.chatId, dari, HALAMAN_RIWAYAT)
            }
            if (pesan.isEmpty()) return baru

            val mentah = pesan.mapNotNull { it.keBerkasMentah() }
            val entri = mentah.mapNotNull { b ->
                val id = BerkasArsipEntity.kunci(b.chatId, b.messageId)
                if (id in sudahAda) return@mapNotNull null
                sudahAda += id
                nomor += 1
                baru += 1
                b.keEntity(
                    koleksiId = kunci.chatId,
                    topicId = kunci.topicId,
                    nomorKatalog = nomor,
                    sekarang = sekarang,
                )
            }
            berkasDao.simpanSemua(entri)
            dari = pesan.last().id
            if (pesan.size < HALAMAN_RIWAYAT) return baru
        }
        return baru
    }

    /**
     * Tarik daftar koleksi dari Telegram dan petakan ke koleksi lokal.
     * Bila vault sudah dipilih: tarik topik-topik di vault. Bila belum:
     * tarik channel lama (kompatibel ke belakang).
     */
    suspend fun tarikDaftarKoleksi(): List<KoleksiEntity> {
        val vault = pengaturan.vaultSekarang()
        return if (vault != null) tarikTopikVault(vault) else tarikChannelLama()
    }

    private suspend fun tarikTopikVault(vault: Long): List<KoleksiEntity> {
        val sekarang = System.currentTimeMillis()
        val entri = telegram.daftarTopik(vault).map { topik ->
            val lama = koleksiDao.ambil(vault, topik.topicId)
            KoleksiEntity(
                chatId = vault,
                topicId = topik.topicId,
                nama = topik.nama.ifBlank { "Topik ${topik.topicId}" },
                deskripsi = lama?.deskripsi,
                jumlahBerkas = lama?.jumlahBerkas ?: 0,
                dibuatPada = lama?.dibuatPada ?: sekarang,
                terakhirSinkron = lama?.terakhirSinkron ?: 0L,
            )
        }
        koleksiDao.simpanSemua(entri)
        return entri
    }

    private suspend fun tarikChannelLama(): List<KoleksiEntity> {
        val channel = telegram.daftarChannel()
        val sekarang = System.currentTimeMillis()
        val entri = channel.map { chat ->
            val lama = koleksiDao.ambil(chat.id, 0)
            KoleksiEntity(
                chatId = chat.id,
                topicId = 0,
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

    /**
     * Buat koleksi baru sebagai topik forum di vault. Vault dibuat otomatis
     * bila pengguna belum memilih. Topik yang sudah ada dipakai ulang.
     *
     * @param label chip label opsional (maks 24 karakter).
     * @param warna ARGB untuk dot/chip kategori, 0 = warna tema.
     * @param tipe himpunan tipe berkas untuk rute otomatis; kosong = semua tipe.
     */
    suspend fun buatKoleksi(
        nama: String,
        deskripsi: String,
        label: String = "",
        warna: Int = 0,
        tipe: Set<TipeFile> = emptySet(),
    ): KoleksiEntity {
        val vault = pastikanVault()
        val topik = telegram.pastikanTopik(vault, nama.trim())
        val lama = koleksiDao.ambil(vault, topik.topicId)
        val sekarang = System.currentTimeMillis()
        val labelBersih = label.trim().take(24).takeIf { it.isNotBlank() }
        val koleksi = KoleksiEntity(
            chatId = vault,
            topicId = topik.topicId,
            nama = topik.nama,
            deskripsi = deskripsi.trim().takeIf { it.isNotBlank() } ?: lama?.deskripsi,
            label = labelBersih ?: lama?.label,
            warna = warna.takeIf { it != 0 } ?: lama?.warna ?: 0,
            tipeFile = if (tipe.isEmpty()) lama?.tipeFile ?: "" else tipe.joinToString(",") { it.name },
            jumlahBerkas = lama?.jumlahBerkas ?: 0,
            dibuatPada = lama?.dibuatPada ?: sekarang,
            terakhirSinkron = lama?.terakhirSinkron ?: sekarang,
        )
        koleksiDao.simpan(koleksi)
        return koleksi
    }

    /**
     * Ubah metadata koleksi (nama, deskripsi, label, warna, tipe). Untuk
     * topik, nama topik di Telegram ikut diubah.
     */
    suspend fun ubahKoleksi(
        kunci: KunciKoleksi,
        nama: String,
        deskripsi: String,
        label: String = "",
        warna: Int = 0,
        tipe: Set<TipeFile> = emptySet(),
    ) {
        val namaBersih = nama.trim()
        require(namaBersih.isNotEmpty()) { "Nama koleksi kosong." }
        if (kunci.adalahTopik) {
            telegram.ubahTopik(kunci.chatId, kunci.topicId, namaBersih)
        } else {
            telegram.ubahJudul(kunci.chatId, namaBersih)
            telegram.ubahDeskripsi(kunci.chatId, deskripsi)
        }
        val lama = koleksiDao.ambil(kunci.chatId, kunci.topicId) ?: return
        koleksiDao.simpan(
            lama.copy(
                nama = namaBersih,
                deskripsi = deskripsi.trim().ifBlank { null },
                label = label.trim().take(24).takeIf { it.isNotBlank() },
                warna = warna,
                tipeFile = tipe.joinToString(",") { it.name },
            ),
        )
    }

    /**
     * Hapus koleksi dari indeks lokal. Untuk topik, topik forum juga dihapus
     * di Telegram (beserta pesannya). Untuk channel lama, channel hanya
     * ditinggalkan bila [tinggalkanChannel] true.
     */
    suspend fun hapusKoleksi(kunci: KunciKoleksi, tinggalkanChannel: Boolean) {
        if (kunci.adalahTopik) {
            runCatching { telegram.hapusTopik(kunci.chatId, kunci.topicId) }
        } else if (tinggalkanChannel) {
            runCatching { telegram.tinggalkanChannel(kunci.chatId) }
        }
        berkasDao.hapusUntukKoleksi(kunci.chatId, kunci.topicId)
        tautanDao.hapusUntukKoleksi(kunci.chatId, kunci.topicId)
        koleksiDao.hapus(kunci.chatId, kunci.topicId)
    }

    suspend fun koleksiTerpilihAtauPertama(): KunciKoleksi? =
        koleksiDao.alirSemua().first().firstOrNull()?.kunci

    // ---- Unggah ----

    /**
     * Antrekan unggah beberapa berkas SAF ke satu koleksi. Untuk koleksi
     * topik, topik dipastikan ada dulu (dibuat otomatis bila hilang).
     */
    suspend fun mulaiUnggah(uris: List<Uri>, kunci: KunciKoleksi) {
        val kunciFinal = if (kunci.adalahTopik) pastikanTopikKoleksi(kunci) else kunci
        uris.forEach { uri ->
            val pekerjaan = "unggah-${UUID.randomUUID()}"
            val permintaan = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(
                    workDataOf(
                        UploadWorker.K_URI to uri.toString(),
                        UploadWorker.K_KOLEKSI to kunciFinal.chatId,
                        UploadWorker.K_TOPIK to kunciFinal.topicId,
                    ),
                )
                .addTag(pekerjaan)
                .addTag(TAG_UNGGAH)
                .build()
            workManager.enqueueUniqueWork(pekerjaan, ExistingWorkPolicy.APPEND_OR_REPLACE, permintaan)
        }
    }

    /**
     * Antrekan unggah dengan rute otomatis: tiap berkas dideteksi tipenya,
     * lalu dikirim ke kategori yang menangani tipe tersebut. Kategori yang
     * paling spesifik (menangani tipe paling sedikit) dipilih bila beberapa
     * cocok; kategori "semua tipe" dipakai bila tidak ada yang cocok.
     *
     * @return peta kunci koleksi → jumlah berkas yang dirutekan ke sana.
     */
    suspend fun unggahOtomatis(uris: List<Uri>): Map<KunciKoleksi, Int> {
        require(uris.isNotEmpty()) { "Belum ada berkas yang dipilih." }
        val daftar = koleksiDao.alirSemua().first()
        require(daftar.isNotEmpty()) { "Buat kategori dulu di Kelola koleksi." }
        val perKunci = mutableMapOf<KunciKoleksi, MutableList<Uri>>()
        uris.forEach { uri ->
            val target = pilihKategoriUntuk(deteksiTipe(uri), daftar)
            perKunci.getOrPut(target.kunci) { mutableListOf() } += uri
        }
        perKunci.forEach { (kunci, daftarUri) -> mulaiUnggah(daftarUri, kunci) }
        return perKunci.mapValues { it.value.size }
    }

    /**
     * Periksa semua kategori topik dan buat ulang topik yang hilang di
     * Telegram (mis. dihapus dari luar aplikasi).
     *
     * @return jumlah topik yang dibuat ulang.
     */
    suspend fun perbaikiTopikHilang(): Int {
        val daftar = koleksiDao.alirSemua().first().filter { it.adalahTopik }
        var diperbaiki = 0
        daftar.forEach { k ->
            val awal = k.kunci
            if (pastikanTopikKoleksi(awal) != awal) diperbaiki++
        }
        return diperbaiki
    }

    /** Pilih kategori tujuan untuk [tipe]: yang paling spesifik menang. */
    private fun pilihKategoriUntuk(tipe: TipeFile, daftar: List<KoleksiEntity>): KoleksiEntity =
        daftar.filter { tipe in it.tipeDitangani }
            .minByOrNull { it.tipeDitangani.size }
            ?: daftar.first()

    /** Deteksi tipe berkas dari MIME + nama berkas SAF. */
    private fun deteksiTipe(uri: Uri): TipeFile {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        val nama = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        return TipeFile.dariMime(mime, nama)
    }

    /**
     * Pastikan topik koleksi masih ada di Telegram (buat ulang otomatis bila
     * dihapus dari luar aplikasi); kembalikan kunci terkini.
     */
    private suspend fun pastikanTopikKoleksi(kunci: KunciKoleksi): KunciKoleksi {
        val lokal = koleksiDao.ambil(kunci.chatId, kunci.topicId) ?: return kunci
        val topik = telegram.pastikanTopik(kunci.chatId, lokal.nama)
        if (topik.topicId != kunci.topicId) {
            val baru = lokal.copy(
                chatId = topik.chatId,
                topicId = topik.topicId,
                nama = topik.nama,
            )
            koleksiDao.simpan(baru)
            return baru.kunci
        }
        return kunci
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

    // ---- Thumbnail ----

    /**
     * Pastikan thumbnail berkas tersedia lokal. Mengembalikan jalur berkas
     * thumbnail, atau null bila pesan tak punya thumbnail / unduh gagal.
     *
     * Urutan: pakai thumbPath yang sudah ada → unduh file thumbnail TDLib
     * ke filesDir/thumbs → (khusus foto) pakai berkas foto lokal bila ada.
     */
    suspend fun pastikanThumbnail(berkas: BerkasArsipEntity): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            berkas.thumbPath?.let { lama ->
                if (lama.isNotBlank() && java.io.File(lama).exists()) return@withContext lama
            }
            if (berkas.tdThumbFileId != 0) {
                val diunduh = runCatching {
                    val f = telegram.unduh(berkas.tdThumbFileId)
                    f.local?.path?.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
                        ?.takeIf { it.exists() }
                }.getOrNull()
                if (diunduh != null) {
                    val tujuan = java.io.File(direktoriThumb(), "${berkas.id.replace(':', '_')}.jpg")
                    val disalin = runCatching {
                        diunduh.copyTo(tujuan, overwrite = true)
                        tujuan.absolutePath
                    }.getOrNull()
                    if (disalin != null) {
                        berkasDao.aturThumb(berkas.id, disalin)
                        return@withContext disalin
                    }
                }
            }
            // Foto: berkas foto lokal sendiri cukup sebagai pratinjau.
            if (berkas.tipe == com.arsipsenja.util.TipeFile.GAMBAR.name) {
                berkas.tdLocalPath?.takeIf { it.isNotBlank() }?.let { p ->
                    if (java.io.File(p).exists()) {
                        berkasDao.aturThumb(berkas.id, p)
                        return@withContext p
                    }
                }
            }
            return@withContext null
        }

    private fun direktoriThumb(): java.io.File =
        java.io.File(context.filesDir, "thumbs").also { if (!it.exists()) it.mkdirs() }

    // ---- Tautan ----

    /** Buat tautan pesan untuk berkas, lalu catat di indeks tautan. */
    suspend fun buatTautanPesan(berkasId: String): String {
        val berkas = berkasDao.ambilId(berkasId)
            ?: throw TelegramException("Berkas tidak ditemukan")
        val url = telegram.tautanPesan(
            chatId = berkas.chatId,
            messageId = berkas.messageId,
            dalamThread = berkas.topicId != 0,
        )
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
        val koleksi = koleksiDao.ambil(chatId, 0)
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

    suspend fun jumlahBerkas(kunci: KunciKoleksi): Int =
        berkasDao.jumlah(kunci.chatId, kunci.topicId)

    /** Simpan hasil unggah yang sudah terkonfirmasi server. */
    suspend fun simpanHasilUnggah(entri: BerkasArsipEntity) {
        berkasDao.simpan(entri)
        val kunci = KunciKoleksi(entri.koleksiId ?: entri.chatId, entri.topicId)
        koleksiDao.hitungUlang(kunci.chatId, kunci.topicId)
    }

    suspend fun hapusBerkas(berkasId: String) {
        berkasDao.hapus(berkasId)
    }

    companion object {
        const val HALAMAN_RIWAYAT = 100
        const val BATAS_HALAMAN = 5

        /** Nama bawaan grup vault yang dibuat otomatis. */
        const val NAMA_VAULT_BAWAAN = "Arsip Senja"

        /** Tag bersama untuk semua pekerjaan unggah (dipantau layar Unggah). */
        const val TAG_UNGGAH = "antrean-unggah"

        /** Nama kerja unik unduhan satu berkas. */
        fun namaKerjaUnduh(berkasId: String) = "unduh-$berkasId"
    }
}
