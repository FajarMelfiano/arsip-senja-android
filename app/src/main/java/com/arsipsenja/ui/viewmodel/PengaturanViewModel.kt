package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.local.StatistikBerkas
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.repo.PenjagaVault
import com.arsipsenja.data.repo.StatusRealtime
import com.arsipsenja.data.telegram.StatusOtorisasi
import com.arsipsenja.data.telegram.TelegramClient
import com.arsipsenja.data.telegram.TelegramException
import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.TampilanDasbor
import com.arsipsenja.util.UrutBerkas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel pengaturan: tampilan dasbor, urutan, kunci biometrik,
 * kredensial API, statistik arsip, dan keluar dari sesi Telegram.
 */
class PengaturanViewModel(
    private val repo: ArsipRepository,
    private val pengaturan: PengaturanStore,
    private val telegram: TelegramClient,
) : ViewModel() {

    val tampilan: StateFlow<TampilanDasbor> = pengaturan.tampilanDasbor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TampilanDasbor.KISI)

    val urut: StateFlow<UrutBerkas> = pengaturan.urutBerkas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UrutBerkas.TANGGAL)

    val arah: StateFlow<ArahUrut> = pengaturan.arahUrut
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArahUrut.TURUN)

    val biometrik: StateFlow<Boolean> = pengaturan.biometrikAktif
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val statistik: StateFlow<StatistikBerkas> = repo.alirStatistik()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatistikBerkas(0, 0L))

    val statusSesi: StateFlow<StatusOtorisasi> = telegram.status
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatusOtorisasi.Menyiapkan)

    // ---- Vault Telegram ----

    private val _vaultAktif = MutableStateFlow<Long?>(null)
    val vaultAktif: StateFlow<Long?> = _vaultAktif.asStateFlow()

    private val _kandidatVault =
        MutableStateFlow<List<ArsipRepository.KandidatVault>>(emptyList())
    val kandidatVault: StateFlow<List<ArsipRepository.KandidatVault>> =
        _kandidatVault.asStateFlow()

    private val _memuatVault = MutableStateFlow(false)
    val memuatVault: StateFlow<Boolean> = _memuatVault.asStateFlow()

    private val _pesanVault = MutableStateFlow<PesanStatus?>(null)
    val pesanVault: StateFlow<PesanStatus?> = _pesanVault.asStateFlow()

    /** Alasan terakhir gagal memuat daftar grup (ditampilkan di dialog). */
    private val _galatKandidat = MutableStateFlow<String?>(null)
    val galatKandidat: StateFlow<String?> = _galatKandidat.asStateFlow()

    /** Nama grup + status kesehatan vault aktif (nama via GetChat). */
    private val _infoVault = MutableStateFlow<ArsipRepository.InfoVault?>(null)
    val infoVault: StateFlow<ArsipRepository.InfoVault?> = _infoVault.asStateFlow()

    /** Status mesin realtime (untuk baris "Terakhir diperbarui"). */
    val statusRealtime: StateFlow<StatusRealtime> = repo.statusRealtime

    // Pemuatan pertama TIDAK di init: PengaturanScreen memanggil
    // muatVaultAktif()/muatInfoVault() lewat LaunchedEffect tiap kali
    // ditampilkan (termasuk pertama kali). Pemuatan ganda di sini akan
    // menjalankan deteksi vault dua kali bersamaan.

    fun muatVaultAktif() {
        viewModelScope.launch {
            _vaultAktif.value = runCatching { repo.vaultTerpilih() }.getOrNull()
        }
    }

    /**
     * Muat ulang nama + status kesehatan vault aktif. Dijalankan tiap
     * kali Pengaturan ditampilkan (LaunchedEffect) agar status tidak basi.
     * Sebelum cek kesehatan, jalankan auto-adopt: vault rusak (mis. channel)
     * diganti otomatis dengan vault sehat milik sendiri bila aman.
     * Bila pengecekan gagal, PERTAHANKAN info lama dan tampilkan alasan
     * gagalnya — kegagalan pengecekan bukan berarti vault "Bermasalah".
     */
    fun muatInfoVault() {
        viewModelScope.launch {
            try {
                when (val hasil = repo.periksaDanAdopsiVault()) {
                    is ArsipRepository.HasilPeriksaVault.Diadopsi -> {
                        _vaultAktif.value = hasil.kandidat.chatId
                        _pesanVault.value = PesanStatus(
                            NadaStatus.INFO,
                            PenjagaVault.pesanAdopsiOtomatis(hasil.kandidat.nama),
                        )
                    }
                    is ArsipRepository.HasilPeriksaVault.BanyakKandidat -> {
                        _pesanVault.value = PesanStatus(
                            NadaStatus.INFO,
                            PenjagaVault.pesanBanyakKandidat(hasil.jumlah),
                        )
                    }
                    ArsipRepository.HasilPeriksaVault.TidakAdaAksi -> Unit
                }
                _infoVault.value = repo.infoVaultAktif()
            } catch (e: Exception) {
                _pesanVault.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal memeriksa status vault: ${e.message}. " +
                        "Coba buka lagi Pengaturan.",
                )
            }
        }
    }

    fun muatKandidatVault() {
        viewModelScope.launch {
            _memuatVault.value = true
            try {
                _kandidatVault.value = repo.daftarKandidatVault()
                _galatKandidat.value = null
                _pesanVault.value = null
            } catch (e: Exception) {
                // Jangan biarkan dialog menampilkan empty state yang
                // menyesatkan saat pemuatan yang gagal — tampilkan alasannya.
                _galatKandidat.value = "Gagal memuat grup: ${e.message}"
                _pesanVault.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal memuat grup: ${e.message}",
                )
            } finally {
                _memuatVault.value = false
            }
        }
    }

    fun pilihVault(chatId: Long) {
        viewModelScope.launch {
            _memuatVault.value = true
            try {
                val forumBaru = repo.pilihVault(chatId)
                _vaultAktif.value = chatId
                _pesanVault.value = PesanStatus(
                    NadaStatus.SUKSES,
                    if (forumBaru) {
                        "Mode forum diaktifkan di grup ini. " +
                            "Koleksi baru akan dibuat sebagai topik."
                    } else {
                        "Vault diganti. Koleksi baru akan dibuat sebagai topik di grup ini."
                    },
                )
                // Optimistis: tampilkan nama + status baru SEKARANG, jangan
                // tunggu refresh async — sebelumnya banner "Vault diganti"
                // tampil sementara baris vault masih menunjukkan nama lama.
                val kandidat = _kandidatVault.value.firstOrNull { it.chatId == chatId }
                _infoVault.value = ArsipRepository.InfoVault(
                    chatId = chatId,
                    nama = kandidat?.nama ?: "Grup $chatId",
                    forumSiap = forumBaru || kandidat?.forumSiap == true,
                    masalah = null,
                )
                muatInfoVault()
            } catch (e: Exception) {
                _pesanVault.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal memilih vault: ${e.message}",
                )
            } finally {
                _memuatVault.value = false
            }
        }
    }

    /** Satu ketukan "Perbaiki": validasi + aktivasi forum vault aktif, lalu refresh. */
    fun perbaikiVault() {
        viewModelScope.launch {
            _memuatVault.value = true
            try {
                val info = repo.perbaikiVaultAktif()
                _vaultAktif.value = info.chatId
                _infoVault.value = info
                _pesanVault.value = if (info.forumSiap) {
                    PesanStatus(
                        NadaStatus.SUKSES,
                        "Vault \"${info.nama}\" sudah forum dan siap dipakai.",
                    )
                } else {
                    PesanStatus(
                        NadaStatus.GALAT,
                        "Vault belum siap: ${info.masalah ?: "periksa lagi nanti."}",
                    )
                }
            } catch (e: Exception) {
                _pesanVault.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal memperbaiki vault: ${e.message}",
                )
            } finally {
                _memuatVault.value = false
            }
        }
    }

    fun buatVaultBaru(nama: String) {
        val bersih = nama.trim().ifBlank { "Arsip Senja" }
        viewModelScope.launch {
            _memuatVault.value = true
            try {
                val hasil = repo.buatVaultBaru(bersih)
                _vaultAktif.value = hasil.chatId
                _pesanVault.value = if (hasil.dibuatBaru) {
                    PesanStatus(
                        NadaStatus.SUKSES,
                        "Vault \"${hasil.nama}\" dibuat dan dijadikan aktif.",
                    )
                } else {
                    PesanStatus(
                        NadaStatus.INFO,
                        PenjagaVault.pesanVaultSudahAda(hasil.nama),
                    )
                }
                // Optimistis: dedupe hanya mengadopsi kandidat forum-siap
                // dan pembuatan baru sudah diverifikasi sebagai grup forum
                // di TelegramClient. Refresh async tetap mengoreksi bila
                // perlu — tanpa ini baris vault sempat menampilkan nama
                // vault lama sementara banner sudah mengumumkan yang baru.
                _infoVault.value = ArsipRepository.InfoVault(
                    chatId = hasil.chatId,
                    nama = hasil.nama,
                    forumSiap = true,
                    masalah = null,
                )
                muatInfoVault()
            } catch (e: Exception) {
                _pesanVault.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal membuat vault: ${e.message}",
                )
            } finally {
                _memuatVault.value = false
            }
        }
    }

    fun habiskanPesanVault() {
        _pesanVault.value = null
    }

    private val _adaKredensial = MutableStateFlow(pengaturan.adaKredensial())
    val adaKredensial: StateFlow<Boolean> = _adaKredensial.asStateFlow()

    private val _galat = MutableStateFlow<PesanStatus?>(null)
    val galat: StateFlow<PesanStatus?> = _galat.asStateFlow()

    private val _sibuk = MutableStateFlow(false)
    val sibuk: StateFlow<Boolean> = _sibuk.asStateFlow()

    fun setTampilan(tampilan: TampilanDasbor) {
        viewModelScope.launch { pengaturan.setTampilan(tampilan) }
    }

    fun setUrutan(urut: UrutBerkas, arah: ArahUrut) {
        viewModelScope.launch { pengaturan.setUrutan(urut, arah) }
    }

    fun setBiometrik(aktif: Boolean) {
        viewModelScope.launch { pengaturan.setBiometrik(aktif) }
    }

    /** Ganti kredensial API; sesi lama ditutup lalu klien start ulang. */
    fun gantiKredensial(apiId: String, apiHash: String) {
        val id = apiId.toIntOrNull()
        when {
            id == null || id <= 0 -> _galat.value = PesanStatus(
                NadaStatus.GALAT,
                "api_id belum diisi dengan benar.",
            )
            apiHash.trim().length < 16 ->
                _galat.value = PesanStatus(
                    NadaStatus.GALAT,
                    "api_hash minimal 16 karakter.",
                )
            else -> {
                _sibuk.value = true
                viewModelScope.launch {
                    try {
                        telegram.simpanKredensialDanMulai(id, apiHash.trim())
                        _adaKredensial.value = pengaturan.adaKredensial()
                        _galat.value = null
                    } catch (e: TelegramException) {
                        _galat.value = PesanStatus(NadaStatus.GALAT, e.pesan)
                    } catch (e: Exception) {
                        _galat.value = PesanStatus(
                            NadaStatus.GALAT,
                            e.message ?: "Gagal menyimpan kredensial.",
                        )
                    } finally {
                        _sibuk.value = false
                    }
                }
            }
        }
    }

    /** Keluar dari sesi Telegram (kredensial API tetap tersimpan). */
    fun keluarSesi() {
        viewModelScope.launch {
            _sibuk.value = true
            try {
                telegram.keluar()
            } finally {
                _sibuk.value = false
            }
        }
    }

    fun sinkronSemua() {
        viewModelScope.launch {
            _sibuk.value = true
            try {
                repo.sinkronSemua()
                _galat.value = null
            } catch (e: Exception) {
                _galat.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Sinkronisasi gagal: ${e.message}",
                )
            } finally {
                _sibuk.value = false
            }
        }
    }

    fun tanganiGalat() {
        _galat.value = null
    }

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            PengaturanViewModel(
                repo = wadah.arsipRepository,
                pengaturan = wadah.pengaturan,
                telegram = wadah.telegramClient,
            )
        }
    }
}
