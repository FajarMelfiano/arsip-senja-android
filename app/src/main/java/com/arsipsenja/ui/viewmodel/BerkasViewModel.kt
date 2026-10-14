@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.local.TautanEntity
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.telegram.ProgresBerkas
import com.arsipsenja.data.telegram.TelegramClient
import com.arsipsenja.data.telegram.TelegramException
import com.arsipsenja.util.StatusLokal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel detail berkas: metadata katalog, pratinjau, unduhan,
 * dan pengelolaan tautan berbagi milik berkas ini.
 */
class BerkasViewModel(
    private val repo: ArsipRepository,
    private val workManager: WorkManager,
    private val telegram: TelegramClient,
    @Suppress("unused") private val pengaturan: PengaturanStore,
    private val berkasId: String,
) : ViewModel() {

    val berkas: StateFlow<BerkasArsipEntity?> = repo.alirBerkasId(berkasId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val koleksi: StateFlow<KoleksiEntity?> = berkas
        .flatMapLatest { b ->
            if (b == null) flowOf(null)
            else repo.alirKoleksi(KunciKoleksi(b.chatId, b.topicId))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tautan: StateFlow<List<TautanEntity>> = repo.alirTautanBerkas(berkasId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Info pekerjaan unduhan unik berkas ini (null bila tak pernah diminta). */
    val unduhan: StateFlow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(ArsipRepository.namaKerjaUnduh(berkasId))
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Progres transfer TDLib terkini untuk berkas ini. */
    val progres: StateFlow<ProgresBerkas?> = berkas
        .flatMapLatest { b ->
            if (b == null || b.tdFileId == 0) flowOf(null)
            else telegram.progresBerkas.filter { it.fileId == b.tdFileId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _pesan = MutableStateFlow<PesanStatus?>(null)
    val pesan: StateFlow<PesanStatus?> = _pesan.asStateFlow()

    private val _sibuk = MutableStateFlow(false)
    val sibuk: StateFlow<Boolean> = _sibuk.asStateFlow()

    private val _tautanBaru = MutableStateFlow<String?>(null)
    val tautanBaru: StateFlow<String?> = _tautanBaru.asStateFlow()

    /**
     * Status penyimpanan perangkat berdasarkan jalur lokal, progres unduh,
     * dan status pekerjaan WorkManager.
     */
    fun statusLokal(): StatusLokal {
        val b = berkas.value
        val kerja = unduhan.value
        val p = progres.value
        return when {
            b != null && !b.tdLocalPath.isNullOrBlank() -> StatusLokal.ADA
            p != null && !p.selesaiUnduh -> StatusLokal.PROSES
            kerja?.state == WorkInfo.State.RUNNING -> StatusLokal.PROSES
            kerja?.state == WorkInfo.State.ENQUEUED -> StatusLokal.PROSES
            else -> StatusLokal.BELUM
        }
    }

    fun mulaiUnduh() {
        viewModelScope.launch {
            _sibuk.value = true
            try {
                repo.mulaiUnduh(berkasId)
                _pesan.value = PesanStatus(NadaStatus.SUKSES, "Unduhan masuk antrean.")
            } catch (e: Exception) {
                _pesan.value = PesanStatus(NadaStatus.GALAT, "Gagal memulai unduhan: ${e.message}")
            } finally {
                _sibuk.value = false
            }
        }
    }

    fun buatTautanPesan() {
        viewModelScope.launch {
            _sibuk.value = true
            try {
                _tautanBaru.value = repo.buatTautanPesan(berkasId)
            } catch (e: TelegramException) {
                _pesan.value = PesanStatus(NadaStatus.GALAT, "Gagal membuat tautan: ${e.pesan}")
            } catch (e: Exception) {
                _pesan.value = PesanStatus(NadaStatus.GALAT, "Gagal membuat tautan: ${e.message}")
            } finally {
                _sibuk.value = false
            }
        }
    }

    fun hapusTautan(id: Long) {
        viewModelScope.launch {
            try {
                repo.hapusCatatanTautan(id)
                _pesan.value = PesanStatus(NadaStatus.SUKSES, "Catatan tautan dihapus.")
            } catch (e: Exception) {
                _pesan.value = PesanStatus(NadaStatus.GALAT, "Gagal menghapus tautan: ${e.message}")
            }
        }
    }

    fun hapusBerkas(cabutJuga: Boolean = false) {
        viewModelScope.launch {
            _sibuk.value = true
            try {
                if (cabutJuga) {
                    tautan.value.filterNot { it.dicabut }.forEach { t ->
                        runCatching { repo.cabutTautan(t.id) }
                    }
                }
                repo.hapusBerkas(berkasId)
                _pesan.value = PesanStatus(NadaStatus.SUKSES, "Berkas dihapus dari indeks arsip.")
            } catch (e: Exception) {
                _pesan.value = PesanStatus(NadaStatus.GALAT, "Gagal menghapus berkas: ${e.message}")
            } finally {
                _sibuk.value = false
            }
        }
    }

    fun tandaiBelumTerunduh() {
        viewModelScope.launch { repo.tandaiLokal(berkasId, null) }
    }

    fun habiskanPesan() {
        _pesan.value = null
    }

    fun habiskanTautanBaru() {
        _tautanBaru.value = null
    }

    companion object {
        fun pabrik(berkasId: String) = pabrikKontainer { wadah ->
            BerkasViewModel(
                repo = wadah.arsipRepository,
                workManager = wadah.workManager,
                telegram = wadah.telegramClient,
                pengaturan = wadah.pengaturan,
                berkasId = berkasId,
            )
        }
    }
}
