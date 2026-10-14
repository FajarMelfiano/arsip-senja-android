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
import kotlinx.coroutines.flow.stateIn
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

    private val _pesan = MutableStateFlow<String?>(null)
    val pesan: StateFlow<String?> = _pesan.asStateFlow()

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
     */
    fun tarik() {
        if (_memuat.value) return
        viewModelScope.launch {
            _memuat.value = true
            try {
                val daftar = repo.tarikDaftarKoleksi()
                _pesan.value = if (daftar.isEmpty()) {
                    "Tidak ada koleksi baru dari Telegram."
                } else {
                    "${daftar.size} koleksi impor diperbarui."
                }
            } catch (e: TelegramException) {
                _pesan.value = "Tarik gagal: ${e.pesan}"
            } catch (e: Exception) {
                _pesan.value = "Tarik gagal: " +
                    PenjagaVault.terjemahkanGalatForum(e.message ?: "galat tak dikenal")
            } finally {
                _memuat.value = false
            }
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
                    "Sinkron impor selesai: $baru berkas baru."
                } else {
                    "Sinkron impor selesai: tidak ada berkas baru."
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

    fun habiskanPesan() {
        _pesan.value = null
    }

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            ImporViewModel(wadah.arsipRepository)
        }
    }
}
