package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.telegram.ProgresBerkas
import com.arsipsenja.data.telegram.TelegramClient
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine

/**
 * ViewModel layar unggah: pemilihan koleksi tujuan, antrean WorkManager,
 * dan aliran progres transfer langsung dari TDLib.
 */
class UnggahViewModel(
    private val repo: ArsipRepository,
    private val workManager: WorkManager,
    private val telegram: TelegramClient,
) : ViewModel() {

    val koleksi: StateFlow<List<KoleksiEntity>> = repo.alirKoleksi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _koleksiTerpilih = MutableStateFlow<Long?>(null)
    val koleksiTerpilih: StateFlow<Long?> = _koleksiTerpilih.asStateFlow()

    private val _pesan = MutableStateFlow<String?>(null)
    val pesan: StateFlow<String?> = _pesan.asStateFlow()

    /** Antrean pekerjaan unggah (baru → lama), termasuk yang sudah selesai. */
    val antrean: StateFlow<List<WorkInfo>> =
        workManager.getWorkInfosByTagFlow(ArsipRepository.TAG_UNGGAH)
            .map { daftar ->
                daftar.sortedByDescending { it.state == WorkInfo.State.RUNNING }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _transfer = MutableStateFlow<Map<Int, ProgresBerkas>>(emptyMap())

    /** Transfer berkas yang sedang berjalan (fileId → progres terakhir). */
    val transfer: StateFlow<Map<Int, ProgresBerkas>> = _transfer.asStateFlow()

    init {
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
            repo.alirKoleksi().collect { daftar ->
                if (_koleksiTerpilih.value == null) {
                    _koleksiTerpilih.value = daftar.firstOrNull()?.chatId
                }
            }
        }
    }

    fun pilihKoleksi(chatId: Long?) {
        _koleksiTerpilih.value = chatId
    }

    /** Antrekan URI SAF terpilih ke koleksi tujuan. */
    fun mulaiUnggah(uris: List<android.net.Uri>) {
        val tujuan = _koleksiTerpilih.value
        if (tujuan == null) {
            _pesan.value = "Pilih koleksi tujuan terlebih dahulu."
            return
        }
        if (uris.isEmpty()) {
            _pesan.value = "Belum ada berkas yang dipilih."
            return
        }
        repo.mulaiUnggah(uris, tujuan)
        _pesan.value = "${uris.size} berkas masuk antrean unggah."
    }

    fun batalkan(id: UUID) {
        workManager.cancelWorkById(id)
        _pesan.value = "Pekerjaan unggah dibatalkan."
    }

    fun batalkanSemua() {
        workManager.cancelAllWorkByTag(ArsipRepository.TAG_UNGGAH)
        _transfer.value = emptyMap()
        _pesan.value = "Seluruh antrean unggah dibatalkan."
    }

    fun habiskanPesan() {
        _pesan.value = null
    }

    /** Gabungkan nama koleksi terpilih untuk label layar. */
    val namaKoleksiTerpilih: StateFlow<String?> =
        combine(koleksi, _koleksiTerpilih) { daftar, id ->
            daftar.firstOrNull { it.chatId == id }?.nama
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            UnggahViewModel(
                repo = wadah.arsipRepository,
                workManager = wadah.workManager,
                telegram = wadah.telegramClient,
            )
        }
    }
}
