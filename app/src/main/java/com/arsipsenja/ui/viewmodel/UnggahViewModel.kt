package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.telegram.ProgresBerkas
import com.arsipsenja.data.telegram.TelegramClient
import com.arsipsenja.data.work.UploadWorker
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

/**
 * ViewModel layar unggah: pemilihan koleksi tujuan, antrean WorkManager,
 * dan aliran progres transfer langsung dari TDLib.
 */
/**
 * Kunci pengurutan antrean unggah (murni, dapat diuji):
 * bertanda prioritas dulu (0), lalu yang sedang berjalan (1), lalu sisanya (2).
 */
internal fun kunciUrutAntrean(prioritas: Boolean, sedangBerjalan: Boolean): Int =
    when {
        prioritas -> 0
        sedangBerjalan -> 1
        else -> 2
    }

class UnggahViewModel(
    private val repo: ArsipRepository,
    private val workManager: WorkManager,
    private val telegram: TelegramClient,
    private val pengaturan: PengaturanStore,
) : ViewModel() {

    /** Koleksi tujuan: hanya pribadi — koleksi impor tidak menerima unggahan. */
    val koleksi: StateFlow<List<KoleksiEntity>> = repo.alirKoleksiPribadi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _koleksiTerpilih = MutableStateFlow<KunciKoleksi?>(null)
    val koleksiTerpilih: StateFlow<KunciKoleksi?> = _koleksiTerpilih.asStateFlow()

    private val _ruteOtomatis = MutableStateFlow(false)
    /** True: tiap berkas dirutekan ke kategori sesuai tipe berkasnya. */
    val ruteOtomatis: StateFlow<Boolean> = _ruteOtomatis.asStateFlow()

    private val _hanyaWifi = MutableStateFlow(false)
    /** True: unggahan ditahan WorkManager selama di jaringan seluler. */
    val hanyaWifi: StateFlow<Boolean> = _hanyaWifi.asStateFlow()

    private val _pesan = MutableStateFlow<PesanStatus?>(null)
    val pesan: StateFlow<PesanStatus?> = _pesan.asStateFlow()

    /**
     * Popup galat untuk pekerjaan yang FAILED terminal — sekali per
     * pekerjaan, persisten antar buka layar (disimpan di DataStore).
     * Alasan dari outputData [UploadWorker], BUKAN per percobaan retry.
     * Teks per baris antrean tetap tampil; popup ini yang membuat
     * kegagalan "kelihatan".
     */
    private val _galatUnggah = MutableStateFlow<PesanStatus?>(null)
    val galatUnggah: StateFlow<PesanStatus?> = _galatUnggah.asStateFlow()

    private val gagalDilaporkan = mutableSetOf<UUID>()

    /** Tandai dilaporkan: di memori + persisten agar tak dibuka ulang. */
    private fun tandaiDilaporkan(id: UUID) {
        if (gagalDilaporkan.add(id)) {
            viewModelScope.launch { pengaturan.tandaiGagalDilaporkan(id.toString()) }
        }
    }

    /** WorkId yang ditandai prioritas — reaktif dari Room. */
    val prioritasUnggah: StateFlow<List<String>> =
        repo.amatiPrioritasUnggah()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Antrean pekerjaan unggah: bertanda prioritas di atas, lalu yang sedang
     * berjalan, lalu sisanya. Termasuk pekerjaan yang sudah selesai.
     */
    val antrean: StateFlow<List<WorkInfo>> =
        combine(
            workManager.getWorkInfosByTagFlow(ArsipRepository.TAG_UNGGAH),
            prioritasUnggah,
        ) { daftar, prioritas ->
            val bertanda = prioritas.toSet()
            daftar.sortedBy { info ->
                kunciUrutAntrean(
                    info.id.toString() in bertanda,
                    info.state == WorkInfo.State.RUNNING,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _transfer = MutableStateFlow<Map<Int, ProgresBerkas>>(emptyMap())

    /** Transfer berkas yang sedang berjalan (fileId → progres terakhir). */
    val transfer: StateFlow<Map<Int, ProgresBerkas>> = _transfer.asStateFlow()

    init {
        // Toggle rute otomatis persisten — baca sekali dari DataStore agar
        // tidak mati sendiri setiap aplikasi dibuka.
        viewModelScope.launch {
            pengaturan.ruteOtomatis.collect { _ruteOtomatis.value = it }
        }
        viewModelScope.launch {
            pengaturan.hanyaWifi.collect { _hanyaWifi.value = it }
        }
        viewModelScope.launch {
            telegram.progresBerkas.collect { p ->
                _transfer.update { peta ->
                    when {
                        p.selesaiUnggah -> peta - p.fileId
                        p.ukuran <= 0L && p.terkirim <= 0L -> peta
                        else -> peta + (p.fileId to p)
                    }
                }
            }
        }
        viewModelScope.launch {
            repo.alirKoleksiPribadi().collect { daftar ->
                if (_koleksiTerpilih.value == null) {
                    _koleksiTerpilih.value = daftar.firstOrNull()?.kunci
                }
            }
        }
        viewModelScope.launch {
            // Muat dulu ID galat yang sudah dilaporkan agar galat lama
            // tidak dibuka ulang setiap layar Unggah dibuka.
            pengaturan.aliranGagalDilaporkan.first().forEach { teks ->
                runCatching { gagalDilaporkan.add(UUID.fromString(teks)) }
            }
            antrean.collect { daftar ->
                if (_galatUnggah.value != null) return@collect
                val gagal = daftar.firstOrNull {
                    it.state == WorkInfo.State.FAILED && it.id !in gagalDilaporkan
                } ?: return@collect
                tandaiDilaporkan(gagal.id)
                _galatUnggah.value = PesanStatus(
                    NadaStatus.GALAT,
                    teksGalatUnggah(gagal),
                )
            }
        }
    }

    /**
     * Teks popup galat: alasan asli worker bila informatif, tanpa
     * pengulangan "gagal … gagal".
     */
    private fun teksGalatUnggah(info: WorkInfo): String {
        val alasan = info.outputData.getString(UploadWorker.K_PESAN_GALAT)
            ?.takeIf { it.isNotBlank() }
            ?: "Unggah gagal."
        val nama = info.progress.getString(UploadWorker.K_NAMA)
            ?.takeIf { it.isNotBlank() }
        val inti = if (alasan == "Unggah gagal.") alasan else "Unggahan gagal: $alasan"
        return if (nama != null) "$nama — $inti" else inti
    }

    fun pilihKoleksi(kunci: KunciKoleksi?) {
        _koleksiTerpilih.value = kunci
    }

    /** Koleksi yang sudah dibuka sesi ini (untuk gating unggah). */
    val koleksiTerbuka: StateFlow<Set<KunciKoleksi>> = repo.koleksiTerbuka

    /** True bila koleksi terkunci DAN belum dibuka sesi ini. */
    fun perluBukaKunci(entitas: KoleksiEntity?): Boolean = repo.perluBukaKunci(entitas)

    /** Coba buka dengan rahasia; hasil lewat [panggilBalik] (true = cocok). */
    fun bukaKunci(kunci: KunciKoleksi, rahasia: String, panggilBalik: (Boolean) -> Unit) {
        viewModelScope.launch {
            val cocok = try {
                repo.bukaKunci(kunci, rahasia)
            } catch (_: Exception) {
                false
            }
            panggilBalik(cocok)
        }
    }

    /** Tandai terbuka setelah BiometricPrompt sukses di UI. */
    fun bukaKunciBiometrik(kunci: KunciKoleksi) {
        repo.bukaKunciBiometrik(kunci)
    }

    fun setRuteOtomatis(aktif: Boolean) {
        _ruteOtomatis.value = aktif
        viewModelScope.launch { pengaturan.setRuteOtomatis(aktif) }
    }

    fun setHanyaWifi(aktif: Boolean) {
        _hanyaWifi.value = aktif
        viewModelScope.launch { pengaturan.setHanyaWifi(aktif) }
    }

    /**
     * Antrekan URI SAF terpilih. Bila rute otomatis aktif, tiap berkas
     * dideteksi tipenya dan dikirim ke kategori yang menanganinya;
     * bila tidak, semuanya dikirim ke koleksi terpilih manual.
     */
    fun mulaiUnggah(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) {
            _pesan.value = PesanStatus(NadaStatus.GALAT, "Belum ada berkas yang dipilih.")
            return
        }
        viewModelScope.launch {
            try {
                if (_ruteOtomatis.value) {
                    // Koleksi terkunci yang belum dibuka dilewati otomatis.
                    val terkunci = koleksi.value
                        .filter { perluBukaKunci(it) }
                        .map { it.kunci }
                        .toSet()
                    val hasil = repo.unggahOtomatis(uris, terkunci)
                    _pesan.value = PesanStatus(
                        NadaStatus.INFO,
                        if (hasil.size == 1) {
                            "${uris.size} berkas dirutekan otomatis ke kategori."
                        } else {
                            "${uris.size} berkas dirutekan otomatis ke ${hasil.size} kategori."
                        } + if (terkunci.isNotEmpty()) {
                            " (${terkunci.size} koleksi terkunci dilewati.)"
                        } else {
                            ""
                        },
                    )
                } else {
                    val tujuan = _koleksiTerpilih.value
                    if (tujuan == null) {
                        _pesan.value = PesanStatus(
                            NadaStatus.GALAT,
                            "Pilih koleksi tujuan terlebih dahulu.",
                        )
                        return@launch
                    }
                    val entitas = koleksi.value.firstOrNull { it.kunci == tujuan }
                    if (perluBukaKunci(entitas)) {
                        _pesan.value = PesanStatus(
                            NadaStatus.GALAT,
                            "Koleksi \"${entitas?.nama ?: "tujuan"}\" terkunci — buka dulu kuncinya.",
                        )
                        return@launch
                    }
                    repo.mulaiUnggah(uris, tujuan)
                    _pesan.value = PesanStatus(
                        NadaStatus.SUKSES,
                        "${uris.size} berkas masuk antrean unggah.",
                    )
                }
            } catch (e: Exception) {
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal mengantre unggah: ${e.message}",
                )
            }
        }
    }

    fun batalkan(id: UUID) {
        workManager.cancelWorkById(id)
        _pesan.value = PesanStatus(NadaStatus.INFO, "Pekerjaan unggah dibatalkan.")
    }

    fun batalkanSemua() {
        workManager.cancelAllWorkByTag(ArsipRepository.TAG_UNGGAH)
        _transfer.value = emptyMap()
        _pesan.value = PesanStatus(
            NadaStatus.INFO,
            "Seluruh antrean unggah dibatalkan.",
        )
    }

    /**
     * Bersihkan riwayat antrean: hapus semua pekerjaan yang sudah selesai
     * (selesai/gagal/dibatalkan) dari daftar. Pekerjaan yang sedang
     * berjalan tidak ikut terhapus.
     */
    fun bersihkanRiwayat() {
        workManager.pruneWork()
        _transfer.value = emptyMap()
        _pesan.value = PesanStatus(
            NadaStatus.INFO,
            "Riwayat antrean dibersihkan.",
        )
    }

    fun habiskanPesan() {
        _pesan.value = null
    }

    /**
     * Tutup popup galat unggah, lalu pindai antrean untuk pekerjaan FAILED
     * berikutnya yang belum dilaporkan (bila ada, popupnya tampil lagi).
     */
    fun habiskanGalatUnggah() {
        _galatUnggah.value = null
        val berikut = antrean.value.firstOrNull {
            it.state == WorkInfo.State.FAILED && it.id !in gagalDilaporkan
        } ?: return
        tandaiDilaporkan(berikut.id)
        _galatUnggah.value = PesanStatus(
            NadaStatus.GALAT,
            teksGalatUnggah(berikut),
        )
    }

    /** Gabungkan nama koleksi terpilih untuk label layar. */
    val namaKoleksiTerpilih: StateFlow<String?> =
        combine(koleksi, _koleksiTerpilih) { daftar, kunci ->
            daftar.firstOrNull { it.kunci == kunci }?.nama
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Ambil workId Arsip Senja dari [WorkInfo].
     * Setiap pekerjaan unggah diberi tag "unggah-<uuid>" saat antre
     * ([ArsipRepository.mulaiUnggah]); dari tag itulah workId dipulihkan.
     */
    fun workIdDariInfo(info: WorkInfo): String? =
        info.tags.firstOrNull { it.startsWith("unggah-") }?.removePrefix("unggah-")

    /** Jeda manual pekerjaan unggah: tandai di Room DAN batalkan work segera. */
    fun jedaUnggah(workId: String) {
        viewModelScope.launch { repo.jedaUnggah(workId) }
    }

    /** Lanjutkan pekerjaan unggah yang sedang dijeda — mulai seketika tanpa backoff. */
    fun lanjutkanUnggah(workId: String) {
        viewModelScope.launch { repo.lanjutkanUnggah(workId) }
    }

    /** Tandai/hapus tanda prioritas satu pekerjaan unggah. */
    fun tandaiPrioritas(workId: String, prioritas: Boolean) {
        viewModelScope.launch { repo.setPrioritasUnggah(workId, prioritas) }
    }

    /** Status jeda reaktif untuk satu pekerjaan; false bila belum pernah dijeda. */
    fun statusJeda(workId: String): Flow<Boolean> =
        repo.amatiJedaUnggah(workId)

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            UnggahViewModel(
                repo = wadah.arsipRepository,
                workManager = wadah.workManager,
                telegram = wadah.telegramClient,
                pengaturan = wadah.pengaturan,
            )
        }
    }
}
