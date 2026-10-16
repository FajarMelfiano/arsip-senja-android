package com.arsipsenja.data.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.arsipsenja.data.local.ArsipDatabase
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.JenisTautan
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KueriTampilan
import com.arsipsenja.data.local.KunciBaru
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.KunciRahasia
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.local.PenandaImpor
import com.arsipsenja.data.local.UploadQueuePauseEntity
import com.arsipsenja.data.local.StatistikBerkas
import com.arsipsenja.data.local.TautanEntity
import com.arsipsenja.data.local.TipeKunci
import com.arsipsenja.data.telegram.TelegramClient
import com.arsipsenja.data.telegram.TelegramException
import com.arsipsenja.data.telegram.BerkasMentah
import com.arsipsenja.data.telegram.keBerkasMentah
import com.arsipsenja.data.telegram.keEntity
import com.arsipsenja.data.work.DownloadWorker
import com.arsipsenja.data.work.UploadWorker
import com.arsipsenja.util.TipeFile
import com.arsipsenja.util.captionTanpaHeader
import android.provider.OpenableColumns
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

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
    private val pauseDao = db.uploadQueuePauseDao()
    private val koleksiDao = db.koleksiDao()
    private val tautanDao = db.tautanDao()

    // ---- Aliran data ----

    fun alirBerkas(kueri: KueriTampilan): Flow<List<BerkasArsipEntity>> =
        berkasDao.alirDaftar(kueri.bangun().keSupportQuery())

    fun alirStatistik(): Flow<StatistikBerkas> = berkasDao.alirStatistik()

    /** Statistik khusus koleksi pribadi (tanpa hasil tarik-impor). */
    fun alirStatistikPribadi(): Flow<StatistikBerkas> = berkasDao.alirStatistikPribadi()

    fun alirKoleksi(): Flow<List<KoleksiEntity>> = koleksiDao.alirSemua()

    /**
     * Koleksi pribadi: sumber Dasbor, Pohon, Unggah, Tautan.
     * Hasil tarik-impor tidak pernah masuk aliran ini.
     */
    fun alirKoleksiPribadi(): Flow<List<KoleksiEntity>> = koleksiDao.alirPribadi()

    /** Koleksi hasil "Tarik dari Telegram": hanya untuk tab Impor. */
    fun alirKoleksiImpor(): Flow<List<KoleksiEntity>> = koleksiDao.alirImpor()

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

    /**
     * Daftar supergroup (bukan channel) milik sendiri; tiap grup dites
     * dukung forum atau tidak.
     *
     * Gabungkan dua sumber seperti deteksi otomatis: GetChats (100 chat
     * teratas) + SearchChats nama vault bawaan — grup lama yang sudah lama
     * tidak aktif tidak masuk 100 teratas GetChats, tetapi SearchChats
     * menjangkau seluruh daftar chat. Hasil SearchChats disaring
     * kepemilikannya juga (fail-closed) agar grup orang lain tidak muncul.
     */
    suspend fun daftarKandidatVault(): List<KandidatVault> {
        val dariPencarian = runCatching { telegram.cariChat(NAMA_VAULT_BAWAAN) }
            .onFailure { Log.i(tag, "SearchChats gagal, lanjut dari GetChats: ${it.message}") }
            .getOrDefault(emptyList())
            // Hasil SearchChats juga wajib milik sendiri (fail-closed):
            // tanpa saringan ini grup orang lain bisa teradopsi otomatis.
            .filter { chat ->
                runCatching { telegram.apakahPemilik(chat.id) }.getOrDefault(false)
            }
        val grup = (telegram.daftarSupergroup() + dariPencarian)
            .distinctBy { it.id }
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
     * Pilih vault baru. Hanya supergroup (bukan channel) yang diterima;
     * grup lama yang belum mode forum diaktifkan otomatis (membutuhkan hak
     * pemilik grup) agar topik bisa dibuat.
     *
     * @return true bila mode forum baru saja diaktifkan untuk grup ini.
     * @throws TelegramException bila tipe chat salah atau aktivasi gagal.
     */
    suspend fun pilihVault(chatId: Long): Boolean {
        val chatLama = runCatching { telegram.ambilChat(chatId) }.getOrNull()
        if (chatLama != null && !telegram.adalahVaultSah(chatLama)) {
            throw TelegramException(
                PenjagaVault.pesanVaultBukanSupergroup(telegram.labelTipeChat(chatLama)) +
                    " Pilih grup lain dari daftar.",
            )
        }
        val perluForum = try {
            telegram.daftarTopik(chatId)
            false
        } catch (e: TelegramException) {
            if (!PenjagaVault.galatBukanForum(e.pesan)) throw e
            try {
                telegram.aktifkanForum(chatId)
            } catch (gagal: TelegramException) {
                throw TelegramException(
                    if (PenjagaVault.galatHanyaSupergroup(gagal.pesan)) {
                        PenjagaVault.pesanVaultBukanSupergroup(labelVaultTerbaik(chatLama, chatId)) +
                            " Pilih grup lain dari daftar."
                    } else {
                        PenjagaVault.pesanAktivasiGagal(gagal.pesan)
                    },
                    gagal.kode,
                    gagal,
                )
            }
            true
        }
        pengaturan.setVaultChatId(chatId)
        return perluForum
    }

    /**
     * Label tipe chat untuk pesan galat: pakai info yang sudah ada bila
     * tersedia, bila tidak ambil ulang via GetChat, bila gagal tulis
     * "tak diketahui".
     */
    private suspend fun labelVaultTerbaik(chat: TdApi.Chat?, chatId: Long): String {
        if (chat != null) return telegram.labelTipeChat(chat)
        return runCatching { telegram.labelTipeChat(telegram.ambilChat(chatId)) }
            .getOrDefault("tak diketahui")
    }

    /** Buat grup vault forum baru dan langsung jadikan vault aktif. */
    suspend fun buatVaultBaru(nama: String = NAMA_VAULT_BAWAAN): HasilBuatVault {
        // Dedupe: jangan buat duplikat bila grup milik sendiri forum-siap
        // dengan nama yang sama sudah ada — pakai lagi grup itu.
        // Bila deteksi gagal (mis. jaringan), jatuh ke pembuatan baru
        // seperti perilaku lama.
        val lama = runCatching { deteksiVaultMilikSendiri() }
            .getOrNull()
            ?.let { PenjagaVault.cariNamaSama(it.keCalon(), nama) }
        if (lama != null) {
            pengaturan.setVaultChatId(lama.chatId)
            return HasilBuatVault(lama.chatId, dibuatBaru = false, nama = lama.nama)
        }
        val chat = telegram.buatVault(nama)
        pengaturan.setVaultChatId(chat.id)
        return HasilBuatVault(
            chatId = chat.id,
            dibuatBaru = true,
            nama = chat.title.orEmpty().ifBlank { nama.trim() },
        )
    }

    /** Hasil pembuatan vault: dibuat baru vs vault lama yang dipakai lagi. */
    data class HasilBuatVault(
        val chatId: Long,
        val dibuatBaru: Boolean,
        val nama: String,
    )

    /**
     * Kembalikan vault aktif; bila pengguna belum pernah memilih, pakai
     * dulu vault sehat milik sendiri yang sudah ada (aturan nama-cocok /
     * satu-satunya) dan hanya buat grup baru bila tidak ada kandidat.
     */
    suspend fun pastikanVault(): Long {
        pengaturan.vaultSekarang()?.let { return it }
        val adopsi = runCatching { deteksiVaultMilikSendiri() }
            .getOrNull()
            ?.let { PenjagaVault.pilihAdopsi(it.keCalon(), NAMA_VAULT_BAWAAN) }
        if (adopsi != null) {
            pengaturan.setVaultChatId(adopsi.chatId)
            return adopsi.chatId
        }
        return buatVaultBaru().chatId
    }

    /**
     * Daftar supergroup milik sendiri yang forum-siap (topik bisa dibaca).
     * Gabungkan dua sumber: GetChats (100 chat teratas) + SearchChats nama
     * vault bawaan — grup lama yang sudah lama tidak aktif tidak masuk 100
     * teratas GetChats, tetapi SearchChats menjangkau seluruh daftar chat.
     */
    suspend fun deteksiVaultMilikSendiri(): List<KandidatVault> {
        // Sumber kedua tidak boleh mematikan sumber pertama: bila
        // SearchChats gagal, deteksi tetap jalan dari GetChats.
        val dariPencarian = runCatching { telegram.cariChat(NAMA_VAULT_BAWAAN) }
            .onFailure { Log.i(tag, "SearchChats gagal, lanjut dari GetChats: ${it.message}") }
            .getOrDefault(emptyList())
            // Hasil SearchChats juga wajib milik sendiri (fail-closed):
            // tanpa saringan ini grup orang lain bisa teradopsi otomatis.
            .filter { chat ->
                runCatching { telegram.apakahPemilik(chat.id) }.getOrDefault(false)
            }
        val gabungan = (telegram.daftarSupergroup() + dariPencarian)
            .distinctBy { it.id }
        return gabungan.mapNotNull { chat ->
            try {
                telegram.daftarTopik(chat.id)
                KandidatVault(
                    chatId = chat.id,
                    nama = chat.title.orEmpty().ifBlank { "Grup ${chat.id}" },
                    forumSiap = true,
                )
            } catch (e: Exception) {
                Log.i(tag, "Grup ${chat.id} belum forum-siap, dilewati deteksi: ${e.message}")
                null
            }
        }
    }

    /** Hasil pemeriksaan vault saat refresh Pengaturan. */
    sealed interface HasilPeriksaVault {
        /** Vault sah / tidak ada vault / adopsi diblokir: tidak ada aksi. */
        data object TidakAdaAksi : HasilPeriksaVault

        /** Vault rusak diganti otomatis dengan vault sehat milik sendiri. */
        data class Diadopsi(val kandidat: KandidatVault) : HasilPeriksaVault

        /** Beberapa kandidat siap pakai tanpa kecocokan nama: jangan menebak. */
        data class BanyakKandidat(val jumlah: Int) : HasilPeriksaVault
    }

    /**
     * Periksa vault tersimpan dan adopsikan vault sehat milik sendiri bila
     * vault lama TIDAK SAH (mis. channel) dan tidak dipakai koleksi
     * bertopik. Dipanggil saat Pengaturan dibuka. GetChat yang gagal
     * dilempar agar ViewModel menampilkan "Gagal memeriksa..." dan
     * mempertahankan info lama, bukan menimpa dengan "Bermasalah".
     */
    suspend fun periksaDanAdopsiVault(): HasilPeriksaVault {
        val vault = pengaturan.vaultSekarang()
            ?: return HasilPeriksaVault.TidakAdaAksi
        val chat = telegram.ambilChat(vault)
        if (telegram.adalahVaultSah(chat)) return HasilPeriksaVault.TidakAdaAksi
        val topicIdDipakai = koleksiDao.alirSemua().first()
            .filter { it.chatId == vault }
            .map { it.topicId }
        if (!PenjagaVault.bolehAdopsiVaultLama(topicIdDipakai)) {
            return HasilPeriksaVault.TidakAdaAksi
        }
        val calon = deteksiVaultMilikSendiri()
        val adopsi = PenjagaVault.pilihAdopsi(calon.keCalon(), NAMA_VAULT_BAWAAN)
        if (adopsi != null) {
            pengaturan.setVaultChatId(adopsi.chatId)
            return HasilPeriksaVault.Diadopsi(
                KandidatVault(adopsi.chatId, adopsi.nama, forumSiap = true),
            )
        }
        if (calon.size > 1) return HasilPeriksaVault.BanyakKandidat(calon.size)
        return HasilPeriksaVault.TidakAdaAksi
    }

    /** Ubah daftar kandidat menjadi calon murni untuk [PenjagaVault]. */
    private fun List<KandidatVault>.keCalon(): List<PenjagaVault.CalonVault> =
        map { PenjagaVault.CalonVault(it.chatId, it.nama) }

    /** Ringkasan kesehatan vault aktif untuk layar Pengaturan. */
    data class InfoVault(
        val chatId: Long?,
        val nama: String,
        /** True bila grup sah + topik forum bisa dibaca. */
        val forumSiap: Boolean,
        /** Penjelasan singkat bila tidak siap; null bila siap. */
        val masalah: String?,
    )

    /**
     * Baca nama grup vault (via GetChat, fallback ke ID mentah) + status
     * kesehatan (tipe supergroup + coba daftarTopik, seperti [periksaVault]).
     */
    suspend fun infoVaultAktif(): InfoVault {
        val vault = pengaturan.vaultSekarang()
            ?: return InfoVault(null, "Belum dipilih", false, "Belum ada vault yang dipilih.")
        val chat = runCatching { telegram.ambilChat(vault) }.getOrNull()
        val nama = chat?.title?.ifBlank { null } ?: "Grup $vault"
        if (chat != null && !telegram.adalahVaultSah(chat)) {
            return InfoVault(
                vault, nama, false,
                "Vault bertipe ${telegram.labelTipeChat(chat)}, bukan grup.",
            )
        }
        // Retry terbatas bila forum belum efektif: grup forum yang baru
        // dibuat bisa butuh sedetik sebelum GetForumTopics mengenalinya.
        // Hanya untuk galat bukan-forum, maks 2x ulang jeda 1,5 dtk;
        // vault sehat lolos percobaan pertama tanpa penundaan.
        var percobaan = 0
        while (true) {
            try {
                telegram.daftarTopik(vault)
                return InfoVault(vault, nama, true, null)
            } catch (e: Exception) {
                val mentah = e.message ?: "galat tak dikenal"
                if (PenjagaVault.galatBukanForum(mentah) && percobaan < 2) {
                    percobaan++
                    delay(1500)
                    continue
                }
                // Pesan status konteks Pengaturan (tanpa klaim "setelah aktivasi",
                // karena di sini tidak ada aktivasi yang dicoba).
                val masalah = when {
                    PenjagaVault.galatHanyaSupergroup(mentah) ->
                        PenjagaVault.pesanVaultBukanSupergroup("tak diketahui")
                    PenjagaVault.galatBukanForum(mentah) -> "Forum belum aktif di grup ini."
                    else -> mentah
                }
                return InfoVault(vault, nama, false, masalah)
            }
        }
    }

    /**
     * Perbaiki vault aktif satu ketukan: validasi tipe + aktivasi forum
     * (jalur [pilihVault]), lalu kembalikan info kesehatan terbaru.
     */
    suspend fun perbaikiVaultAktif(): InfoVault {
        val vault = pengaturan.vaultSekarang()
            ?: throw TelegramException("Belum ada vault yang dipilih.")
        pilihVault(vault)
        return infoVaultAktif()
    }

    // ---- Sinkronisasi ----

    /**
     * Sinkronkan semua koleksi PRIBADI yang sudah terdaftar (ambil riwayat
     * terbaru, masukkan berkas baru ke indeks lokal). Koleksi impor
     * disinkron dari tab Impor via [sinkronImpor], bukan dari sini.
     * Dipanggil manual dari UI — tombol Sinkron tetap ada sebagai
     * fallback di samping mesin realtime otomatis.
     */
    suspend fun sinkronSemua() {
        kunciSinkron.withLock {
            _statusRealtime.update { it.copy(menyinkronkan = true) }
            try {
                val koleksi = koleksiDao.ambilSemuaPribadi()
                koleksi.forEach { sinkronKoleksi(it.kunci) }
                koleksiDao.hitungUlangSemua()
            } finally {
                _statusRealtime.update { it.copy(menyinkronkan = false) }
            }
        }
    }

    /** Sinkronkan semua koleksi impor (dipanggil dari tab Impor). */
    suspend fun sinkronImpor(): Int {
        kunciSinkron.withLock {
            _statusRealtime.update { it.copy(menyinkronkan = true) }
            try {
                val koleksi = koleksiDao.ambilSemuaImpor()
                var baru = 0
                koleksi.forEach { baru += sinkronKoleksi(it.kunci) }
                return baru
            } finally {
                _statusRealtime.update { it.copy(menyinkronkan = false) }
            }
        }
    }

    /** Sinkron satu koleksi; mengembalikan jumlah berkas baru yang masuk. */
    suspend fun sinkronKoleksi(kunci: KunciKoleksi): Int {
        val baru = tarikRiwayatBaru(kunci)
        koleksiDao.hitungUlang(kunci.chatId, kunci.topicId)
        koleksiDao.ambil(kunci.chatId, kunci.topicId)?.let {
            koleksiDao.simpan(it.copy(terakhirSinkron = System.currentTimeMillis()))
        }
        catatSinkron()
        return baru
    }

    private suspend fun tarikRiwayatBaru(kunci: KunciKoleksi): Int {
        // High-water mark menggantikan `semuaId()` yang menyedot seluruh tabel
        // setiap penyapuan. Telegram memberi message id yang selalu menaik per
        // chat, jadi apa pun yang lebih baru dari nilai ini pasti belum pernah
        // diindeks — dan untuk vault yang sama hasilnya 0, bukan daftar
        // puluhan ribu id yang dibuang lagi.
        val batasBawah = berkasDao.messageIdTertinggi(kunci.chatId, kunci.topicId) ?: 0L
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

            val mentah = pesan.filter { it.id > batasBawah }.mapNotNull { it.keBerkasMentah() }
            val entri = mentah.map { b ->
                val bSegar = pastikanUkuran(b)
                nomor += 1
                baru += 1
                bSegar.keEntity(
                    koleksiId = kunci.chatId,
                    topicId = kunci.topicId,
                    nomorKatalog = nomor,
                    sekarang = sekarang,
                )
            }
            if (entri.isNotEmpty()) berkasDao.simpanSemua(entri)
            dari = pesan.last().id
            if (pesan.size < HALAMAN_RIWAYAT) return baru
        }
        return baru
    }

    /**
     * Selaraskan indeks lokal dengan isi sebenarnya di Telegram untuk satu
     * koleksi: apa pun yang hilang dari vault ditandai, bukan dibiarkan
     * menyesatkan.
     *
     * ## Mengapa ini diperlukan
     *
     * Sinkron lama bersifat **tambah-saja**. Kalau sebuah pesan dihapus dari
     * channel di aplikasi Telegram — atau dari perangkat lain, atau karena
     * pesan itu dihapus automatik setelah 30 hari di channel non-kategori —
     * barisnya tetap ada di Room selamanya. Katalog menampilkan berkas yang
     * tidak ada, dan mengunduhnya akan mencoba ulang tanpa henti.
     *
     * Pendekatan **tandai-lalu-sapu**: seluruh baris koleksi ditandai "perlu
     * dicek" (1), setiap pesan yang benar-benar ditemukan mengembalikan
     * statusnya ke 0, dan sisa yang bernilai 1 memang tidak ada lagi.
     * Biayanya O(jumlah pesan), bukan O(jumlah baris arsip) — tidak perlu
     * daftar `IN` berisi puluhan ribu id.
     *
     * Baris yang hilang **tidak dihapus**: berkas salinan lokal pengguna harus
     * tetap bisa ditemukan, dan keputusan menghapusnya adalah hak pengguna.
     *
     * @return jumlah baris yang ditandai hilang.
     */
    suspend fun selaraskanKoleksi(kunci: KunciKoleksi): Int {
        val ditandai = berkasDao.tandaiPerluDicek(kunci.chatId, kunci.topicId)
        if (ditandai == 0) return 0

        // Telusuri seluruh riwayat — bukan hanya yang lebih baru — karena
        // tujuan justru memeriksa berkas lama.
        var dari = 0L
        repeat(BATAS_HALAMAN_SELARAS) {
            val pesan = if (kunci.adalahTopik) {
                telegram.riwayatTopik(kunci.chatId, kunci.topicId, dari, HALAMAN_RIWAYAT)
            } else {
                telegram.riwayat(kunci.chatId, dari, HALAMAN_RIWAYAT)
            }
            if (pesan.isEmpty()) return@repeat
            for (p in pesan) {
                berkasDao.tandaiMasihAda(BerkasArsipEntity.kunci(p.chatId, p.id))
            }
            dari = pesan.last().id
            if (pesan.size < HALAMAN_RIWAYAT) return@repeat
        }
        return berkasDao.jumlahHilang(kunci.chatId, kunci.topicId)
    }

    /** Selaraskan seluruh koleksi pribadi; mengembalikan total berkas hilang. */
    suspend fun selaraskanSemua(): Int = kunciSinkron.withLock {
        var total = 0
        koleksiDao.ambilSemuaPribadi().forEach { total += selaraskanKoleksi(it.kunci) }
        total
    }

    /**
     * Tarik daftar koleksi impor dari Telegram — hasilnya masuk TAB IMPOR,
     * bukan daftar pribadi.
     *
     * Sumber gabungan: vault aktif + semua vault forum-siap milik sendiri
     * (dedupe per chatId) + channel lama. Tiap koleksi yang ditemukan
     * langsung disinkronkan riwayatnya agar jumlah berkas langsung nyata.
     * Satu sumber yang gagal tidak membatalkan sumber lain (isolasi
     * per-sumber via runCatching).
     *
     * Anti-duplikat: hasil tarikan yang kuncinya SUDAH dikenal sebagai
     * koleksi pribadi dilewati — pribadi tidak disentuh, tidak ditimpa,
     * tidak digandakan ([PenandaImpor.bolehJadiImpor]).
     *
     * @return hanya koleksi bertanda impor.
     */
    suspend fun tarikDaftarKoleksi(): List<KoleksiEntity> {
        val daftarVault = mutableSetOf<Long>()
        pengaturan.vaultSekarang()?.let { daftarVault += it }
        runCatching { deteksiVaultMilikSendiri() }
            .onFailure { Log.i(tag, "Deteksi vault untuk tarik gagal: ${it.message}") }
            .getOrNull()
            ?.forEach { daftarVault += it.chatId }
        runCatching { daftarKandidatVault() }
            .onFailure { Log.i(tag, "Kandidat vault untuk tarik gagal: ${it.message}") }
            .getOrNull()
            ?.filter { it.forumSiap }
            ?.forEach { daftarVault += it.chatId }
        val semua = mutableListOf<KoleksiEntity>()
        daftarVault.forEach { vault ->
            runCatching { tarikTopikVault(vault, sebagaiImpor = true) }
                .onSuccess { semua += it }
                .onFailure { Log.i(tag, "Tarik topik vault $vault gagal: ${it.message}") }
        }
        runCatching { tarikChannelLama(sebagaiImpor = true) }
            .onSuccess { semua += it }
            .onFailure { Log.i(tag, "Tarik channel lama gagal: ${it.message}") }
        val unik = semua.distinctBy { it.kunci }
        unik.forEach { koleksi ->
            runCatching { sinkronKoleksi(koleksi.kunci) }
                .onFailure { Log.i(tag, "Sinkron koleksi ${koleksi.kunci} gagal: ${it.message}") }
        }
        return unik
    }

    /**
     * Tarik topik-topik di satu vault. Bila [sebagaiImpor] true, entri
     * ditandai impor dan yang sudah dikenal pribadi dilewati.
     */
    private suspend fun tarikTopikVault(vault: Long, sebagaiImpor: Boolean = false): List<KoleksiEntity> {
        val sekarang = System.currentTimeMillis()
        val entri = telegram.daftarTopik(vault).mapNotNull { topik ->
            val lama = koleksiDao.ambil(vault, topik.topicId)
            if (sebagaiImpor && !PenandaImpor.bolehJadiImpor(lama)) return@mapNotNull null
            val dasar = KoleksiEntity(
                chatId = vault,
                topicId = topik.topicId,
                nama = topik.nama.ifBlank { "Topik ${topik.topicId}" },
                deskripsi = lama?.deskripsi,
                // Pertahankan SEMUA metadata lokal saat rebuild — rebuild
                // hanya menyegarkan nama dari Telegram, bukan menimpa
                // kustomisasi pengguna (label, warna, tipe, kunci).
                label = lama?.label,
                warna = lama?.warna ?: 0,
                tipeFile = lama?.tipeFile ?: "",
                kunciTipe = lama?.kunciTipe ?: TipeKunci.TIDAK_ADA.name,
                kunciGaram = lama?.kunciGaram,
                kunciHash = lama?.kunciHash,
                kunciBiometrik = lama?.kunciBiometrik ?: 0,
                jumlahBerkas = lama?.jumlahBerkas ?: 0,
                dibuatPada = lama?.dibuatPada ?: sekarang,
                terakhirSinkron = lama?.terakhirSinkron ?: 0L,
            )
            if (sebagaiImpor) PenandaImpor.tandaiImpor(dasar) else dasar
        }
        koleksiDao.simpanSemua(entri)
        return entri
    }

    /** Tarik channel lama. Bila [sebagaiImpor] true, berlaku aturan impor. */
    private suspend fun tarikChannelLama(sebagaiImpor: Boolean = false): List<KoleksiEntity> {
        val channel = telegram.daftarChannel()
        val sekarang = System.currentTimeMillis()
        val entri = channel.mapNotNull { chat ->
            val lama = koleksiDao.ambil(chat.id, 0)
            if (sebagaiImpor && !PenandaImpor.bolehJadiImpor(lama)) return@mapNotNull null
            val dasar = KoleksiEntity(
                chatId = chat.id,
                topicId = 0,
                nama = chat.title.orEmpty().ifBlank { "Koleksi ${chat.id}" },
                deskripsi = lama?.deskripsi,
                label = lama?.label,
                warna = lama?.warna ?: 0,
                tipeFile = lama?.tipeFile ?: "",
                kunciTipe = lama?.kunciTipe ?: TipeKunci.TIDAK_ADA.name,
                kunciGaram = lama?.kunciGaram,
                kunciHash = lama?.kunciHash,
                kunciBiometrik = lama?.kunciBiometrik ?: 0,
                jumlahBerkas = lama?.jumlahBerkas ?: 0,
                dibuatPada = lama?.dibuatPada ?: sekarang,
                terakhirSinkron = lama?.terakhirSinkron ?: 0L,
            )
            if (sebagaiImpor) PenandaImpor.tandaiImpor(dasar) else dasar
        }
        koleksiDao.simpanSemua(entri)
        return entri
    }

    // ---- Mesin realtime (event-driven + debounce) ----

    private val _statusRealtime = MutableStateFlow(StatusRealtime())

    /** Status sinkron otomatis untuk UI (indikator + dialog progres). */
    val statusRealtime: StateFlow<StatusRealtime> = _statusRealtime.asStateFlow()

    /**
     * Scope milik mesin realtime: hidup selama proses aplikasi berjalan.
     * SupervisorJob agar satu kolektor yang gagal tidak mematikan yang lain.
     */
    private val lingkupRealtime = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Kunci agar dua sinkron (manual/otomatis) tidak tumpang tindih. */
    private val kunciSinkron = Mutex()

    private val realtimeBerjalan = AtomicBoolean(false)
    private val koleksiImporDikenalRealtime = java.util.concurrent.ConcurrentHashMap.newKeySet<KunciKoleksi>()

    /**
     * Daftarkan satu koleksi impor yang dibuka pengguna agar di-ingest secara
     * realtime (lazy subscribe). Koleksi impor yang belum pernah disentuh
     * pengguna tidak menyedot pemrosesan pesan realtime.
     */
    fun daftarkanKoleksiImporDikenal(kunci: KunciKoleksi) {
        koleksiImporDikenalRealtime.add(kunci)
    }

    fun apakahKoleksiImporDikenalRealtime(kunci: KunciKoleksi): Boolean =
        koleksiImporDikenalRealtime.contains(kunci)

    /**
     * Mulai mesin realtime — idempoten, dipanggil sekali dari
     * [ArsipSenjaApp.onCreate]. Mengumpulkan:
     * - pesan baru Telegram (batch 2 detik, hanya koleksi yang dikenal),
     * - perubahan koneksi (Ready setelah putus → sinkron ringan bila ambang),
     * - perubahan judul chat / info topik (refresh nama lokal saja,
     *   tidak pernah membuat koleksi dari topik asing).
     */
    fun mulaiRealtime() {
        if (!realtimeBerjalan.compareAndSet(false, true)) return
        _statusRealtime.update { it.copy(aktif = true) }
        // Hidrasi cap waktu sinkron terakhir agar bertahan antar restart.
        lingkupRealtime.launch {
            _statusRealtime.update { it.copy(sinkronTerakhir = pengaturan.sinkronTerakhir()) }
        }
        lingkupRealtime.launch { kolektorPesanBatch() }
        lingkupRealtime.launch { kolektorKoneksi() }
        lingkupRealtime.launch { kolektorJudulChat() }
        lingkupRealtime.launch { kolektorInfoTopik() }
        lingkupRealtime.launch { kolektorPenghapusan() }
        lingkupRealtime.launch { kolektorPerubahan() }
        Log.i(tag, "Mesin realtime dimulai (jendela batch ${AturanRealtime.JENDELA_BATCH_MS} ms).")
    }

    /**
     * Dipanggil saat aplikasi kembali foreground (MainActivity.onStart):
     * sinkron ringan sekali bila sinkron terakhir sudah melewati ambang.
     */
    fun saatKembaliForeground() {
        if (!realtimeBerjalan.get()) return
        lingkupRealtime.launch { sinkronRinganOtomatis() }
    }

    /** Kumpulkan pesan baru dalam jendela batch, lalu ingest sekaligus. */
    private suspend fun kolektorPesanBatch() {
        val konteks = currentCoroutineContext()
        val antre = Channel<TdApi.Message>(Channel.UNLIMITED)
        val pemompa = lingkupRealtime.launch {
            telegram.pesanBaru.collect { antre.trySend(it) }
        }
        val batch = mutableListOf<TdApi.Message>()
        try {
            while (konteks.isActive) {
                batch += antre.receive()
                // Tunggu kiriman susulan dalam jendela; timeout = batch siap di-ingest.
                withTimeoutOrNull(AturanRealtime.JENDELA_BATCH_MS) {
                    while (konteks.isActive) batch += antre.receive()
                }
                val isi = batch.toList()
                batch.clear()
                runCatching { prosesBatch(isi) }
                    .onFailure { Log.w(tag, "Ingest batch realtime gagal: ${it.message}") }
            }
        } finally {
            pemompa.cancel()
            antre.close()
        }
    }

    /**
     * Ingest satu batch pesan baru ke indeks lokal. Hanya koleksi yang
     * sudah dikenal; chat/topik asing diabaikan (tidak ada koleksi liar).
     * Dedupe per "$chatId:$messageId" — aman terhadap UpdateNewMessage
     * untuk kiriman sendiri yang juga sudah diindeks UploadWorker.
     */
    private suspend fun prosesBatch(pesan: List<TdApi.Message>) {
        if (pesan.isEmpty()) return
        val sudahAda = berkasDao.semuaId().toHashSet()
        val perKunci = mutableMapOf<KunciKoleksi, MutableList<BerkasMentah>>()
        var diproses = 0L
        var diabaikan = 0L
        var duplikat = 0L
        for (m in pesan) {
            val kunci = KunciKoleksi(m.chatId, AturanRealtime.topikDariPesan(m))
            val entitasKoleksi = koleksiDao.ambil(kunci.chatId, kunci.topicId)
            val dikenal = if (entitasKoleksi == null) {
                false
            } else if (entitasKoleksi.asalImpor) {
                // Lazy subscribe: koleksi impor hanya di-ingest bila pernah dibuka pengguna
                koleksiImporDikenalRealtime.contains(kunci)
            } else {
                true // Koleksi pribadi selalu dikenal
            }
            val id = AturanRealtime.kunciDedupe(m.chatId, m.id)
            val mentah = m.keBerkasMentah()
            when (AturanRealtime.keputusanPesan(dikenal, id in sudahAda, mentah != null)) {
                AturanRealtime.KeputusanPesan.PROSES -> {
                    perKunci.getOrPut(kunci) { mutableListOf() } += mentah ?: continue
                    sudahAda += id
                    diproses++
                }
                AturanRealtime.KeputusanPesan.ABAIKAN_TAK_DIKENAL,
                AturanRealtime.KeputusanPesan.LEWATI_BUKAN_BERKAS -> diabaikan++
                AturanRealtime.KeputusanPesan.LEWATI_DUPLIKAT -> duplikat++
            }
        }
        val sekarang = System.currentTimeMillis()
        for ((kunci, daftar) in perKunci) {
            var nomor = berkasDao.jumlah(kunci.chatId, kunci.topicId)
            val entri = daftar.map { b ->
                val bSegar = pastikanUkuran(b)
                nomor += 1
                bSegar.keEntity(kunci.chatId, kunci.topicId, nomor, sekarang)
            }
            berkasDao.simpanSemua(entri)
            koleksiDao.hitungUlang(kunci.chatId, kunci.topicId)
        }
        if (diproses > 0) {
            catatSinkron()
            Log.i(tag, "Realtime: $diproses berkas baru masuk indeks.")
        }
        _statusRealtime.update {
            it.copy(
                pesanDiproses = it.pesanDiproses + diproses,
                pesanDiabaikan = it.pesanDiabaikan + diabaikan,
                duplikatDilewati = it.duplikatDilewati + duplikat,
            )
        }
    }

    /** Reaksi koneksi: Ready setelah putus → sinkron ringan bila ambang. */
    private suspend fun kolektorKoneksi() {
        var sebelumnya = false
        telegram.koneksi.collect { siap ->
            _statusRealtime.update { it.copy(tersambung = siap) }
            if (siap && !sebelumnya) {
                Log.i(tag, "Koneksi Telegram pulih — cek sinkron ringan.")
                sinkronRinganOtomatis()
            }
            sebelumnya = siap
        }
    }

    /** Refresh nama koleksi channel lama bila judulnya diubah di Telegram. */
    private suspend fun kolektorJudulChat() {
        telegram.judulChatBerubah.collect { (chatId, judul) ->
            val entri = koleksiDao.ambil(chatId, 0) ?: return@collect
            val baru = judul.ifBlank { entri.nama }
            if (baru != entri.nama) koleksiDao.simpan(entri.copy(nama = baru))
        }
    }

    /**
     * Refresh nama koleksi topik yang SUDAH dikenal bila diubah di
     * Telegram. Topik asing tetap tidak membuat koleksi baru.
     */
    private suspend fun kolektorInfoTopik() {
        telegram.infoTopikBerubah.collect { (chatId, topicId, nama) ->
            val entri = koleksiDao.ambil(chatId, topicId) ?: return@collect
            val baru = nama.ifBlank { entri.nama }
            if (baru != entri.nama) koleksiDao.simpan(entri.copy(nama = baru))
        }
    }

    /**
     * Tandai berkas yang pesannya dihapus di Telegram.
     *
     * Baris tidak dihapus: bila pengguna sudah mengunduh salinannya, berkas
     * itu masih berguna dan harus tetap bisa ditemukan. Yang hilang adalah
     * **salinan di vault**, dan itu memang informasi yang harus jujur
     * ditampilkan — bukan disembunyikan sampai pengguna menemukan sendiri
     * saat mengunduh gagal.
     */
    private suspend fun kolektorPenghapusan() {
        telegram.pesanDihapus.collect { (chatId, messageId) ->
            val id = BerkasArsipEntity.kunci(chatId, messageId)
            if (berkasDao.ambilId(id) == null) return@collect
            runCatching { berkasDao.tandaiHilang(id) }
                .onFailure { Log.w(tag, "Gagal menandai pesan terhapus $id: ${it.message}") }
        }
    }

    /**
     * Selaraskan baris yang pesannya diedit atau diganti isinya.
     *
     * Hanya kolom yang berasal dari Telegram yang ditulis ulang. `tdLocalPath`
     * dan `thumbPath` **tidak boleh** disentuh: keduanya menyimpan kerja
     * lokal pengguna, dan menimpanya dengan null (seperti yang terjadi bila
     * @Upsert dipakai untuk pesan yang sudah terindeks) membuat berkas yang
     * sudah diunduh tampak hilang lagi.
     */
    private suspend fun kolektorPerubahan() {
        telegram.pesanDiubah.collect { pesan ->
            val id = BerkasArsipEntity.kunci(pesan.chatId, pesan.id)
            val lama = berkasDao.ambilId(id) ?: return@collect
            val mentah = pesan.keBerkasMentah() ?: return@collect
            val baru = pastikanUkuran(mentah)
            if (baru.nama == lama.nama && baru.ukuran == lama.ukuran &&
                baru.caption == lama.deskripsi
            ) {
                return@collect
            }
            runCatching {
                berkasDao.perbaruiDariTelegram(
                    id = id,
                    nama = baru.nama,
                    ukuran = baru.ukuran,
                    mime = baru.mime,
                    tipe = baru.tipe.name,
                    deskripsi = captionTanpaHeader(baru.caption),
                    tdFileId = baru.tdFileId,
                )
            }.onFailure { Log.w(tag, "Gagal menyelaraskan pesan $id: ${it.message}") }
        }
    }

    /**
     * Sinkron ringan otomatis: semua koleksi pribadi. Dilewati bila
     * sinkron terakhir masih dalam ambang (anti-spam saat koneksi
     * flapping) atau bila sinkron lain sedang berjalan.
     */
    private suspend fun sinkronRinganOtomatis() {
        if (!AturanRealtime.bolehSinkronUlang(pengaturan.sinkronTerakhir())) {
            Log.i(tag, "Sinkron otomatis dilewati — baru saja sinkron.")
            return
        }
        if (!kunciSinkron.tryLock()) {
            Log.i(tag, "Sinkron otomatis dilewati — sinkron lain berjalan.")
            return
        }
        try {
            _statusRealtime.update { it.copy(menyinkronkan = true) }
            try {
                val koleksi = koleksiDao.ambilSemuaPribadi()
                koleksi.forEach { sinkronKoleksi(it.kunci) }
                koleksiDao.hitungUlangSemua()
                _statusRealtime.update { it.copy(sinkronOtomatis = it.sinkronOtomatis + 1) }
                Log.i(tag, "Sinkron otomatis selesai (${koleksi.size} koleksi).")
            } finally {
                _statusRealtime.update { it.copy(menyinkronkan = false) }
            }
        } finally {
            kunciSinkron.unlock()
        }
    }

    /** Catat waktu sinkron ke DataStore + status UI. */
    private suspend fun catatSinkron() {
        val sekarang = System.currentTimeMillis()
        pengaturan.setSinkronTerakhir(sekarang)
        _statusRealtime.update { it.copy(sinkronTerakhir = sekarang) }
    }

    // ---- Koleksi ----

    /**
     * Buat koleksi baru sebagai topik forum di vault. Vault dibuat otomatis
     * bila pengguna belum memilih. Topik yang sudah ada dipakai ulang.
     *
     * Satu tombol membereskan semuanya: vault disiapkan otomatis (dibuat /
     * dipulihkan bila rusak dan tak dipakai koleksi mana pun), lalu topik
     * dibuat — user tidak mengurus vault secara terpisah.
     *
     * @param label chip label opsional (maks 24 karakter).
     * @param warna ARGB untuk dot/chip kategori, 0 = warna tema.
     * @param tipe himpunan tipe berkas untuk rute otomatis; kosong = semua tipe.
     * @param kunci kunci pengaman koleksi baru; null = tanpa kunci. Bila topik
     * dipakai ulang dan [kunci] null, kunci lama dipertahankan.
     */
    suspend fun buatKoleksi(
        nama: String,
        deskripsi: String,
        label: String = "",
        warna: Int = 0,
        tipe: Set<TipeFile> = emptySet(),
        kunci: KunciBaru? = null,
    ): KoleksiEntity {
        val vault = pastikanVault()
        val topik = pastikanTopikDenganPerbaikanForum(vault, nama.trim())
        // Pakai chatId dari hasil (bukan vault lama) agar tetap benar bila
        // vault basi diganti vault baru otomatis di dalam perbaikan forum.
        val chatId = topik.chatId
        val lama = koleksiDao.ambil(chatId, topik.topicId)
        val sekarang = System.currentTimeMillis()
        val labelBersih = label.trim().take(24).takeIf { it.isNotBlank() }
        val (tipeKunci, garamKunci, hashKunci, bioKunci) = kunciDisimpan(kunci, lama)
        val koleksi = KoleksiEntity(
            chatId = chatId,
            topicId = topik.topicId,
            nama = topik.nama,
            deskripsi = deskripsi.trim().takeIf { it.isNotBlank() } ?: lama?.deskripsi,
            label = labelBersih ?: lama?.label,
            warna = warna.takeIf { it != 0 } ?: lama?.warna ?: 0,
            tipeFile = if (tipe.isEmpty()) lama?.tipeFile ?: "" else tipe.joinToString(",") { it.name },
            kunciTipe = tipeKunci,
            kunciGaram = garamKunci,
            kunciHash = hashKunci,
            kunciBiometrik = bioKunci,
            // Koleksi buatan pengguna selalu pribadi — sekalipun topiknya
            // dipakai ulang dari hasil tarikan, kepemilikan beralih ke pribadi.
            asalImpor = false,
            jumlahBerkas = lama?.jumlahBerkas ?: 0,
            dibuatPada = lama?.dibuatPada ?: sekarang,
            terakhirSinkron = lama?.terakhirSinkron ?: sekarang,
        )
        koleksiDao.simpan(koleksi)
        return koleksi
    }

    /**
     * Hitung kolom kunci untuk entity baru: kunci yang diberikan divalidasi
     * lalu di-hash (rahasia plaintext tidak pernah disimpan); bila null,
     * kunci lama dipertahankan (kasus topik dipakai ulang).
     */
    private fun kunciDisimpan(
        kunci: KunciBaru?,
        lama: KoleksiEntity?,
    ): KunciEmpat {
        if (kunci == null) {
            return KunciEmpat(
                lama?.kunciTipe ?: TipeKunci.TIDAK_ADA.name,
                lama?.kunciGaram,
                lama?.kunciHash,
                lama?.kunciBiometrik ?: 0,
            )
        }
        val galat = KunciRahasia.validasi(kunci.tipe, kunci.rahasia)
            ?: if (kunci.tipe == TipeKunci.TIDAK_ADA) "Pilih jenis kunci dulu." else null
        if (galat != null) throw TelegramException(galat)
        val garam = KunciRahasia.buatGaram()
        return KunciEmpat(
            kunci.tipe.name,
            garam,
            KunciRahasia.hash(kunci.rahasia, garam),
            if (kunci.biometrik) 1 else 0,
        )
    }

    private data class KunciEmpat(
        val tipe: String,
        val garam: String?,
        val hash: String?,
        val biometrik: Int,
    )

    /**
     * Pastikan topik ada; bila vault ternyata belum mode forum (mis. vault
     * dipilih sebelum fitur aktivasi otomatis ada), aktifkan mode forum
     * dulu lalu coba lagi sekali.
     *
     * Dua kegagalan dibedakan: supergroup yang belum forum diperbaiki
     * otomatis (perilaku Build #21); vault yang BUKAN supergroup (mis.
     * channel basi dari versi lama) TIDAK dicoba diaktifkan — melainkan
     * dipulihkan via [pulihkanVaultTakSah] (ganti otomatis bila tak ada
     * koleksi yang memakainya, bila ada beri galat yang jelas).
     */
    private suspend fun pastikanTopikDenganPerbaikanForum(
        vault: Long,
        nama: String,
    ): TelegramClient.InfoTopik {
        try {
            return telegram.pastikanTopik(vault, nama)
        } catch (e: TelegramException) {
            val bukanForum = PenjagaVault.galatBukanForum(e.pesan)
            val hanyaSupergroup = PenjagaVault.galatHanyaSupergroup(e.pesan)
            if (!bukanForum && !hanyaSupergroup) throw e
            val status = periksaVault(vault)
            if (status is PenjagaVault.StatusVault.BukanSupergroup || hanyaSupergroup) {
                // Vault bertipe salah: ToggleSupergroupIsForum pasti gagal,
                // jadi jangan dipanggil sama sekali.
                val label = (status as? PenjagaVault.StatusVault.BukanSupergroup)?.labelTipe
                    ?: labelVaultTerbaik(null, vault)
                return pulihkanVaultTakSah(vault, label, nama)
            }
            try {
                telegram.aktifkanForum(vault)
            } catch (e: IllegalArgumentException) {
                // grupIdDariChat menolak chatId non-supergroup (bukan
                // TelegramException sehingga tak tertangkap di bawah):
                // perlakukan sebagai vault bertipe salah, jangan lempar mentah.
                Log.i(tag, "Vault $vault bukan supergroup (validasi lokal): ${e.message}")
                return pulihkanVaultTakSah(vault, labelVaultTerbaik(null, vault), nama)
            } catch (gagal: TelegramException) {
                if (PenjagaVault.galatHanyaSupergroup(gagal.pesan)) {
                    return pulihkanVaultTakSah(vault, labelVaultTerbaik(null, vault), nama)
                }
                throw TelegramException(
                    PenjagaVault.pesanAktivasiGagal(gagal.pesan),
                    gagal.kode,
                    gagal,
                )
            }
            // Percobaan kedua: aktivasi "berhasil" tetapi forum bisa belum
            // efektif (propagasi). Galat forum di sini diterjemahkan, tidak
            // dilempar mentah; percobaan tidak diulang lagi (hindari loop).
            // Kebijakan pulihkanVaultTakSah diperluas ke jalur ini: bila
            // vault tak dipakai koleksi mana pun, ganti vault baru otomatis
            // dan lanjutkan (aman — tidak ada koleksi yang kehilangan rumah).
            // Bila dipakai koleksi lain, lempar galat terstruktur agar UI bisa
            // menawarkan tombol "Buat vault baru & coba lagi" (vault lama
            // tidak diutak-atik).
            try {
                return telegram.pastikanTopik(vault, nama)
            } catch (e2: TelegramException) {
                if (PenjagaVault.galatBukanForum(e2.pesan) ||
                    PenjagaVault.galatHanyaSupergroup(e2.pesan)
                ) {
                    val dipakai = koleksiDao.alirSemua().first().any { it.chatId == vault }
                    if (!dipakai) {
                        Log.i(tag, "Vault $vault belum forum setelah aktivasi, tanpa koleksi — ganti vault baru otomatis.")
                        return gantiVaultOtomatisDanBuatTopik(nama)
                    }
                    throw GalatButuhVaultBaru(
                        PenjagaVault.terjemahkanGalatForum(e2.pesan),
                        e2,
                    )
                }
                throw e2
            } catch (e2: IllegalArgumentException) {
                throw TelegramException(
                    PenjagaVault.terjemahkanGalatForum(e2.message ?: "galat tak dikenal"),
                    0,
                    e2,
                )
            }
        }
    }

    /** Periksa tipe chat vault via GetChat; gagal baca = TakDiketahui. */
    private suspend fun periksaVault(vault: Long): PenjagaVault.StatusVault {
        val chat = try {
            telegram.ambilChat(vault)
        } catch (e: Exception) {
            Log.w(tag, "GetChat vault $vault gagal, lanjut alur lama: ${e.message}")
            return PenjagaVault.StatusVault.TakDiketahui
        }
        return if (telegram.adalahVaultSah(chat)) {
            PenjagaVault.StatusVault.Sah
        } else {
            PenjagaVault.StatusVault.BukanSupergroup(telegram.labelTipeChat(chat))
        }
    }

    /**
     * Pulihkan vault bertipe salah: tanpa koleksi terdaftar → buang vault
     * basi, buat vault forum baru otomatis, lanjutkan pembuatan topik;
     * dengan koleksi terdaftar → vault lama dipertahankan + galat TERSTRUKTUR
     * ([GalatButuhVaultBaru]) agar UI bisa menawarkan tombol aksi.
     */
    private suspend fun pulihkanVaultTakSah(
        vault: Long,
        labelTipe: String,
        nama: String,
    ): TelegramClient.InfoTopik {
        val adaKoleksi = koleksiDao.alirSemua().first().any { it.chatId == vault }
        return when (val putusan = PenjagaVault.putuskanVaultBukanSupergroup(labelTipe, adaKoleksi)) {
            is PenjagaVault.Keputusan.Tolak -> throw GalatButuhVaultBaru(putusan.pesan)
            PenjagaVault.Keputusan.BuangDanBuatBaru -> {
                Log.i(tag, "Vault basi $vault (tipe: $labelTipe) tanpa koleksi — ganti vault baru otomatis.")
                gantiVaultOtomatisDanBuatTopik(nama)
            }
        }
    }

    /**
     * Buang vault aktif (basi), buat vault forum baru, jadikan aktif, lalu
     * buat topik [nama] di dalamnya. Vault lama + koleksinya TIDAK
     * dihapus/diutak-atik (user bisa kembali lewat Pengaturan bila mau).
     */
    private suspend fun gantiVaultOtomatisDanBuatTopik(nama: String): TelegramClient.InfoTopik {
        pengaturan.setVaultChatId(null)
        val baru = buatVaultBaru().chatId
        try {
            return telegram.pastikanTopik(baru, nama)
        } catch (e: TelegramException) {
            throw TelegramException(
                PenjagaVault.terjemahkanGalatForum(e.pesan),
                e.kode,
                e,
            )
        }
    }

    // ---- Kunci koleksi ----

    /**
     * Koleksi yang sudah dibuka sesi ini (in-memory, hilang saat proses mati).
     * Satu sumber kebenaran untuk Katalog, Pohon, Kelola, dan Unggah.
     */
    private val _koleksiTerbuka = MutableStateFlow<Set<KunciKoleksi>>(emptySet())
    val koleksiTerbuka: StateFlow<Set<KunciKoleksi>> = _koleksiTerbuka.asStateFlow()

    /** True bila koleksi terkunci DAN belum dibuka sesi ini. */
    fun perluBukaKunci(entitas: KoleksiEntity?): Boolean =
        KunciRahasia.perluBukaKunci(entitas, _koleksiTerbuka.value)

    /**
     * Coba buka koleksi dengan rahasia (PIN/kata sandi/pola "0-1-4-7-8").
     * True bila cocok (koleksi ditandai terbuka sesi ini).
     */
    suspend fun bukaKunci(kunci: KunciKoleksi, rahasia: String): Boolean {
        val entitas = koleksiDao.ambil(kunci.chatId, kunci.topicId) ?: return false
        if (!entitas.terkunci) return true
        val cocok = KunciRahasia.verifikasi(
            rahasia, entitas.kunciGaram.orEmpty(), entitas.kunciHash.orEmpty(),
        )
        if (cocok) _koleksiTerbuka.value += kunci
        return cocok
    }

    /**
     * Tandai terbuka setelah BiometricPrompt sukses di UI.
     * Repository tidak memanggil biometrik — hanya mencatat hasilnya.
     */
    fun bukaKunciBiometrik(kunci: KunciKoleksi) {
        _koleksiTerbuka.value += kunci
    }

    /**
     * Ganti/hapus kunci koleksi. Bila koleksi sedang terkunci, [rahasiaLama]
     * WAJIB cocok dulu. [kunciBaru] null berarti hapus kunci.
     *
     * @throws TelegramException "Kunci lama salah." bila verifikasi gagal,
     * atau pesan validasi bila kunci baru tidak valid.
     */
    suspend fun ubahKunci(kunci: KunciKoleksi, rahasiaLama: String?, kunciBaru: KunciBaru?) {
        val entitas = koleksiDao.ambil(kunci.chatId, kunci.topicId)
            ?: throw TelegramException("Koleksi tidak ditemukan.")
        if (entitas.terkunci) {
            val cocok = KunciRahasia.verifikasi(
                rahasiaLama.orEmpty(), entitas.kunciGaram.orEmpty(), entitas.kunciHash.orEmpty(),
            )
            if (!cocok) throw TelegramException("Kunci lama salah.")
        }
        if (kunciBaru == null) {
            koleksiDao.simpan(
                entitas.copy(
                    kunciTipe = TipeKunci.TIDAK_ADA.name,
                    kunciGaram = null,
                    kunciHash = null,
                    kunciBiometrik = 0,
                ),
            )
            _koleksiTerbuka.value -= kunci
            return
        }
        val galat = KunciRahasia.validasi(kunciBaru.tipe, kunciBaru.rahasia)
        if (galat != null) throw TelegramException(galat)
        val garam = KunciRahasia.buatGaram()
        koleksiDao.simpan(
            entitas.copy(
                kunciTipe = kunciBaru.tipe.name,
                kunciGaram = garam,
                kunciHash = KunciRahasia.hash(kunciBaru.rahasia, garam),
                kunciBiometrik = if (kunciBaru.biometrik) 1 else 0,
            ),
        )
        // Kunci diganti oleh pemilik yang terverifikasi — anggap terbuka sesi ini.
        _koleksiTerbuka.value += kunci
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
        _koleksiTerbuka.value -= kunci
    }

    suspend fun koleksiTerpilihAtauPertama(): KunciKoleksi? =
        koleksiDao.ambilSemuaPribadi().firstOrNull()?.kunci

    /**
     * Semua kunci koleksi yang sudah dikenal (pribadi + impor) — snapshot
     * untuk deteksi "baru tercakup" saat Tarik. Bukan nama: topik bernama
     * sama dengan koleksi lama tetap baru bila kuncinya berbeda.
     */
    suspend fun kunciDikenal(): Set<KunciKoleksi> =
        (koleksiDao.ambilSemuaPribadi() + koleksiDao.ambilSemuaImpor())
            .map { it.kunci }
            .toSet()

    /** Satu entri koleksi berdasarkan kuncinya, null bila belum dikenal. */
    suspend fun koleksi(kunci: KunciKoleksi): KoleksiEntity? =
        koleksiDao.ambil(kunci.chatId, kunci.topicId)

    // ---- Unggah ----

    /**
     * Antrekan unggah beberapa berkas SAF ke satu koleksi. Untuk koleksi
     * topik, topik dipastikan ada dulu (dibuat otomatis bila hilang).
     */
    suspend fun mulaiUnggah(uris: List<Uri>, kunci: KunciKoleksi) {
        val kunciFinal = if (kunci.adalahTopik) pastikanTopikKoleksi(kunci) else kunci
        val hanyaWifi = pengaturan.hanyaWifi.first()
        uris.forEach { uri ->
            val workId = UUID.randomUUID().toString()
            val pekerjaan = "unggah-$workId"
            // Sisipkan status jeda awal (tidak jeda, prioritas normal) ke database.
            // Simpan juga parameter enqueue agar "lanjutkan" dapat membangun ulang WorkRequest.
            pauseDao.upsert(
                UploadQueuePauseEntity(
                    workId = workId,
                    isPaused = 0,
                    priority = 0,
                    uri = uri.toString(),
                    koleksiId = kunciFinal.chatId,
                    topicId = kunciFinal.topicId,
                )
            )
            val permintaan = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(
                    workDataOf(
                        UploadWorker.K_URI to uri.toString(),
                        UploadWorker.K_KOLEKSI to kunciFinal.chatId,
                        UploadWorker.K_TOPIK to kunciFinal.topicId,
                        UploadWorker.K_WORK_ID to workId,
                    ),
                )
                // Menunggu jaringan: tanpa ini antrean langsung jalan saat
                // perangkat offline, memakai salah satu dari dua percobaan
                // ulang, lalu gagal padahal akan berhasil begitu jaringan kembali.
                .setConstraints(UploadWorker.batasJaringan(hanyaWifi))
                .addTag(pekerjaan)
                .addTag(TAG_UNGGAH)
                .build()
            workManager.enqueueUniqueWork(pekerjaan, ExistingWorkPolicy.APPEND_OR_REPLACE, permintaan)
        }
    }

    /**
     * Jeda pekerjaan unggah: tandai di Room DAN batalkan work agar berhenti segera.
     * WorkManager cancel menyebabkan CancellationException di worker — sudah ditangani
     * dengan `throw e` sehingga state menjadi CANCELLED (bukan FAILED).
     * Jika entitas tidak ada, tidak melakukan apa-apa.
     */
    suspend fun jedaUnggah(workId: String) {
        val entitas = pauseDao.getPauseStatus(workId) ?: return
        pauseDao.updatePauseStatus(entitas.copy(isPaused = 1))
        workManager.cancelUniqueWork("unggah-$workId")
    }

    /**
     * Lanjutkan pekerjaan unggah yang dijeda: hapus tanda jeda di Room, lalu
     * daftarkan ulang WorkRequest (REPLACE) agar mulai segera — tanpa backoff.
     *
     * Guard: jika data enqueue tidak lengkap (uri kosong atau koleksiId 0),
     * cukup hapus tanda jeda tanpa mendaftarkan ulang.
     * Guard 2: jika sudah ada state SUCCEEDED, tidak daftarkan ulang (berkas sudah terkirim).
     */
    suspend fun lanjutkanUnggah(workId: String) {
        val entitas = pauseDao.getPauseStatus(workId) ?: return
        pauseDao.updatePauseStatus(entitas.copy(isPaused = 0))

        // Guard data tidak lengkap
        if (entitas.uri.isBlank() || entitas.koleksiId == 0L) return

        // Cek state WorkManager: jangan daftarkan ulang bila sudah SUCCEEDED
        val states = withContext(Dispatchers.IO) {
            workManager.getWorkInfosForUniqueWork("unggah-$workId").get()
        }.map { it.state }
        if (!perluDaftarkanUlang(states)) return

        val hanyaWifi = pengaturan.hanyaWifi.first()
        val pekerjaan = "unggah-$workId"
        val permintaanBaru = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(
                workDataOf(
                    UploadWorker.K_URI to entitas.uri,
                    UploadWorker.K_KOLEKSI to entitas.koleksiId,
                    UploadWorker.K_TOPIK to entitas.topicId,
                    UploadWorker.K_WORK_ID to workId,
                    UploadWorker.K_LANJUTAN to true,
                ),
            )
            .setConstraints(UploadWorker.batasJaringan(hanyaWifi))
            .addTag(pekerjaan)
            .addTag(TAG_UNGGAH)
            .build()
        // Cancel eksplisit dulu (jika masih RUNNING karena race condition),
        // lalu enqueue REPLACE agar dijadwalkan segera tanpa backoff.
        workManager.cancelUniqueWork(pekerjaan)
        workManager.enqueueUniqueWork(pekerjaan, ExistingWorkPolicy.REPLACE, permintaanBaru)
    }

    /** Mengamati status jeda pekerjaan unggah; false bila belum pernah dicatat. */
    fun amatiJedaUnggah(workId: String): Flow<Boolean> =
        pauseDao.amatiJeda(workId).map { it == 1 }

    /** Dibaca UploadWorker di awal doWork: apakah pekerjaan ini sedang dijeda. */
    suspend fun sedangJeda(workId: String): Boolean =
        pauseDao.getPauseStatus(workId)?.isPaused == 1

    /** Tandai/hapus tanda prioritas satu pekerjaan unggah. */
    suspend fun setPrioritasUnggah(workId: String, prioritas: Boolean) {
        val entitas = pauseDao.getPauseStatus(workId) ?: return
        pauseDao.updatePauseStatus(entitas.copy(priority = if (prioritas) 1 else 0))
    }

    /** Mengamati daftar workId bertanda prioritas (untuk urutan antrean). */
    fun amatiPrioritasUnggah(): Flow<List<String>> = pauseDao.amatiPrioritas()

    /**
     * Antrekan unggah dengan rute otomatis: tiap berkas dideteksi tipenya,
     * lalu dikirim ke kategori yang menangani tipe tersebut. Kategori yang
     * paling spesifik (menangani tipe paling sedikit) dipilih bila beberapa
     * cocok; kategori "semua tipe" dipakai bila tidak ada yang cocok.
     *
     * @return peta kunci koleksi → jumlah berkas yang dirutekan ke sana.
     */
    suspend fun unggahOtomatis(uris: List<Uri>): Map<KunciKoleksi, Int> =
        unggahOtomatis(uris, kecuali = emptySet())

    /**
     * Rute otomatis dengan pengecualian: koleksi terkunci yang belum dibuka
     * sesi ini dilewati ([kecuali]) — berkas tidak dikirim ke koleksi yang
     * user belum buka kuncinya. Hanya koleksi pribadi yang jadi tujuan;
     * koleksi impor tidak menerima unggahan.
     */
    suspend fun unggahOtomatis(
        uris: List<Uri>,
        kecuali: Set<KunciKoleksi>,
    ): Map<KunciKoleksi, Int> {
        require(uris.isNotEmpty()) { "Belum ada berkas yang dipilih." }
        val semua = koleksiDao.ambilSemuaPribadi()
        require(semua.isNotEmpty()) { "Buat kategori dulu di Kelola koleksi." }
        val daftar = semua.filter { it.kunci !in kecuali }
        require(daftar.isNotEmpty()) {
            "Semua koleksi terkunci — buka dulu kuncinya sebelum mengunggah."
        }
        val perKunci = mutableMapOf<KunciKoleksi, MutableList<Uri>>()
        uris.forEach { uri ->
            val target = pilihKategoriUntuk(deteksiTipe(uri), daftar)
            perKunci.getOrPut(target.kunci) { mutableListOf() } += uri
        }
        perKunci.forEach { (kunci, daftarUri) -> mulaiUnggah(daftarUri, kunci) }
        return perKunci.mapValues { it.value.size }
    }

    /**
     * Periksa semua kategori PRIBADI dan buat ulang topik yang hilang di
     * Telegram (mis. dihapus dari luar aplikasi).
     *
     * @return jumlah topik yang dibuat ulang.
     */
    suspend fun perbaikiTopikHilang(): Int {
        val daftar = koleksiDao.ambilSemuaPribadi().filter { it.adalahTopik }
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
    private fun deteksiTipe(uri: Uri): TipeFile {        val resolver = context.contentResolver
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
        val topik = pastikanTopikDenganPerbaikanForum(kunci.chatId, lokal.nama)
        if (topik.chatId != kunci.chatId || topik.topicId != kunci.topicId) {
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
            // Menunggu jaringan: tanpa ini antrean langsung jalan saat perangkat
            // offline, memakai salah satu dari percobaan ulang, lalu gagal
            // padahal akan berhasil begitu jaringan kembali.
            .setConstraints(DownloadWorker.BATAS_JARINGAN)
            .build()
        workManager.enqueueUniqueWork("unduh-$berkasId", ExistingWorkPolicy.KEEP, permintaan)
    }

    /**
     * Seluruh potongan milik satu kelompok, terurut indeks.
     *
     * Dipakai `DownloadWorker` untuk menyatu kembali berkas besar sebelum
     * ditulis ke penyimpanan pengguna.
     */
    suspend fun ambilGrup(grupId: String): List<BerkasArsipEntity> =
        berkasDao.ambilGrup(grupId)

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

    /**
     * Refresh metadata ukuran berkas via TDLib 1x percobaan ringan bila ukuran masih 0.
     * Bila tetap 0, berkas tetap dikembalikan apa adanya agar tetap tersimpan.
     */
    private suspend fun pastikanUkuran(b: BerkasMentah): BerkasMentah {
        if (b.ukuran > 0L || b.tdFileId <= 0) return b
        val ukuranSegar = runCatching {
            telegram.ambilFile(b.tdFileId).size
        }.getOrNull() ?: 0L
        return if (ukuranSegar > 0L) b.copy(ukuran = ukuranSegar) else b
    }

    /**
     * True bila berkas bernama [nama] berukuran [ukuran] untuk chat [chatId]
     * sudah terindeks dalam [jendelaMs] terakhir (default 7 hari). Guard idempoten untuk
     * retry [UploadWorker]: kiriman yang sebenarnya sudah sampai ke Telegram
     * (terindeks oleh batch realtime) tidak dikirim ulang sebagai pesan baru.
     * Hanya dipakai saat runAttemptCount > 0 — unggahan baru dari pengguna
     * selalu dikirim.
     */
    suspend fun sudahTerindeks(
        nama: String,
        ukuran: Long,
        chatId: Long,
        jendelaMs: Long = AturanIdempoten.JENDELA_DEFAULT_MS,
    ): Boolean {
        val sekarang = System.currentTimeMillis()
        val sejak = sekarang - jendelaMs
        val kandidat = berkasDao.cariKandidatIdempoten(nama, chatId, sejak)
        return kandidat.any { target ->
            val waktuTarget = maxOf(target.dibuatPada, target.tanggal)
            AturanIdempoten.cocok(
                namaKandidat = nama,
                ukuranKandidat = ukuran,
                chatIdKandidat = chatId,
                namaTarget = target.nama,
                ukuranTarget = target.ukuran,
                chatIdTarget = target.koleksiId ?: target.chatId,
                waktuTarget = waktuTarget,
                sekarang = sekarang,
                jendelaMs = jendelaMs,
            )
        }
    }

    suspend fun hapusBerkas(berkasId: String) {
        berkasDao.hapus(berkasId)
    }

    /** Pindai kelompok berkas duplikat yang ada di indeks lokal. */
    suspend fun pindaiDuplikat(): List<GrupDuplikat> {
        val semua = berkasDao.ambilSemua()
        return AturanDuplikat.kelompokkan(semua)
    }

    /**
     * Rapikan duplikat: simpan SATU baris per grup (nomorKatalog terkecil),
     * hapus baris lain DARI INDEKS LOKAL SAJA. Pesan Telegram TIDAK DIHAPUS.
     */
    suspend fun rapikanDuplikatLokal(grup: List<GrupDuplikat>): Int {
        val (_, dihapus) = AturanDuplikat.pisahkanUntukRapikan(grup)
        if (dihapus.isEmpty()) return 0
        return berkasDao.hapusBanyak(dihapus.map { it.id })
    }

    /**
     * Opsi lanjutan: simpan SATU baris per grup (nomorKatalog terkecil),
     * hapus baris duplikat lain dari indeks lokal DAN hapus pesannya di Telegram.
     */
    suspend fun rapikanDuplikatDenganTelegram(grup: List<GrupDuplikat>): Int {
        val (_, dihapus) = AturanDuplikat.pisahkanUntukRapikan(grup)
        if (dihapus.isEmpty()) return 0
        dihapus.forEach { b ->
            val cid = b.koleksiId ?: b.chatId
            if (cid != 0L && b.messageId > 0L) {
                runCatching {
                    telegram.hapusPesan(cid, listOf(b.messageId))
                }
            }
        }
        return berkasDao.hapusBanyak(dihapus.map { it.id })
    }

    companion object {
        const val HALAMAN_RIWAYAT = 100
        const val BATAS_HALAMAN = 5

        /**
         * Halaman maksimum saat penyelarasan. Lebih besar daripada
         * [BATAS_HALAMAN] karena penyelarasan harus menelusuri berkas **lama**
         * untuk memverifikasi bahwa mereka masih ada.
         */
        const val BATAS_HALAMAN_SELARAS = 50

        /** Nama bawaan grup vault yang dibuat otomatis. */
        const val NAMA_VAULT_BAWAAN = "Arsip Senja"

        /** Tag bersama untuk semua pekerjaan unggah (dipantau layar Unggah). */
        const val TAG_UNGGAH = "antrean-unggah"

        /**
         * Tentukan apakah WorkRequest perlu didaftarkan ulang saat "lanjutkan" ditekan.
         * Hanya JANGAN daftarkan ulang bila sudah ada state SUCCEEDED (berkas sudah terkirim penuh).
         * Untuk state ENQUEUED/RUNNING/BLOCKED/FAILED/CANCELLED/daftar kosong → daftarkan ulang.
         */
        fun perluDaftarkanUlang(states: List<WorkInfo.State>): Boolean =
            states.none { it == WorkInfo.State.SUCCEEDED }

        /** Nama kerja unik unduhan satu berkas. */
        fun namaKerjaUnduh(berkasId: String) = "unduh-$berkasId"
    }
}
