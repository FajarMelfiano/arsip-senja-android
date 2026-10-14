package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.KueriTampilan
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.ui.pohon.PohonArsip
import com.arsipsenja.ui.pohon.SimpulKoleksi
import com.arsipsenja.ui.pohon.SimpulPohon
import com.arsipsenja.ui.pohon.SimpulTipe
import com.arsipsenja.ui.pohon.bangunPohon
import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.UrutBerkas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel halaman Pohon: menggabungkan koleksi + seluruh berkas menjadi
 * pohon turunan (Vault → Koleksi → Tipe → Berkas).
 *
 * Status buka/tutup disimpan di sini sebagai peta idSimpul → terbuka
 * (bukan di builder murni). Saat pencarian aktif, cabang yang cocok
 * ditampilkan terbuka otomatis agar hasil langsung terlihat.
 */
class PohonArsipViewModel(
    private val repo: ArsipRepository,
) : ViewModel() {

    private val _kataCari = MutableStateFlow("")
    val kataCari: StateFlow<String> = _kataCari.asStateFlow()

    private val _terbuka = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val terbuka: StateFlow<Map<String, Boolean>> = _terbuka.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pohon: StateFlow<PohonArsip> =
        combine(
            repo.alirKoleksi(),
            repo.alirBerkas(KueriTampilan(urut = UrutBerkas.NAMA, arah = ArahUrut.NAIK)),
            _kataCari,
            repo.koleksiTerbuka,
        ) { koleksi, berkas, cari, terbuka ->
            // Berkas milik koleksi terkunci yang belum dibuka sesi ini
            // disembunyikan dari pohon (simpul koleksi tetap tampil sebagai
            // "Terkunci" tanpa anak — ditentukan di layar via perluBukaKunci).
            val kunciTerkunci = koleksi
                .filter { it.terkunci && it.kunci !in terbuka }
                .map { it.chatId to it.topicId }
                .toSet()
            val terlihat = berkas.filter { b ->
                val induk = b.koleksiId ?: b.chatId
                (induk to b.topicId) !in kunciTerkunci
            }
            bangunPohon(koleksi, terlihat, cari)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PohonArsip.kosong())

    fun ubahCari(nilai: String) {
        _kataCari.value = nilai
    }

    fun kosongkanCari() {
        _kataCari.value = ""
    }

    /** Buka/tutup satu simpul koleksi atau tipe. */
    fun jungkit(idSimpul: String) {
        _terbuka.update { it + (idSimpul to !(it[idSimpul] ?: false)) }
    }

    /** Buka semua simpul koleksi + tipe pada pohon yang sedang tampil. */
    fun bukaSemua() {
        _terbuka.value = kumpulkanIdDapatDibuka(pohon.value.akar).associateWith { true }
    }

    /** Tutup semua simpul (kembali ke tampilan ringkas). */
    fun tutupSemua() {
        _terbuka.value = emptyMap()
    }

    // ---- Kunci koleksi (sumber yang sama dengan Dasbor) ----

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

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            PohonArsipViewModel(wadah.arsipRepository)
        }
    }
}

/** Kumpulkan id semua simpul koleksi + tipe dalam pohon (untuk "Buka semua"). */
private fun kumpulkanIdDapatDibuka(akar: SimpulPohon): List<String> {
    val hasil = mutableListOf<String>()
    fun telusur(simpul: SimpulPohon) {
        if (simpul is SimpulKoleksi || simpul is SimpulTipe) hasil += simpul.idSimpul
        simpul.anak.forEach(::telusur)
    }
    telusur(akar)
    return hasil
}
