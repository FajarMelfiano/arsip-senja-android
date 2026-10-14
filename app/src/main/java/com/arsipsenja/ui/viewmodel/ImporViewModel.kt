@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.KueriTampilan
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.repo.PenjagaVault
import com.arsipsenja.data.telegram.TelegramException
import com.arsipsenja.util.StatusLokal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel tab Impor Telegram: daftar koleksi hasil tarikan (terpisah dari
 * koleksi pribadi), berkas tiap koleksi impor, tarik ulang, dan sinkron impor.
 *
 * Berkas koleksi impor yang terkunci mengikuti gating kunci yang sama
 * seperti berkas pribadi — pratinjau tidak bocor sebelum dibuka.
 */
class ImporViewModel(
    private val repo: ArsipRepository,
) : ViewModel() {

    /** Hanya koleksi bertanda impor — tidak pernah berisi koleksi pribadi. */
    val impor: StateFlow<List<KoleksiEntity>> = repo.alirKoleksiImpor()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _dipilih = MutableStateFlow<KunciKoleksi?>(null)
    val dipilih: StateFlow<KunciKoleksi?> = _dipilih.asStateFlow()

    /** Berkas koleksi impor yang sedang dipilih (null = belum pilih). */
    val berkas: StateFlow<List<BerkasArsipEntity>> = _dipilih
        .flatMapLatest { kunci ->
            if (kunci == null) flowOf(emptyList())
            else repo.alirBerkas(KueriTampilan(kunciKoleksi = kunci))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _memuat = MutableStateFlow(false)
    val memuat: StateFlow<Boolean> = _memuat.asStateFlow()

    private val _pesan = MutableStateFlow<PesanStatus?>(null)
    val pesan: StateFlow<PesanStatus?> = _pesan.asStateFlow()

    /** Antrean popup penamaan untuk entri baru hasil Tarik. */
    private val _antreanTarik = MutableStateFlow<List<ItemPenamaan>>(emptyList())
    val antreanTarik: StateFlow<List<ItemPenamaan>> = _antreanTarik.asStateFlow()

    /** Semua nama koleksi (pribadi + impor) untuk validasi duplikat popup. */
    val semuaNamaKoleksi: StateFlow<List<String>> = repo.alirKoleksi()
        .map { daftar -> daftar.map { it.nama } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var totalTarik = 0
    private var gagalTarik = 0

    /** Ringkasan yang menunggu popup galat ditutup dulu (latest-wins). */
    private var ringkasanTarikTertunda = false

    fun pilih(kunci: KunciKoleksi?) {
        _dipilih.value = kunci
    }

    /** Status penyimpanan lokal satu berkas (untuk penanda kartu). */
    fun statusLokal(berkas: BerkasArsipEntity): StatusLokal =
        if (!berkas.tdLocalPath.isNullOrBlank()) StatusLokal.ADA else StatusLokal.BELUM

    private val thumbBerjalan = mutableSetOf<String>()

    /** Minta thumbnail berkas; guard mencegah unduhan ganda untuk id sama. */
    fun mintaThumbnail(berkas: BerkasArsipEntity) {
        if (!berkas.thumbPath.isNullOrBlank() || berkas.tdThumbFileId == 0) return
        synchronized(thumbBerjalan) {
            if (!thumbBerjalan.add(berkas.id)) return
        }
        viewModelScope.launch {
            try {
                repo.pastikanThumbnail(berkas)
            } catch (_: Exception) {
                // Gagal unduh thumbnail: kartu tetap tampil dengan ikon.
            } finally {
                synchronized(thumbBerjalan) { thumbBerjalan.remove(berkas.id) }
            }
        }
    }

    /**
     * Tarik dari Telegram: vault aktif + grup forum-siap milik sendiri +
     * channel lama → semuanya masuk tab ini, bukan daftar pribadi.
     * Entri BARU tercakup ditanyai namanya satu per satu lewat popup;
     * entri yang sudah tercakup tidak ditanya ulang dan namanya
     * tidak pernah ditimpa.
     */
    fun tarik() {
        if (_memuat.value) return
        viewModelScope.launch {
            _memuat.value = true
            try {
                val sebelum = repo.kunciDikenal()
                val daftar = repo.tarikDaftarKoleksi()
                totalTarik = daftar.size
                gagalTarik = 0
                val baru = AntreanPenamaan.baruTercakup(sebelum, daftar)
                if (baru.isEmpty()) {
                    _pesan.value = if (daftar.isEmpty()) {
                        PesanStatus(
                            NadaStatus.INFO,
                            "Tidak ada koleksi baru dari Telegram.",
                        )
                    } else {
                        PesanStatus(
                            NadaStatus.SUKSES,
                            "${daftar.size} koleksi impor diperbarui.",
                        )
                    }
                } else {
                    _antreanTarik.value = AntreanPenamaan.dariTarik(baru)
                }
            } catch (e: TelegramException) {
                _pesan.value = PesanStatus(NadaStatus.GALAT, "Tarik gagal: ${e.pesan}")
            } catch (e: Exception) {
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Tarik gagal: " +
                        PenjagaVault.terjemahkanGalatForum(e.message ?: "galat tak dikenal"),
                )
            } finally {
                _memuat.value = false
            }
        }
    }

    /** Simpan nama untuk item Tarik terdepan; field lain dipertahankan. */
    fun simpanNamaTarik(nama: String) {
        val item = _antreanTarik.value.firstOrNull() ?: return
        val kunci = item.kunci ?: return
        viewModelScope.launch {
            _memuat.value = true
            try {
                val lama = repo.koleksi(kunci)
                repo.ubahKoleksi(
                    kunci,
                    nama.trim(),
                    lama?.deskripsi.orEmpty(),
                    lama?.label.orEmpty(),
                    lama?.warna ?: 0,
                    lama?.tipeDitangani ?: emptySet(),
                )
                _antreanTarik.update { it.drop(1) }
                if (_antreanTarik.value.isEmpty()) {
                    ringkasanTarikTertunda = false
                    _pesan.value = AntreanPenamaan.ringkasanTarik(totalTarik, gagalTarik)
                }
            } catch (e: Exception) {
                gagalTarik++
                _antreanTarik.update { it.drop(1) }
                if (_antreanTarik.value.isEmpty()) {
                    // Tunggu popup galat ditutup agar ringkasan tidak menimpanya.
                    ringkasanTarikTertunda = true
                }
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal memberi nama koleksi: ${e.message}",
                )
            } finally {
                _memuat.value = false
            }
        }
    }

    /** Lewati item Tarik terdepan: nama topik/chat dipertahankan. */
    fun lewatiNamaTarik() {
        _antreanTarik.update { it.drop(1) }
        if (_antreanTarik.value.isEmpty()) {
            ringkasanTarikTertunda = false
            _pesan.value = AntreanPenamaan.ringkasanTarik(totalTarik, gagalTarik)
        }
    }

    /**
     * Batalkan antrean Tarik: sisanya tetap tersimpan dengan nama topik.
     * Bila ada rename yang sudah gagal, ringkasannya tetap ditampilkan
     * agar kegagalan itu tidak hilang diam-diam.
     */
    fun batalkanAntreanTarik() {
        _antreanTarik.value = emptyList()
        ringkasanTarikTertunda = false
        if (gagalTarik > 0) {
            _pesan.value = AntreanPenamaan.ringkasanTarik(totalTarik, gagalTarik)
        }
    }

    /** Sinkronkan ulang semua koleksi impor (ambil riwayat terbaru). */
    fun sinkron() {
        if (_memuat.value) return
        viewModelScope.launch {
            _memuat.value = true
            try {
                val baru = repo.sinkronImpor()
                _pesan.value = if (baru > 0) {
                    PesanStatus(
                        NadaStatus.SUKSES,
                        "Sinkron impor selesai: $baru berkas baru.",
                    )
                } else {
                    PesanStatus(
                        NadaStatus.SUKSES,
                        "Sinkron impor selesai: tidak ada berkas baru.",
                    )
                }
            } catch (e: TelegramException) {
                _pesan.value = PesanStatus(NadaStatus.GALAT, "Sinkronisasi gagal: ${e.pesan}")
            } catch (e: Exception) {
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Sinkronisasi gagal: ${e.message ?: "galat tak dikenal"}",
                )
            } finally {
                _memuat.value = false
            }
        }
    }

    // ---- Kunci koleksi (sumber bersama dengan Dasbor) ----

    /** Koleksi yang sudah dibuka sesi ini (mengikuti repository). */
    val koleksiTerbuka: StateFlow<Set<KunciKoleksi>> = repo.koleksiTerbuka

    /** True bila koleksi terkunci DAN belum dibuka sesi ini. */
    fun perluBukaKunci(entitas: KoleksiEntity?): Boolean = repo.perluBukaKunci(entitas)

    /** Cari entity impor untuk satu berkas (untuk gating ketuk berkas). */
    fun entitasUntuk(chatId: Long, topicId: Int): KoleksiEntity? =
        impor.value.firstOrNull { it.chatId == chatId && it.topicId == topicId }

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

    /**
     * Tutup popup status. Menutup popup galat yang menunda ringkasan
     * menampilkan ringkasan itu.
     */
    fun habiskanPesan() {
        _pesan.value = null
        if (ringkasanTarikTertunda) {
            ringkasanTarikTertunda = false
            _pesan.value = AntreanPenamaan.ringkasanTarik(totalTarik, gagalTarik)
        }
    }

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            ImporViewModel(wadah.arsipRepository)
        }
    }
}
