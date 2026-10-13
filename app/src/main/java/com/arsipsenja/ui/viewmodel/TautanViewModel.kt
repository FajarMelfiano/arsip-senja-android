package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.TautanEntity
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.telegram.TelegramException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel layar tautan: daftar tautan pesan & undangan, pembuatan,
 * penyalinan (lewat UI), pencabutan, dan pembersihan catatan.
 */
class TautanViewModel(
    private val repo: ArsipRepository,
) : ViewModel() {

    val tautan: StateFlow<List<TautanEntity>> = repo.alirTautan()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val koleksi: StateFlow<List<KoleksiEntity>> = repo.alirKoleksi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pesan = MutableStateFlow<String?>(null)
    val pesan: StateFlow<String?> = _pesan.asStateFlow()

    private val _sibuk = MutableStateFlow(false)
    val sibuk: StateFlow<Boolean> = _sibuk.asStateFlow()

    private val _hasilBaru = MutableStateFlow<String?>(null)
    val hasilBaru: StateFlow<String?> = _hasilBaru.asStateFlow()

    fun buatTautanUndangan(chatId: Long) {
        viewModelScope.launch {
            _sibuk.value = true
            try {
                _hasilBaru.value = repo.buatTautanUndangan(chatId)
            } catch (e: TelegramException) {
                _pesan.value = "Gagal membuat tautan undangan: ${e.pesan}"
            } catch (e: Exception) {
                _pesan.value = "Gagal membuat tautan undangan: ${e.message}"
            } finally {
                _sibuk.value = false
            }
        }
    }

    fun cabutTautan(id: Long) {
        viewModelScope.launch {
            _sibuk.value = true
            try {
                repo.cabutTautan(id)
                _pesan.value = "Tautan dicabut dan tak lagi berlaku."
            } catch (e: TelegramException) {
                _pesan.value = "Gagal mencabut tautan: ${e.pesan}"
            } catch (e: Exception) {
                _pesan.value = "Gagal mencabut tautan: ${e.message}"
            } finally {
                _sibuk.value = false
            }
        }
    }

    fun hapusCatatan(id: Long) {
        viewModelScope.launch {
            try {
                repo.hapusCatatanTautan(id)
                _pesan.value = "Catatan tautan dihapus."
            } catch (e: Exception) {
                _pesan.value = "Gagal menghapus: ${e.message}"
            }
        }
    }

    fun habiskanPesan() {
        _pesan.value = null
    }

    fun habiskanHasil() {
        _hasilBaru.value = null
    }

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            TautanViewModel(wadah.arsipRepository)
        }
    }
}
