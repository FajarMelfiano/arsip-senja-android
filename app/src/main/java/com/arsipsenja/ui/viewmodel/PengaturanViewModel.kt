package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.local.StatistikBerkas
import com.arsipsenja.data.repo.ArsipRepository
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

    private val _adaKredensial = MutableStateFlow(pengaturan.adaKredensial())
    val adaKredensial: StateFlow<Boolean> = _adaKredensial.asStateFlow()

    private val _galat = MutableStateFlow<String?>(null)
    val galat: StateFlow<String?> = _galat.asStateFlow()

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
            id == null || id <= 0 -> _galat.value = "api_id belum diisi dengan benar."
            apiHash.trim().length < 16 ->
                _galat.value = "api_hash minimal 16 karakter."
            else -> {
                _sibuk.value = true
                viewModelScope.launch {
                    try {
                        telegram.simpanKredensialDanMulai(id, apiHash.trim())
                        _adaKredensial.value = pengaturan.adaKredensial()
                        _galat.value = null
                    } catch (e: TelegramException) {
                        _galat.value = e.pesan
                    } catch (e: Exception) {
                        _galat.value = e.message ?: "Gagal menyimpan kredensial."
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
                _galat.value = "Sinkronisasi gagal: ${e.message}"
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
