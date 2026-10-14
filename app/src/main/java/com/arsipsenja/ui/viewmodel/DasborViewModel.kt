@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciBaru
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.KueriTampilan
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.local.StatistikBerkas
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.repo.GalatButuhVaultBaru
import com.arsipsenja.data.repo.PenjagaVault
import com.arsipsenja.data.telegram.TelegramException
import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.StatusLokal
import com.arsipsenja.util.TampilanDasbor
import com.arsipsenja.util.TipeFile
import com.arsipsenja.util.UrutBerkas
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel dasbor: pencarian as-you-type, filter tipe/koleksi, urutan,
 * tampilan kisi/daftar, statistik, sinkronisasi, dan CRUD koleksi.
 */
class DasborViewModel(
    private val repo: ArsipRepository,
    private val pengaturan: PengaturanStore,
) : ViewModel() {

    private val _kata = MutableStateFlow("")
    val kata: StateFlow<String> = _kata.asStateFlow()

    private val _saringan = MutableStateFlow(KueriTampilan())
    val saringan: StateFlow<KueriTampilan> = _saringan.asStateFlow()

    private val _memuat = MutableStateFlow(false)
    val memuat: StateFlow<Boolean> = _memuat.asStateFlow()

    private val _pesan = MutableStateFlow<String?>(null)
    val pesan: StateFlow<String?> = _pesan.asStateFlow()

    private val _koleksiAktif = MutableStateFlow<KunciKoleksi?>(null)
    val koleksiAktif: StateFlow<KunciKoleksi?> = _koleksiAktif.asStateFlow()

    val berkas: StateFlow<List<BerkasArsipEntity>> =
        combine(_kata.debounce(250), _saringan) { kata, kueri ->
            kueri.copy(kata = kata.trim().ifBlank { null })
        }
            .flatMapLatest { repo.alirBerkas(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statistik: StateFlow<StatistikBerkas> = repo.alirStatistik()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatistikBerkas(0, 0L))

    val koleksi: StateFlow<List<KoleksiEntity>> = repo.alirKoleksi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tampilan: StateFlow<TampilanDasbor> = pengaturan.tampilanDasbor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TampilanDasbor.KISI)

    init {
        viewModelScope.launch {
            _saringan.value = repo.tampilanAwal()
        }
    }

    // ---- Pencarian & filter ----

    fun ubahKata(nilai: String) {
        _kata.value = nilai
    }

    fun saringTipe(tipe: TipeFile?) {
        _saringan.update { it.copy(tipe = tipe) }
    }

    fun saringKoleksi(kunci: KunciKoleksi?) {
        _koleksiAktif.value = kunci
        _saringan.update { it.copy(kunciKoleksi = kunci) }
    }

    fun urutkan(urut: UrutBerkas, arah: ArahUrut) {
        _saringan.update { it.copy(urut = urut, arah = arah) }
        viewModelScope.launch { pengaturan.setUrutan(urut, arah) }
    }

    fun gantiTampilan() {
        viewModelScope.launch {
            val sekarang = tampilan.value
            pengaturan.setTampilan(
                if (sekarang == TampilanDasbor.KISI) TampilanDasbor.DAFTAR else TampilanDasbor.KISI,
            )
        }
    }

    /** Status penyimpanan lokal satu berkas (untuk penanda kartu). */
    fun statusLokal(berkas: BerkasArsipEntity): StatusLokal =
        if (!berkas.tdLocalPath.isNullOrBlank()) StatusLokal.ADA else StatusLokal.BELUM

    private val thumbBerjalan = mutableSetOf<String>()

    /**
     * Minta thumbnail berkas; dipanggil sekali per kartu saat thumbnail
     * belum ada. Guard mencegah unduhan ganda untuk id yang sama.
     */
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

    // ---- Sinkronisasi ----

    /**
     * Sinkronisasi terpilih: hanya koleksi yang dicentang di dialog pilih
     * yang disinkron, satu per satu dengan progres nama koleksi.
     */
    fun sinkronTerpilih(kunci: List<KunciKoleksi>) {
        if (_memuat.value || kunci.isEmpty()) return
        viewModelScope.launch {
            _memuat.value = true
            try {
                var totalBaru = 0
                kunci.forEachIndexed { i, k ->
                    val nama = koleksi.value.firstOrNull { it.kunci == k }?.nama
                        ?: "koleksi ${i + 1}"
                    _pesan.value = "Menyinkron “$nama” (${i + 1}/${kunci.size})…"
                    totalBaru += repo.sinkronKoleksi(k)
                }
                _pesan.value = if (totalBaru > 0) {
                    "Sinkronisasi selesai: $totalBaru berkas baru dari ${kunci.size} koleksi."
                } else {
                    "Sinkronisasi selesai: tidak ada berkas baru."
                }
            } catch (e: TelegramException) {
                _pesan.value = "Sinkronisasi gagal: ${e.pesan}"
            } catch (e: Exception) {
                _pesan.value = "Sinkronisasi gagal: ${e.message ?: "galat tak dikenal"}"
            } finally {
                _memuat.value = false
            }
        }
    }

    fun sinkron() {
        if (_memuat.value) return
        viewModelScope.launch {
            _memuat.value = true
            try {
                repo.sinkronSemua()
                _pesan.value = "Sinkronisasi selesai. Indeks lokal sudah segar."
            } catch (e: TelegramException) {
                _pesan.value = "Sinkronisasi gagal: ${e.pesan}"
            } catch (e: Exception) {
                _pesan.value = "Sinkronisasi gagal: ${e.message ?: "galat tak dikenal"}"
            } finally {
                _memuat.value = false
            }
        }
    }

    fun tarikDaftarKoleksi() {
        viewModelScope.launch {
            _memuat.value = true
            try {
                val daftar = repo.tarikDaftarKoleksi()
                _pesan.value = "${daftar.size} koleksi ditemukan di akunmu."
            } catch (e: Exception) {
                _pesan.value = "Gagal menarik daftar koleksi: " +
                    PenjagaVault.terjemahkanGalatForum(e.message ?: "galat tak dikenal")
            } finally {
                _memuat.value = false
            }
        }
    }

    // ---- Koleksi ----

    /** Parameter pembuatan koleksi terakhir (untuk tombol "coba lagi" vault baru). */
    private data class PermintaanBuat(
        val nama: String,
        val deskripsi: String,
        val label: String,
        val warna: Int,
        val tipe: Set<TipeFile>,
        val kunci: KunciBaru?,
    )

    private var permintaanTerakhir: PermintaanBuat? = null
    private var tundaBerhasil: (() -> Unit)? = null

    /**
     * True bila kegagalan terakhir membawa tawaran "Buat vault baru & coba
     * lagi" (vault rusak tetapi dipakai koleksi lain).
     */
    private val _tawarkanVaultBaru = MutableStateFlow(false)
    val tawarkanVaultBaru: StateFlow<Boolean> = _tawarkanVaultBaru.asStateFlow()

    /**
     * Buat koleksi (satu tombol membereskan vault + topik + kunci).
     * Form TIDAK di-reset di sini — [onBerhasil] dipanggil hanya bila sukses
     * sehingga data tetap terisi saat gagal dan user bisa coba lagi.
     */
    fun buatKoleksi(
        nama: String,
        deskripsi: String,
        label: String = "",
        warna: Int = 0,
        tipe: Set<TipeFile> = emptySet(),
        kunci: KunciBaru? = null,
        onBerhasil: (() -> Unit)? = null,
    ) {
        if (nama.isBlank()) {
            _pesan.value = "Nama koleksi belum diisi."
            return
        }
        permintaanTerakhir = PermintaanBuat(nama.trim(), deskripsi.trim(), label, warna, tipe, kunci)
        tundaBerhasil = onBerhasil
        viewModelScope.launch {
            _memuat.value = true
            try {
                val m = permintaanTerakhir ?: return@launch
                repo.buatKoleksi(m.nama, m.deskripsi, m.label, m.warna, m.tipe, m.kunci)
                _tawarkanVaultBaru.value = false
                _pesan.value = "Koleksi \"${m.nama}\" dibuat di Telegram."
                tundaBerhasil?.invoke()
                tundaBerhasil = null
                permintaanTerakhir = null
            } catch (e: GalatButuhVaultBaru) {
                // Vault rusak + dipakai koleksi lain: vault lama dipertahankan,
                // UI menawarkan tombol aksi (pesan dibawa apa adanya).
                _tawarkanVaultBaru.value = true
                _pesan.value = "Gagal membuat koleksi: ${e.pesan}"
            } catch (e: Exception) {
                _tawarkanVaultBaru.value = false
                _pesan.value = "Gagal membuat koleksi: " +
                    PenjagaVault.terjemahkanGalatForum(e.message ?: "galat tak dikenal")
            } finally {
                _memuat.value = false
            }
        }
    }

    /**
     * Aksi tombol "Buat vault baru & coba lagi": buat vault forum baru,
     * jadikan aktif, lalu ulangi pembuatan koleksi dengan data form yang sama.
     * Vault lama + koleksinya tidak dihapus/diutak-atik.
     */
    fun buatVaultBaruDanCobaLagi() {
        val m = permintaanTerakhir ?: return
        viewModelScope.launch {
            _memuat.value = true
            try {
                val hasil = repo.buatVaultBaru()
                repo.buatKoleksi(m.nama, m.deskripsi, m.label, m.warna, m.tipe, m.kunci)
                _tawarkanVaultBaru.value = false
                _pesan.value = if (hasil.dibuatBaru) {
                    "Vault baru dibuat; koleksi \"${m.nama}\" dibuat di Telegram."
                } else {
                    "Vault \"${hasil.nama}\" sudah ada, dipakai lagi; " +
                        "koleksi \"${m.nama}\" dibuat di Telegram."
                }
                tundaBerhasil?.invoke()
                tundaBerhasil = null
                permintaanTerakhir = null
            } catch (e: GalatButuhVaultBaru) {
                _tawarkanVaultBaru.value = true
                _pesan.value = "Gagal membuat koleksi: ${e.pesan}"
            } catch (e: Exception) {
                _tawarkanVaultBaru.value = false
                _pesan.value = "Gagal membuat koleksi: " +
                    PenjagaVault.terjemahkanGalatForum(e.message ?: "galat tak dikenal")
            } finally {
                _memuat.value = false
            }
        }
    }

    fun ubahKoleksi(
        kunci: KunciKoleksi,
        nama: String,
        deskripsi: String,
        label: String = "",
        warna: Int = 0,
        tipe: Set<TipeFile> = emptySet(),
    ) {
        if (nama.isBlank()) {
            _pesan.value = "Nama koleksi belum diisi."
            return
        }
        viewModelScope.launch {
            _memuat.value = true
            try {
                repo.ubahKoleksi(kunci, nama.trim(), deskripsi.trim(), label, warna, tipe)
                _pesan.value = "Koleksi diperbarui."
            } catch (e: Exception) {
                _pesan.value = "Gagal memperbarui koleksi: ${e.message}"
            } finally {
                _memuat.value = false
            }
        }
    }

    /** Periksa semua kategori dan buat ulang topik yang hilang di Telegram. */
    fun perbaikiTopikHilang() {
        viewModelScope.launch {
            _memuat.value = true
            try {
                val n = repo.perbaikiTopikHilang()
                _pesan.value = if (n > 0) {
                    "$n topik dibuat ulang di Telegram."
                } else {
                    "Semua topik kategori sudah ada di Telegram."
                }
            } catch (e: Exception) {
                _pesan.value = "Gagal memeriksa topik: " +
                    PenjagaVault.terjemahkanGalatForum(e.message ?: "galat tak dikenal")
            } finally {
                _memuat.value = false
            }
        }
    }

    fun hapusKoleksi(kunci: KunciKoleksi, tinggalkanChannel: Boolean) {
        viewModelScope.launch {
            _memuat.value = true
            try {
                repo.hapusKoleksi(kunci, tinggalkanChannel)
                _pesan.value = "Koleksi dihapus dari arsip."
            } catch (e: Exception) {
                _pesan.value = "Gagal menghapus koleksi: ${e.message}"
            } finally {
                _memuat.value = false
            }
        }
    }

    // ---- Kunci koleksi (sumber bersama untuk Katalog, Pohon, Kelola, Unggah) ----

    /** Koleksi yang sudah dibuka sesi ini (mengikuti repository). */
    val koleksiTerbuka: StateFlow<Set<KunciKoleksi>> = repo.koleksiTerbuka

    /** True bila koleksi terkunci DAN belum dibuka sesi ini. */
    fun perluBukaKunci(entitas: KoleksiEntity?): Boolean = repo.perluBukaKunci(entitas)

    /** Cari entity koleksi untuk satu berkas (untuk gating ketuk berkas). */
    fun entitasUntuk(chatId: Long, topicId: Int): KoleksiEntity? =
        koleksi.value.firstOrNull { it.chatId == chatId && it.topicId == topicId }

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
     * Ganti/hapus kunci koleksi ([kunciBaru] null = hapus). Ubah/hapus wajib
     * verifikasi kunci lama dulu bila terkunci (ditegakkan repository).
     */
    fun aturKunci(kunci: KunciKoleksi, rahasiaLama: String?, kunciBaru: KunciBaru?) {
        viewModelScope.launch {
            _memuat.value = true
            try {
                repo.ubahKunci(kunci, rahasiaLama, kunciBaru)
                _pesan.value = if (kunciBaru == null) {
                    "Kunci koleksi dihapus."
                } else {
                    "Kunci koleksi diperbarui."
                }
            } catch (e: TelegramException) {
                _pesan.value = e.pesan
            } catch (e: Exception) {
                _pesan.value = e.message ?: "Gagal mengubah kunci."
            } finally {
                _memuat.value = false
            }
        }
    }

    fun habiskanPesan() {
        _pesan.value = null
    }

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            DasborViewModel(wadah.arsipRepository, wadah.pengaturan)
        }
    }
}
