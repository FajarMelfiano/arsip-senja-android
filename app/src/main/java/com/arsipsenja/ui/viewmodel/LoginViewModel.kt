package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.telegram.StatusOtorisasi
import com.arsipsenja.data.telegram.TelegramClient
import com.arsipsenja.data.telegram.TelegramException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel layar login: alur empat langkah TDLib —
 * kredensial api_id/api_hash → nomor → kode OTP → kata sandi 2FA (→ registrasi).
 */
class LoginViewModel(
    private val telegram: TelegramClient,
    @Suppress("unused") private val pengaturan: PengaturanStore,
) : ViewModel() {

    /** Formulir kredensial developer (my.telegram.org). */
    data class Kredensial(val apiId: String = "", val apiHash: String = "")

    private val _kredensial = MutableStateFlow(Kredensial())
    val kredensial: StateFlow<Kredensial> = _kredensial.asStateFlow()

    private val _galat = MutableStateFlow<PesanStatus?>(null)
    val galat: StateFlow<PesanStatus?> = _galat.asStateFlow()

    private val _sibuk = MutableStateFlow(false)
    val sibuk: StateFlow<Boolean> = _sibuk.asStateFlow()

    private val _nomor = MutableStateFlow("")
    val nomor: StateFlow<String> = _nomor.asStateFlow()

    private val _kode = MutableStateFlow("")
    val kode: StateFlow<String> = _kode.asStateFlow()

    private val _sandi = MutableStateFlow("")
    val sandi: StateFlow<String> = _sandi.asStateFlow()

    /** Nama untuk registrasi akun baru (nomor tak dikenal TDLib). */
    data class Registrasi(val depan: String = "", val belakang: String = "")

    private val _registrasi = MutableStateFlow(Registrasi())
    val registrasi: StateFlow<Registrasi> = _registrasi.asStateFlow()

    val status: StateFlow<StatusOtorisasi> = telegram.status.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        StatusOtorisasi.Menyiapkan,
    )

    // ---- Kredensial ----

    fun ubahApiId(nilai: String) {
        _kredensial.update { it.copy(apiId = nilai.filter(Char::isDigit).take(10)) }
        _galat.value = null
    }

    fun ubahApiHash(nilai: String) {
        _kredensial.update { it.copy(apiHash = nilai.trim()) }
        _galat.value = null
    }

    /** Validasi & simpan kredensial; klien TDLib lalu start otomatis. */
    fun simpanKredensial() {
        val id = _kredensial.value.apiId.toIntOrNull()
        val hash = _kredensial.value.apiHash
        when {
            id == null || id <= 0 ->
                _galat.value = PesanStatus(NadaStatus.GALAT, "api_id belum diisi dengan benar.")
            hash.length < 16 ->
                _galat.value = PesanStatus(
                    NadaStatus.GALAT,
                    "api_hash minimal 16 karakter. Salin persis dari my.telegram.org.",
                )
            else -> {
                _galat.value = null
                jalankan { telegram.simpanKredensialDanMulai(id, hash) }
            }
        }
    }

    // ---- Langkah otorisasi ----

    fun ubahNomor(nilai: String) {
        _nomor.value = nilai.filter { it.isDigit() || it == '+' || it == ' ' || it == '-' }
        _galat.value = null
    }

    fun kirimNomor() {
        val nilai = _nomor.value.trim()
        if (nilai.filter(Char::isDigit).length < 6) {
            _galat.value = PesanStatus(NadaStatus.GALAT, "Nomor telepon belum lengkap.")
            return
        }
        jalankan { telegram.setNomorTelepon(nilai) }
    }

    fun ubahKode(nilai: String) {
        _kode.value = nilai.filter { it.isDigit() || it == '-' }.take(8)
        _galat.value = null
    }

    fun kirimKode() {
        val nilai = _kode.value.trim()
        if (nilai.length < 4) {
            _galat.value = PesanStatus(NadaStatus.GALAT, "Kode verifikasi minimal 4 digit.")
            return
        }
        jalankan { telegram.cekKode(nilai) }
    }

    fun ubahSandi(nilai: String) {
        _sandi.value = nilai
        _galat.value = null
    }

    fun kirimSandi() {
        if (_sandi.value.isEmpty()) {
            _galat.value = PesanStatus(NadaStatus.GALAT, "Kata sandi belum diisi.")
            return
        }
        jalankan { telegram.cekSandi(_sandi.value) }
    }

    fun ubahNamaDepan(nilai: String) =
        _registrasi.update { it.copy(depan = nilai.trimStart().take(64)) }

    fun ubahNamaBelakang(nilai: String) =
        _registrasi.update { it.copy(belakang = nilai.trimStart().take(64)) }

    fun daftarkanAkun() {
        val r = _registrasi.value
        if (r.depan.isBlank()) {
            _galat.value = PesanStatus(NadaStatus.GALAT, "Nama depan wajib diisi.")
            return
        }
        jalankan { telegram.daftarkanAkun(r.depan, r.belakang) }
    }

    fun tanganiGalat() {
        _galat.value = null
    }

    private fun jalankan(blok: suspend () -> Unit) {
        if (_sibuk.value) return
        viewModelScope.launch {
            _sibuk.value = true
            try {
                blok()
            } catch (e: TelegramException) {
                _galat.value = PesanStatus(NadaStatus.GALAT, e.pesan)
            } catch (e: Exception) {
                _galat.value = PesanStatus(
                    NadaStatus.GALAT,
                    e.message ?: "Galat tak terduga. Coba lagi.",
                )
            } finally {
                _sibuk.value = false
            }
        }
    }

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            LoginViewModel(wadah.telegramClient, wadah.pengaturan)
        }
    }
}
