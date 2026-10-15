@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciBaru
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.KueriTampilan
import com.arsipsenja.data.local.PenandaImpor
import com.arsipsenja.data.local.PengaturanStore
import com.arsipsenja.data.local.StatistikBerkas
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.repo.GalatButuhVaultBaru
import com.arsipsenja.data.repo.PenjagaVault
import com.arsipsenja.data.repo.StatusRealtime
import com.arsipsenja.data.telegram.TelegramException
import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.AturanSenjaMemories
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TimeZone

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

    private val _pesan = MutableStateFlow<PesanStatus?>(null)
    val pesan: StateFlow<PesanStatus?> = _pesan.asStateFlow()

    /**
     * Progres sinkron terpilih (transien, BUKAN status akhir): ditampilkan
     * sebagai teks inline di Dasbor, tidak pernah jadi popup modal —
     * popup per koleksi akan menumpuk dan memaksa ketuk berkali-kali.
     */
    private val _progres = MutableStateFlow<String?>(null)
    val progres: StateFlow<String?> = _progres.asStateFlow()
    // ---- Senja Memories: kenangan foto/video "pada hari ini" di tahun-tahun lalu ----
    private val _memories = MutableStateFlow<List<BerkasArsipEntity>>(emptyList())
    val memories: StateFlow<List<BerkasArsipEntity>> = _memories.asStateFlow()

    private val _senjaMemoriesDismissed = MutableStateFlow(false)
    val senjaMemoriesDismissed: StateFlow<Boolean> = _senjaMemoriesDismissed.asStateFlow()

    init {
        viewModelScope.launch {
            // Aliran khusus tanpa saringan pencarian: kenangan dihitung dari
            // seluruh arsip pribadi, bukan dari hasil cari yang sedang aktif.
            val awal = repo.tampilanAwal()
            combine(
                repo.alirBerkas(awal),
                repo.alirKoleksiImpor(),
                koleksi,
                koleksiTerbuka,
            ) { daftar, impor, daftarKoleksi, dibuka ->
                val kunciImpor = PenandaImpor.kunciImpor(impor)
                val kunciTerkunci =
                    daftarKoleksi.filter { it.terkunci }.map { it.kunci }.toSet()
                daftar
                    .filter { !PenandaImpor.milikImpor(it, kunciImpor) }
                    .filter {
                        it.tipe == TipeFile.GAMBAR.name || it.tipe == TipeFile.VIDEO.name
                    }
                    .filter { berkas ->
                        // Berkas koleksi terkunci hanya tampil bila vault sedang terbuka.
                        val kunci = KunciKoleksi(berkas.chatId, berkas.topicId)
                        kunci !in kunciTerkunci || kunci in dibuka
                    }
            }.collect { layak ->
                _memories.value =
                    AturanSenjaMemories.filtering(layak, TimeZone.getDefault()).take(4)
            }
        }
        viewModelScope.launch {
            _senjaMemoriesDismissed.value = pengaturan.isSenjaMemoriesDismissed()
        }
    }

    /** Antrean popup penamaan: Buat (per tipe) dan Tarik (per entri baru). */
    private val _antreanBuat = MutableStateFlow<List<ItemPenamaan>>(emptyList())
    val antreanBuat: StateFlow<List<ItemPenamaan>> = _antreanBuat.asStateFlow()

    private val _antreanTarik = MutableStateFlow<List<ItemPenamaan>>(emptyList())
    val antreanTarik: StateFlow<List<ItemPenamaan>> = _antreanTarik.asStateFlow()

    /** Semua nama koleksi (pribadi + impor) untuk validasi duplikat popup. */
    val semuaNamaKoleksi: StateFlow<List<String>> = repo.alirKoleksi()
        .map { daftar -> daftar.map { it.nama } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Item Buat yang gagal karena vault, menunggu tombol aksi ulang. */
    private var itemGagalVault: ItemPenamaan? = null

    private var dibuatBuat = 0
    private var gagalBuat = 0
    private var totalTarik = 0
    private var gagalTarik = 0

    /** Ringkasan yang menunggu popup galat ditutup dulu (latest-wins). */
    private var ringkasanBuatTertunda = false
    private var ringkasanTarikTertunda = false

    private val _koleksiAktif = MutableStateFlow<KunciKoleksi?>(null)
    val koleksiAktif: StateFlow<KunciKoleksi?> = _koleksiAktif.asStateFlow()

    /**
     * Katalog pribadi: berkas milik koleksi impor disaring keluar di sini
     * (tab Impor yang menampilkannya). Kunci impor diikuti reaktif agar
     * berkas yang baru ketahuan milik impor langsung hilang dari Dasbor.
     */
    val berkas: StateFlow<List<BerkasArsipEntity>> =
        combine(_kata.debounce(250), _saringan, repo.alirKoleksiImpor()) { kata, kueri, impor ->
            kueri.copy(kata = kata.trim().ifBlank { null }) to PenandaImpor.kunciImpor(impor)
        }
            .flatMapLatest { (kueri, kunciImpor) ->
                repo.alirBerkas(kueri).map { daftar ->
                    daftar.filter { !PenandaImpor.milikImpor(it, kunciImpor) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statistik: StateFlow<StatistikBerkas> = repo.alirStatistikPribadi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatistikBerkas(0, 0L))

    /** Hanya koleksi pribadi — hasil tarikan tampil di tab Impor. */
    val koleksi: StateFlow<List<KoleksiEntity>> = repo.alirKoleksiPribadi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tampilan: StateFlow<TampilanDasbor> = pengaturan.tampilanDasbor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TampilanDasbor.KISI)

    /** Status mesin realtime untuk indikator + dialog progres di header. */
    val statusRealtime: StateFlow<StatusRealtime> = repo.statusRealtime

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
                    _progres.value = "Menyinkron “$nama” (${i + 1}/${kunci.size})…"
                    totalBaru += repo.sinkronKoleksi(k)
                }
                _progres.value = null
                _pesan.value = if (totalBaru > 0) {
                    PesanStatus(
                        NadaStatus.SUKSES,
                        "Sinkronisasi selesai: $totalBaru berkas baru dari ${kunci.size} koleksi.",
                    )
                } else {
                    PesanStatus(
                        NadaStatus.SUKSES,
                        "Sinkronisasi selesai: tidak ada berkas baru.",
                    )
                }
            } catch (e: TelegramException) {
                _progres.value = null
                _pesan.value = PesanStatus(NadaStatus.GALAT, "Sinkronisasi gagal: ${e.pesan}")
            } catch (e: Exception) {
                _progres.value = null
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Sinkronisasi gagal: ${e.message ?: "galat tak dikenal"}",
                )
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
                _pesan.value = PesanStatus(
                    NadaStatus.SUKSES,
                    "Sinkronisasi selesai. Indeks lokal sudah segar.",
                )
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

    /**
     * Tarik dari Telegram: hasilnya masuk tab Impor (bukan daftar pribadi).
     * Entri BARU tercakup (kunci belum dikenal sebelum tarik) ditanyai
     * namanya satu per satu lewat popup; entri yang sudah tercakup tidak
     * ditanya ulang dan namanya tidak pernah ditimpa. Ringkasan tampil
     * sesudah antrean penamaan habis.
     */
    fun tarikDaftarKoleksi() {
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
                            "Tidak ada koleksi baru dari Telegram. " +
                                "Lihat tab Impor untuk hasil sebelumnya.",
                        )
                    } else {
                        PesanStatus(
                            NadaStatus.SUKSES,
                            "${daftar.size} koleksi impor diperbarui — lihat di tab Impor.",
                        )
                    }
                } else {
                    _antreanTarik.value = AntreanPenamaan.dariTarik(baru)
                }
            } catch (e: Exception) {
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal menarik daftar koleksi: " +
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
                    _pesan.value = AntreanPenamaan.ringkasanTarik(
                        totalTarik,
                        gagalTarik,
                        " — lihat di tab Impor",
                    )
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
            _pesan.value = AntreanPenamaan.ringkasanTarik(
                totalTarik,
                gagalTarik,
                " — lihat di tab Impor",
            )
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
            _pesan.value = AntreanPenamaan.ringkasanTarik(
                totalTarik,
                gagalTarik,
                " — lihat di tab Impor",
            )
        }
    }

    // ---- Koleksi: antrean penamaan per tipe ----

    /**
     * Mulai antrean penamaan: satu popup per tipe yang dicentang, urutan
     * [TipeFile.entries]. Pengaturan bersama (deskripsi/label/warna/kunci)
     * disalin ke tiap item; tiap Simpan membuat SATU topik + SATU koleksi
     * untuk tipe itu. Form pemanggil di-reset begitu antrean dimulai —
     * data sudah tersimpan di antrean, bukan di form.
     */
    fun mulaiBuatAntrean(
        deskripsi: String,
        label: String = "",
        warna: Int = 0,
        tipe: Set<TipeFile> = emptySet(),
        kunci: KunciBaru? = null,
    ) {
        if (tipe.isEmpty()) {
            _pesan.value = PesanStatus(
                NadaStatus.INFO,
                "Pilih minimal satu tipe berkas untuk koleksi baru.",
            )
            return
        }
        dibuatBuat = 0
        gagalBuat = 0
        itemGagalVault = null
        _antreanBuat.value = AntreanPenamaan.dariBuat(
            tipe,
            deskripsi.trim(),
            label,
            warna,
            kunci,
        )
    }

    /** Simpan nama untuk item Buat terdepan: SATU topik + SATU koleksi. */
    fun simpanNamaBuat(nama: String) {
        val item = _antreanBuat.value.firstOrNull() ?: return
        val tipe = item.tipe ?: return
        viewModelScope.launch {
            _memuat.value = true
            try {
                repo.buatKoleksi(
                    nama.trim(),
                    item.deskripsi,
                    item.label,
                    item.warna,
                    setOf(tipe),
                    item.kunciRahasia,
                )
                dibuatBuat++
                _antreanBuat.update { it.drop(1) }
                if (_antreanBuat.value.isEmpty()) {
                    // Ringkasan ditampilkan sekarang: flag tertunda (dari
                    // galat sebelumnya) dibersihkan agar Mengerti tidak
                    // menampilkan ringkasan yang sama untuk kedua kalinya.
                    ringkasanBuatTertunda = false
                    _pesan.value = AntreanPenamaan.ringkasanBuat(dibuatBuat, gagalBuat)
                }
            } catch (e: GalatButuhVaultBaru) {
                // Vault rusak + dipakai koleksi lain: vault lama dipertahankan,
                // popup galat menawarkan tombol aksi "Buat vault baru & coba lagi".
                // Simpan NAMA KETIKAN USER (bukan prefill label tipe) agar
                // retry memakai nama yang dipilih user di popup.
                itemGagalVault = item.copy(prefill = nama.trim())
                _antreanBuat.update { it.drop(1) }
                if (_antreanBuat.value.isEmpty()) {
                    ringkasanBuatTertunda = true
                }
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal membuat koleksi: ${e.pesan}",
                    aksiUlang = true,
                )
            } catch (e: Exception) {
                gagalBuat++
                _antreanBuat.update { it.drop(1) }
                if (_antreanBuat.value.isEmpty()) {
                    ringkasanBuatTertunda = true
                }
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal membuat koleksi: " +
                        PenjagaVault.terjemahkanGalatForum(e.message ?: "galat tak dikenal"),
                )
            } finally {
                _memuat.value = false
            }
        }
    }

    /** Lewati item Buat terdepan: tipe itu tidak dibuat. */
    fun lewatiNamaBuat() {
        _antreanBuat.update { it.drop(1) }
        if (_antreanBuat.value.isEmpty()) {
            ringkasanBuatTertunda = false
            _pesan.value = AntreanPenamaan.ringkasanBuat(dibuatBuat, gagalBuat)
        }
    }

    /**
     * Batalkan antrean Buat: berhenti, yang sudah dibuat tetap dibuat.
     * Ringkasan tampil hanya bila ada yang sudah dibuat/gagal.
     */
    fun batalkanAntreanBuat() {
        _antreanBuat.value = emptyList()
        itemGagalVault = null
        ringkasanBuatTertunda = false
        if (dibuatBuat > 0 || gagalBuat > 0) {
            _pesan.value = AntreanPenamaan.ringkasanBuat(dibuatBuat, gagalBuat)
        }
    }

    /**
     * Aksi tombol "Buat vault baru & coba lagi": buat vault forum baru,
     * jadikan aktif, lalu ulangi pembuatan item yang gagal tadi saja.
     * Vault lama + koleksinya tidak dihapus/diutak-atik.
     */
    fun buatVaultBaruDanCobaLagi() {
        val item = itemGagalVault ?: return
        val tipe = item.tipe ?: return
        viewModelScope.launch {
            _memuat.value = true
            _pesan.value = null
            try {
                val hasil = repo.buatVaultBaru()
                repo.buatKoleksi(
                    item.prefill,
                    item.deskripsi,
                    item.label,
                    item.warna,
                    setOf(tipe),
                    item.kunciRahasia,
                )
                itemGagalVault = null
                dibuatBuat++
                if (_antreanBuat.value.isEmpty()) {
                    _pesan.value = if (hasil.dibuatBaru) {
                        PesanStatus(
                            NadaStatus.SUKSES,
                            "Vault baru dibuat; " +
                                AntreanPenamaan.ringkasanBuat(dibuatBuat, gagalBuat).teks,
                        )
                    } else {
                        PesanStatus(
                            NadaStatus.SUKSES,
                            "Vault “${hasil.nama}” sudah ada, dipakai lagi; " +
                                AntreanPenamaan.ringkasanBuat(dibuatBuat, gagalBuat).teks,
                        )
                    }
                    ringkasanBuatTertunda = false
                }
            } catch (e: GalatButuhVaultBaru) {
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal membuat koleksi: ${e.pesan}",
                    aksiUlang = true,
                )
            } catch (e: Exception) {
                gagalBuat++
                itemGagalVault = null
                if (_antreanBuat.value.isEmpty()) {
                    ringkasanBuatTertunda = true
                }
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal membuat koleksi: " +
                        PenjagaVault.terjemahkanGalatForum(e.message ?: "galat tak dikenal"),
                )
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
            _pesan.value = PesanStatus(NadaStatus.GALAT, "Nama koleksi belum diisi.")
            return
        }
        viewModelScope.launch {
            _memuat.value = true
            try {
                repo.ubahKoleksi(kunci, nama.trim(), deskripsi.trim(), label, warna, tipe)
                _pesan.value = PesanStatus(NadaStatus.SUKSES, "Koleksi diperbarui.")
            } catch (e: Exception) {
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal memperbarui koleksi: ${e.message}",
                )
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
                    PesanStatus(
                        NadaStatus.SUKSES,
                        "$n topik dibuat ulang di Telegram.",
                    )
                } else {
                    PesanStatus(
                        NadaStatus.INFO,
                        "Semua topik kategori sudah ada di Telegram.",
                    )
                }
            } catch (e: Exception) {
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal memeriksa topik: " +
                        PenjagaVault.terjemahkanGalatForum(e.message ?: "galat tak dikenal"),
                )
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
                _pesan.value = PesanStatus(NadaStatus.SUKSES, "Koleksi dihapus dari arsip.")
            } catch (e: Exception) {
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    "Gagal menghapus koleksi: ${e.message}",
                )
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
    
    /** Menandai bahwa kartu Senja Memories telah ditolak untuk hari ini. */
    fun setSenjaMemoriesDismissed() {
        viewModelScope.launch {
            pengaturan.setSenjaMemoriesDismissed()
            _senjaMemoriesDismissed.value = true
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
                    PesanStatus(NadaStatus.SUKSES, "Kunci koleksi dihapus.")
                } else {
                    PesanStatus(NadaStatus.SUKSES, "Kunci koleksi diperbarui.")
                }
            } catch (e: TelegramException) {
                _pesan.value = PesanStatus(NadaStatus.GALAT, e.pesan)
            } catch (e: Exception) {
                _pesan.value = PesanStatus(
                    NadaStatus.GALAT,
                    e.message ?: "Gagal mengubah kunci.",
                )
            } finally {
                _memuat.value = false
            }
        }
    }

    /**
     * Tutup popup status. Menutup popup galat yang menunda ringkasan
     * (antrean sudah habis saat gagal) menampilkan ringkasan itu; menutup
     * popup galat vault membatalkan retry yang belum dieksekusi.
     */
    fun habiskanPesan() {
        _pesan.value = null
        itemGagalVault = null
        when {
            ringkasanBuatTertunda -> {
                ringkasanBuatTertunda = false
                _pesan.value = AntreanPenamaan.ringkasanBuat(dibuatBuat, gagalBuat)
            }
            ringkasanTarikTertunda -> {
                ringkasanTarikTertunda = false
                _pesan.value = AntreanPenamaan.ringkasanTarik(
                    totalTarik,
                    gagalTarik,
                    " — lihat di tab Impor",
                )
            }
        }
    }

    companion object {
        val Pabrik = pabrikKontainer { wadah ->
            DasborViewModel(wadah.arsipRepository, wadah.pengaturan)
        }
    }
}
