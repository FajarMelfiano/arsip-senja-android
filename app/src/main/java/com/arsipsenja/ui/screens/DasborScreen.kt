@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.arsipsenja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import com.arsipsenja.data.local.KunciBaru
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.KunciRahasia
import com.arsipsenja.data.local.TipeKunci
import com.arsipsenja.util.TipeFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.repo.StatusRealtime
import com.arsipsenja.ui.components.BagianKunciBaru
import com.arsipsenja.ui.components.BarisKatalog
import com.arsipsenja.ui.components.ChipArsip
import com.arsipsenja.ui.components.DialogRealtime
import com.arsipsenja.ui.components.KartuKatalog
import com.arsipsenja.ui.components.KeadaanKosong
import com.arsipsenja.ui.components.KolomCari
import com.arsipsenja.ui.components.LencanaTerkunci
import com.arsipsenja.ui.components.PopupNamaKoleksi
import com.arsipsenja.ui.components.PopupStatus
import com.arsipsenja.ui.components.SheetBukaKunci
import com.arsipsenja.ui.components.StatistikArsip
import com.arsipsenja.ui.components.TombolIkon
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.components.formatJam
import com.arsipsenja.ui.components.perangkatDukungBiometrik
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.DasborViewModel
import com.arsipsenja.util.TampilanDasbor
import com.arsipsenja.util.UrutBerkas
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Dasbor arsip: statistik, pencarian as-you-type, filter tipe & koleksi,
 * urutan, toggle kisi/daftar, sinkronisasi, dan kelola koleksi.
 */
@Composable
fun DasborScreen(onBukaBerkas: (String) -> Unit, onBukaImpor: () -> Unit = {}) {
    val vm: DasborViewModel = viewModel(factory = DasborViewModel.Pabrik)

    val berkas by vm.berkas.collectAsState()
    val statistik by vm.statistik.collectAsState()
    val koleksi by vm.koleksi.collectAsState()
    val tampilan by vm.tampilan.collectAsState()
    val kata by vm.kata.collectAsState()
    val saringan by vm.saringan.collectAsState()
    val memuat by vm.memuat.collectAsState()
    val pesan by vm.pesan.collectAsState()
    val progres by vm.progres.collectAsState()
    val statusRealtime by vm.statusRealtime.collectAsState()
    val antreanBuat by vm.antreanBuat.collectAsState()
    val antreanTarik by vm.antreanTarik.collectAsState()
    val semuaNamaKoleksi by vm.semuaNamaKoleksi.collectAsState()
    val dibuka by vm.koleksiTerbuka.collectAsState()

    var kelolaKoleksi by remember { mutableStateOf(false) }
    var menuUrutTerbuka by remember { mutableStateOf(false) }
    var pilihSinkron by remember { mutableStateOf(false) }
    var dialogRealtime by remember { mutableStateOf(false) }
    var bukaUntuk by remember { mutableStateOf<KoleksiEntity?>(null) }
    var tertundaBerkasId by remember { mutableStateOf<String?>(null) }

    // Berkas milik koleksi terkunci yang belum dibuka sesi ini disembunyikan
    // dari katalog maupun hasil cari (di semua tampilan kisi/daftar).
    val tampil = remember(berkas, koleksi, dibuka) {
        val peta = koleksi.associateBy { it.chatId to it.topicId }
        berkas.filter { b ->
            val induk = peta[b.chatId to b.topicId]
                ?: b.koleksiId?.let { peta[it to b.topicId] }
            !KunciRahasia.perluBukaKunci(induk, dibuka)
        }
    }

    /** Ketuk berkas: koleksi terkunci meminta buka kunci dulu, baru detail. */
    fun ketukBerkas(b: BerkasArsipEntity) {
        val induk = vm.entitasUntuk(b.chatId, b.topicId)
            ?: b.koleksiId?.let { vm.entitasUntuk(it, b.topicId) }
        if (induk != null && vm.perluBukaKunci(induk)) {
            tertundaBerkasId = b.id
            bukaUntuk = induk
        } else {
            onBukaBerkas(b.id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        KepalaDasbor(
            jumlahBerkas = statistik.jumlah,
            tampilan = tampilan,
            memuat = memuat,
            statusRealtime = statusRealtime,
            onSinkron = { pilihSinkron = true },
            onKelola = { kelolaKoleksi = true },
            onGantiTampilan = vm::gantiTampilan,
            onBukaRealtime = { dialogRealtime = true },
        )

        if (dialogRealtime) {
            DialogRealtime(
                status = statusRealtime,
                onTutup = { dialogRealtime = false },
            )
        }

        // Progres sinkron terpilih: caption teks inline, bukan popup —
        // popup per koleksi akan menumpuk dan memaksa ketuk berkali-kali.
        progres?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
            )
        }

        Spacer(Modifier.height(12.dp))

        StatistikArsip(statistik = statistik)

        Spacer(Modifier.height(12.dp))

        KolomCari(
            nilai = kata,
            onUbah = vm::ubahKata,
            onKosongkan = { vm.ubahKata("") },
        )

        Spacer(Modifier.height(10.dp))

        BarisFilterTipe(tipe = saringan.tipe, onPilih = vm::saringTipe)

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            BarisFilterKoleksi(
                koleksi = koleksi,
                terpilih = saringan.kunciKoleksi,
                terkunci = vm::perluBukaKunci,
                onPilih = vm::saringKoleksi,
                onBuka = { bukaUntuk = it },
                modifier = Modifier.weight(1f),
            )
            MenuUrut(
                terbuka = menuUrutTerbuka,
                onUbah = { menuUrutTerbuka = it },
                urut = saringan.urut,
                onPilihUrut = { u -> vm.urutkan(u, saringan.arah) },
                onBalikArah = {
                    vm.urutkan(
                        saringan.urut,
                        if (saringan.arah == com.arsipsenja.util.ArahUrut.TURUN)
                            com.arsipsenja.util.ArahUrut.NAIK
                        else com.arsipsenja.util.ArahUrut.TURUN,
                    )
                },
            )
        }

        Spacer(Modifier.height(4.dp))

        if (tampil.isEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    if (kata.isBlank() && saringan.tipe == null && saringan.kunciKoleksi == null) {
                        KeadaanKosong(
                            ikon = ArsipIcons.Inbox,
                            judul = "Arsip masih kosong",
                            pesan = "Tarik riwayat koleksi Telegram untuk mengisi katalog, " +
                                "atau unggah berkas pertamamu dari layar Unggah.",
                            aksiLabel = "Sinkronkan sekarang",
                            onAksi = { pilihSinkron = true },
                        )
                    } else {
                        KeadaanKosong(
                            ikon = ArsipIcons.Search,
                            judul = "Tidak ada yang cocok",
                            pesan = "Ubah kata kunci atau longgarkan filter tipe/koleksi.",
                            aksiLabel = "Kosongkan filter",
                            onAksi = {
                                vm.ubahKata("")
                                vm.saringTipe(null)
                                vm.saringKoleksi(null)
                            },
                        )
                    }
                }
            }
        } else if (tampilan == TampilanDasbor.KISI) {
            DaftarKisi(
                daftar = tampil,
                namaKoleksi = { kunci -> koleksi.firstOrNull { it.kunci == kunci }?.nama },
                status = vm::statusLokal,
                onMintaThumbnail = vm::mintaThumbnail,
                onBuka = ::ketukBerkas,
            )
        } else {
            DaftarBaris(
                daftar = tampil,
                namaKoleksi = { kunci -> koleksi.firstOrNull { it.kunci == kunci }?.nama },
                status = vm::statusLokal,
                onMintaThumbnail = vm::mintaThumbnail,
                onBuka = ::ketukBerkas,
            )
        }
    }

    if (pilihSinkron) {
        DialogPilihKoleksi(
            koleksi = koleksi,
            memuat = memuat,
            onBatal = { pilihSinkron = false },
            onKonfirmasi = { terpilih ->
                pilihSinkron = false
                vm.sinkronTerpilih(terpilih)
            },
        )
    }

    if (kelolaKoleksi) {
        LembarKoleksi(
            vm = vm,
            koleksi = koleksi,
            onTutup = { kelolaKoleksi = false },
            onBukaImpor = {
                kelolaKoleksi = false
                onBukaImpor()
            },
        )
    }

    bukaUntuk?.let { target ->
        val segar = koleksi.firstOrNull { it.kunci == target.kunci } ?: target
        SheetBukaKunci(
            koleksi = segar,
            onTutup = {
                bukaUntuk = null
                tertundaBerkasId = null
            },
            onBerhasil = {
                bukaUntuk = null
                tertundaBerkasId?.let { onBukaBerkas(it) }
                tertundaBerkasId = null
            },
            verifikasi = { rahasia, panggilBalik ->
                vm.bukaKunci(target.kunci, rahasia, panggilBalik)
            },
            onBiometrikLolos = { vm.bukaKunciBiometrik(target.kunci) },
        )
    }

    // Status (sukses/gagal/info) hanya lewat popup modal — satu dalam
    // satu waktu, pesan baru mengganti isi popup yang sedang tampil.
    PopupStatus(
        pesan = pesan,
        onTutup = vm::habiskanPesan,
        onAksiUlang = if (pesan?.aksiUlang == true) {
            vm::buatVaultBaruDanCobaLagi
        } else {
            null
        },
    )

    // Penamaan koleksi: satu popup dalam satu waktu, antrean Buat
    // didahulukan atas Tarik. Tampil hanya bila tidak ada popup status —
    // popup galat (mis. gagal buat) tidak boleh ditumpuk popup penamaan.
    if (pesan == null) {
        val itemBuat = antreanBuat.firstOrNull()
        val itemTarik = antreanTarik.firstOrNull()
        when {
            itemBuat != null -> PopupNamaKoleksi(
                item = itemBuat,
                namaTerpakai = semuaNamaKoleksi,
                adaLagi = antreanBuat.size > 1,
                onSimpan = vm::simpanNamaBuat,
                onLewati = vm::lewatiNamaBuat,
                onBatalkanSemua = vm::batalkanAntreanBuat,
            )
            itemTarik != null -> PopupNamaKoleksi(
                item = itemTarik,
                namaTerpakai = semuaNamaKoleksi,
                adaLagi = antreanTarik.size > 1,
                onSimpan = vm::simpanNamaTarik,
                onLewati = vm::lewatiNamaTarik,
                onBatalkanSemua = vm::batalkanAntreanTarik,
            )
        }
    }
}

// ---------------------------------------------------------------------
// Kepala & filter
// ---------------------------------------------------------------------

@Composable
private fun KepalaDasbor(
    jumlahBerkas: Int,
    tampilan: TampilanDasbor,
    memuat: Boolean,
    statusRealtime: StatusRealtime,
    onSinkron: () -> Unit,
    onKelola: () -> Unit,
    onGantiTampilan: () -> Unit,
    onBukaRealtime: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Katalog arsip",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Fraunces),
                color = WarnaArsip.Tinta,
            )
            Text(
                text = "$jumlahBerkas berkas terindeks",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
            )
            Text(
                text = teksStatusSinkron(statusRealtime),
                style = MaterialTheme.typography.bodySmall,
                color = warnaStatusSinkron(statusRealtime),
            )
        }
        TombolIkon(
            ikon = ArsipIcons.LoaderCircle,
            aksi = "Progres sinkron otomatis",
            tint = when {
                statusRealtime.menyinkronkan -> WarnaArsip.Amber
                statusRealtime.tersambung -> WarnaArsip.Sukses
                else -> WarnaArsip.Tinta3
            },
            onClick = onBukaRealtime,
        )
        TombolIkon(
            ikon = ArsipIcons.RefreshCw,
            aksi = "Sinkronkan arsip",
            tint = if (memuat) WarnaArsip.Amber else WarnaArsip.Tinta2,
            onClick = onSinkron,
        )
        TombolIkon(
            ikon = ArsipIcons.Folder,
            aksi = "Kelola koleksi",
            onClick = onKelola,
        )
        TombolIkon(
            ikon = if (tampilan == TampilanDasbor.KISI) ArsipIcons.List else ArsipIcons.LayoutGrid,
            aksi = if (tampilan == TampilanDasbor.KISI) "Tampil daftar" else "Tampil kisi",
            onClick = onGantiTampilan,
        )
    }
}

/**
 * Satu baris status jujur di bawah "N berkas terindeks":
 * - sinkron berjalan → "Menyinkronkan…",
 * - sudah pernah sinkron → "Terakhir diperbarui HH:mm",
 * - belum pernah & belum tersambung → "Menghubungkan…".
 */
private fun teksStatusSinkron(status: StatusRealtime): String = when {
    status.menyinkronkan -> "Menyinkronkan…"
    status.sinkronTerakhir != null -> "Terakhir diperbarui ${formatJam(status.sinkronTerakhir)}"
    !status.tersambung -> "Menghubungkan…"
    else -> "Sinkron otomatis aktif"
}

private fun warnaStatusSinkron(status: StatusRealtime) =
    if (status.menyinkronkan) WarnaArsip.Amber else WarnaArsip.Tinta3

@Composable
private fun BarisFilterTipe(tipe: TipeFile?, onPilih: (TipeFile?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipArsip(label = "Semua", terpilih = tipe == null, onClick = { onPilih(null) })
        TipeFile.entries.forEach { t ->
            ChipArsip(
                label = t.label,
                terpilih = tipe == t,
                ikon = com.arsipsenja.ui.components.ikonTipe(t),
                onClick = { onPilih(if (tipe == t) null else t) },
            )
        }
    }
}

@Composable
private fun BarisFilterKoleksi(
    koleksi: List<KoleksiEntity>,
    terpilih: KunciKoleksi?,
    terkunci: (KoleksiEntity) -> Boolean,
    onPilih: (KunciKoleksi?) -> Unit,
    onBuka: (KoleksiEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChipArsip(
            label = "Semua koleksi",
            terpilih = terpilih == null,
            onClick = { onPilih(null) },
        )
        koleksi.forEach { k ->
            if (terkunci(k)) {
                ChipTerkunci(koleksi = k, onBuka = { onBuka(k) })
            } else {
                ChipArsip(
                    label = k.nama,
                    terpilih = terpilih == k.kunci,
                    jumlah = k.jumlahBerkas,
                    onClick = { onPilih(if (terpilih == k.kunci) null else k.kunci) },
                )
            }
        }
    }
}

/** Chip koleksi terkunci: gembok + nama + "Terkunci" + tombol Buka. */
@Composable
private fun ChipTerkunci(koleksi: KoleksiEntity, onBuka: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .border(
                androidx.compose.foundation.BorderStroke(1.dp, WarnaArsip.GarisRambut),
                androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
            )
            .background(WarnaArsip.Permukaan, androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector = ArsipIcons.Lock,
            contentDescription = "Terkunci",
            tint = WarnaArsip.Tinta3,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = koleksi.nama,
            style = MaterialTheme.typography.labelMedium,
            color = WarnaArsip.Tinta2,
            maxLines = 1,
            modifier = Modifier.width(72.dp),
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        LencanaTerkunci()
        TextButton(
            onClick = onBuka,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                text = "Buka",
                style = MaterialTheme.typography.labelMedium,
                color = WarnaArsip.Amber,
            )
        }
    }
}

@Composable
private fun MenuUrut(
    terbuka: Boolean,
    onUbah: (Boolean) -> Unit,
    urut: UrutBerkas,
    onPilihUrut: (UrutBerkas) -> Unit,
    onBalikArah: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onUbah(true) }) {
            Icon(
                imageVector = ArsipIcons.ArrowUpDown,
                contentDescription = "Urutkan",
                tint = WarnaArsip.Amber,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = urut.label,
                style = MaterialTheme.typography.labelMedium,
                color = WarnaArsip.Amber,
            )
        }
        DropdownMenu(expanded = terbuka, onDismissRequest = { onUbah(false) }) {
            UrutBerkas.entries.forEach { u ->
                DropdownMenuItem(
                    text = { Text(u.label) },
                    onClick = {
                        onPilihUrut(u)
                        onUbah(false)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Balik arah urutan") },
                leadingIcon = {
                    Icon(ArsipIcons.ArrowUpDown, contentDescription = null)
                },
                onClick = {
                    onBalikArah()
                    onUbah(false)
                },
            )
        }
    }
}

// ---------------------------------------------------------------------
// Daftar
// ---------------------------------------------------------------------

@Composable
private fun DaftarKisi(
    daftar: List<BerkasArsipEntity>,
    namaKoleksi: (KunciKoleksi) -> String?,
    status: (BerkasArsipEntity) -> com.arsipsenja.util.StatusLokal,
    onMintaThumbnail: (BerkasArsipEntity) -> Unit,
    onBuka: (BerkasArsipEntity) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = rememberLazyGridState(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(daftar, key = { it.id }) { berkas ->
            KartuKatalog(
                berkas = berkas,
                namaKoleksi = namaKoleksi(KunciKoleksi(berkas.chatId, berkas.topicId)),
                statusLokal = status(berkas),
                onMintaThumbnail = onMintaThumbnail,
                onClick = { onBuka(berkas) },
            )
        }
    }
}

@Composable
private fun DaftarBaris(
    daftar: List<BerkasArsipEntity>,
    namaKoleksi: (KunciKoleksi) -> String?,
    status: (BerkasArsipEntity) -> com.arsipsenja.util.StatusLokal,
    onMintaThumbnail: (BerkasArsipEntity) -> Unit,
    onBuka: (BerkasArsipEntity) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(daftar, key = { it.id }) { berkas ->
            BarisKatalog(
                berkas = berkas,
                namaKoleksi = namaKoleksi(KunciKoleksi(berkas.chatId, berkas.topicId)),
                statusLokal = status(berkas),
                onMintaThumbnail = onMintaThumbnail,
                onClick = { onBuka(berkas) },
            )
        }
    }
}

// ---------------------------------------------------------------------
// Dialog pilih koleksi untuk sinkronisasi
// ---------------------------------------------------------------------

/**
 * Dialog multi-pilih koleksi: pengguna mencentang koleksi yang mau
 * disinkron, lalu sinkronisasi berjalan otomatis untuk yang terpilih.
 */
@Composable
private fun DialogPilihKoleksi(
    koleksi: List<KoleksiEntity>,
    memuat: Boolean,
    onBatal: () -> Unit,
    onKonfirmasi: (List<KunciKoleksi>) -> Unit,
) {
    var terpilih by remember(koleksi) {
        mutableStateOf(koleksi.map { it.kunci }.toSet())
    }
    val semuaTerpilih = koleksi.isNotEmpty() && terpilih.size == koleksi.size

    Dialog(onDismissRequest = onBatal) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = WarnaArsip.Veil,
            contentColor = WarnaArsip.Tinta,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "Pilih koleksi",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarnaArsip.Tinta,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Centang koleksi yang mau disinkron — " +
                        "sinkronisasi berjalan otomatis setelahnya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = semuaTerpilih,
                        onCheckedChange = { ya ->
                            terpilih = if (ya) koleksi.map { it.kunci }.toSet() else emptySet()
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = WarnaArsip.Amber,
                            checkmarkColor = WarnaArsip.Kanvas,
                        ),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Pilih semua (${koleksi.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = WarnaArsip.Tinta,
                    )
                }

                if (koleksi.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    KeadaanKosong(
                        ikon = ArsipIcons.Folder,
                        judul = "Belum ada koleksi",
                        pesan = "Buat koleksi dulu atau tarik daftar dari Telegram.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                    ) {
                        items(koleksi, key = { it.kunci.toString() }) { k ->
                            val dicentang = k.kunci in terpilih
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Checkbox(
                                    checked = dicentang,
                                    onCheckedChange = { ya ->
                                        terpilih = if (ya) terpilih + k.kunci
                                        else terpilih - k.kunci
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = WarnaArsip.Amber,
                                        checkmarkColor = WarnaArsip.Kanvas,
                                    ),
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = ArsipIcons.Folder,
                                    contentDescription = null,
                                    tint = WarnaArsip.VioletTerang,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = k.nama,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = WarnaArsip.Tinta,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${k.jumlahBerkas} berkas" +
                                            (if (k.adalahTopik) " · topik" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarnaArsip.Tinta3,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBatal) {
                        Text("Batal", color = WarnaArsip.Tinta2)
                    }
                    Spacer(Modifier.width(8.dp))
                    TombolUtama(
                        teks = if (terpilih.isEmpty()) "Sinkron"
                        else "Sinkron (${terpilih.size})",
                        ikon = ArsipIcons.RefreshCw,
                        aktif = terpilih.isNotEmpty() && !memuat,
                        penuh = false,
                        onClick = { onKonfirmasi(terpilih.toList()) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Lembar kelola koleksi
// ---------------------------------------------------------------------

@Composable
private fun LembarKoleksi(
    vm: DasborViewModel,
    koleksi: List<KoleksiEntity>,
    onTutup: () -> Unit,
    onBukaImpor: () -> Unit,
) {
    var deskripsiBaru by remember { mutableStateOf("") }
    var labelBaru by remember { mutableStateOf("") }
    var warnaBaru by remember { mutableStateOf(0) }
    var tipeBaru by remember { mutableStateOf(TipeFile.entries.toSet()) }
    var diedit by remember { mutableStateOf<KoleksiEntity?>(null) }

    // Bagian kunci koleksi (default mati). State milik sheet sehingga data
    // TIDAK hilang bila pembuatan gagal — user tinggal tekan coba lagi.
    var kunciAktif by remember { mutableStateOf(false) }
    var kunciTipe by remember { mutableStateOf(TipeKunci.TIDAK_ADA) }
    var kunciRahasia by remember { mutableStateOf("") }
    var kunciKonfirmasi by remember { mutableStateOf("") }
    var kunciPola by remember { mutableStateOf(emptyList<Int>()) }
    var kunciPolaUlang by remember { mutableStateOf(emptyList<Int>()) }
    var kunciBio by remember { mutableStateOf(false) }
    var galatKunci by remember { mutableStateOf<String?>(null) }

    val konteks = LocalContext.current
    val dukungBio = remember { perangkatDukungBiometrik(konteks) }

    fun aturUlangForm() {
        deskripsiBaru = ""
        labelBaru = ""
        warnaBaru = 0
        tipeBaru = TipeFile.entries.toSet()
        kunciAktif = false
        kunciTipe = TipeKunci.TIDAK_ADA
        kunciRahasia = ""
        kunciKonfirmasi = ""
        kunciPola = emptyList()
        kunciPolaUlang = emptyList()
        kunciBio = false
        galatKunci = null
    }

    /**
     * Validasi kunci dulu, lalu mulai antrean penamaan: satu popup nama
     * per tipe yang dicentang. Form dikosongkan dan sheet ditutup
     * langsung — pengaturan bersama sudah disalin ke antrean, dan
     * pembuatan tiap koleksi terjadi saat namanya disimpan di popup.
     */
    fun buatAntrean() {
        if (tipeBaru.isEmpty()) return
        galatKunci = KunciRahasia.validasiFormulir(
            kunciAktif, kunciTipe, kunciRahasia, kunciKonfirmasi, kunciPola, kunciPolaUlang,
        )
        if (galatKunci != null) return
        val kunci = KunciRahasia.rangkai(kunciAktif, kunciTipe, kunciRahasia, kunciPola, kunciBio)
        vm.mulaiBuatAntrean(deskripsiBaru, labelBaru, warnaBaru, tipeBaru, kunci)
        aturUlangForm()
        onTutup()
    }

    ModalBottomSheet(
        onDismissRequest = onTutup,
        containerColor = WarnaArsip.Veil,
        contentColor = WarnaArsip.Tinta,
        dragHandle = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(WarnaArsip.GarisKuat, RoundedCornerShape(999.dp)),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Kelola koleksi",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarnaArsip.Tinta,
                    modifier = Modifier.weight(1f),
                )
                TombolIkon(
                    ikon = ArsipIcons.X,
                    aksi = "Tutup",
                    onClick = onTutup,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Koleksi = satu rak (topik) di dalam vault. Vault = grup " +
                    "Telegram tempat semua koleksi disimpan; aplikasi menyiapkannya " +
                    "otomatis, kamu tidak perlu mengaturnya.",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta2,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Nama tiap koleksi ditanya satu per satu lewat popup " +
                    "setelah kamu menekan \"Buat koleksi\".",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta2,
            )
            Spacer(Modifier.height(10.dp))
            com.arsipsenja.ui.components.KolomIsian(
                label = "Deskripsi (opsional)",
                nilai = deskripsiBaru,
                onUbah = { deskripsiBaru = it },
                tempat = "Foto, surat, dan catatan perjalanan",
            )
            Spacer(Modifier.height(10.dp))
            com.arsipsenja.ui.components.KolomIsian(
                label = "Label (opsional, maks 24)",
                nilai = labelBaru,
                onUbah = { labelBaru = it.take(24) },
                tempat = "PENTING",
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onKirim = { buatAntrean() },
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Warna kategori",
                style = MaterialTheme.typography.labelMedium,
                color = WarnaArsip.Tinta2,
            )
            Spacer(Modifier.height(8.dp))
            PemilihWarnaKategori(warnaTerpilih = warnaBaru, onPilih = { warnaBaru = it })

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Tipe berkas untuk rute otomatis",
                style = MaterialTheme.typography.labelMedium,
                color = WarnaArsip.Tinta2,
            )
            Spacer(Modifier.height(8.dp))
            PemilihTipeKategori(tipeTerpilih = tipeBaru, onUbah = { tipeBaru = it })

            Spacer(Modifier.height(12.dp))
            BagianKunciBaru(
                diaktifkan = kunciAktif,
                onAktif = { kunciAktif = it },
                tipe = kunciTipe,
                onTipe = { kunciTipe = it },
                rahasia = kunciRahasia,
                onRahasia = { kunciRahasia = it },
                konfirmasi = kunciKonfirmasi,
                onKonfirmasi = { kunciKonfirmasi = it },
                pola = kunciPola,
                onPola = { kunciPola = it },
                polaUlang = kunciPolaUlang,
                onPolaUlang = { kunciPolaUlang = it },
                biometrik = kunciBio,
                onBiometrik = { kunciBio = it },
                tampilBiometrik = dukungBio,
                galat = galatKunci,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = if (tipeBaru.isEmpty()) {
                    "Centang minimal satu tipe berkas untuk membuat koleksi."
                } else {
                    "${tipeBaru.size} koleksi akan dibuat, satu per tipe — " +
                        "namanya ditanya satu per satu."
                },
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta2,
            )
            Spacer(Modifier.height(8.dp))
            TombolUtama(
                teks = "Buat koleksi",
                ikon = ArsipIcons.Plus,
                aktif = tipeBaru.isNotEmpty(),
                onClick = { buatAntrean() },
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Koleksi di akunmu (${koleksi.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = WarnaArsip.Tinta,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::perbaikiTopikHilang) {
                    Text("Buatkan topik", color = WarnaArsip.Amber)
                }
                TextButton(onClick = vm::tarikDaftarKoleksi) {
                    Text("Tarik dari Telegram", color = WarnaArsip.Amber)
                }
            }
            TextButton(onClick = onBukaImpor, modifier = Modifier.fillMaxWidth()) {
                Text("Lihat hasil impor di tab Impor", color = WarnaArsip.VioletTerang)
            }

            if (koleksi.isEmpty()) {
                KeadaanKosong(
                    ikon = ArsipIcons.Folder,
                    judul = "Belum ada koleksi",
                    pesan = "Buat koleksi pertama, atau tarik daftar channel yang sudah ada.",
                )
            } else {
                koleksi.forEach { k ->
                    BarisKoleksi(
                        koleksi = k,
                        onUbah = { diedit = k },
                        onHapus = { vm.hapusKoleksi(k.kunci, tinggalkanChannel = false) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    diedit?.let { k ->
        DialogUbahKoleksi(
            koleksi = k,
            onSimpan = { nama, deskripsi, label, warna, tipe ->
                vm.ubahKoleksi(k.kunci, nama, deskripsi, label, warna, tipe)
                diedit = null
            },
            onHapus = {
                vm.hapusKoleksi(k.kunci, tinggalkanChannel = false)
                diedit = null
            },
            onUbahKunci = { lama, baru ->
                vm.aturKunci(k.kunci, lama, baru)
            },
            onTutup = { diedit = null },
        )
    }
}

@Composable
private fun BarisKoleksi(
    koleksi: KoleksiEntity,
    onUbah: () -> Unit,
    onHapus: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = ArsipIcons.Folder,
            contentDescription = null,
            tint = if (koleksi.warna != 0) Color(koleksi.warna) else WarnaArsip.VioletTerang,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = koleksi.nama,
                    style = MaterialTheme.typography.titleSmall,
                    color = WarnaArsip.Tinta,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                koleksi.label?.takeIf { it.isNotBlank() }?.let { l ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = l,
                        style = MaterialTheme.typography.labelSmall,
                        color = WarnaArsip.AmberTerang,
                        modifier = Modifier
                            .background(WarnaArsip.AmberRedup, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                    )
                }
                if (koleksi.terkunci) {
                    Spacer(Modifier.width(6.dp))
                    LencanaTerkunci()
                }
            }
            Text(
                text = "${koleksi.jumlahBerkas} berkas" +
                    (koleksi.deskripsi?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                    " · ${ringkasanTipe(koleksi)}",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        TombolIkon(ikon = ArsipIcons.Pencil, aksi = "Ubah koleksi", onClick = onUbah)
        TombolIkon(
            ikon = ArsipIcons.Trash2,
            aksi = "Hapus koleksi",
            tint = WarnaArsip.Bahaya,
            onClick = onHapus,
        )
    }
}

@Composable
private fun DialogUbahKoleksi(
    koleksi: KoleksiEntity,
    onSimpan: (String, String, String, Int, Set<TipeFile>) -> Unit,
    onHapus: () -> Unit,
    onUbahKunci: (String?, KunciBaru?) -> Unit,
    onTutup: () -> Unit,
) {
    var nama by remember { mutableStateOf(koleksi.nama) }
    var deskripsi by remember { mutableStateOf(koleksi.deskripsi.orEmpty()) }
    var label by remember { mutableStateOf(koleksi.label.orEmpty()) }
    var warna by remember { mutableStateOf(koleksi.warna) }
    var tipe by remember { mutableStateOf(koleksi.tipeDitangani) }

    // Kelola kunci: ubah/hapus wajib memasukkan kunci lama dulu bila terkunci.
    val tipeLama = remember(koleksi.kunci) {
        runCatching { TipeKunci.valueOf(koleksi.kunciTipe) }
            .getOrDefault(TipeKunci.TIDAK_ADA)
    }
    var kunciLama by remember { mutableStateOf("") }
    var polaLama by remember { mutableStateOf(emptyList<Int>()) }
    var kunciAktif by remember { mutableStateOf(false) }
    var kunciTipe by remember { mutableStateOf(TipeKunci.TIDAK_ADA) }
    var kunciRahasia by remember { mutableStateOf("") }
    var kunciKonfirmasi by remember { mutableStateOf("") }
    var kunciPola by remember { mutableStateOf(emptyList<Int>()) }
    var kunciPolaUlang by remember { mutableStateOf(emptyList<Int>()) }
    var kunciBio by remember { mutableStateOf(false) }
    var galatKunci by remember { mutableStateOf<String?>(null) }
    val konteks = LocalContext.current
    val dukungBio = remember { perangkatDukungBiometrik(konteks) }

    /** Rahasia lama sesuai tipe kunci yang sedang dipasang. */
    fun rahasiaLama(): String =
        if (tipeLama == TipeKunci.POLA) KunciRahasia.polaKeRahasia(polaLama) else kunciLama

    fun kirimUbahKunci(hapus: Boolean) {
        if (koleksi.terkunci && rahasiaLama().isEmpty()) {
            galatKunci = "Masukkan kunci lama dulu."
            return
        }
        if (hapus) {
            galatKunci = null
            onUbahKunci(rahasiaLama().ifBlank { null }, null)
            return
        }
        galatKunci = KunciRahasia.validasiFormulir(
            kunciAktif, kunciTipe, kunciRahasia, kunciKonfirmasi, kunciPola, kunciPolaUlang,
        )
        if (galatKunci != null) return
        onUbahKunci(
            rahasiaLama().ifBlank { null },
            KunciRahasia.rangkai(kunciAktif, kunciTipe, kunciRahasia, kunciPola, kunciBio),
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onTutup,
        containerColor = WarnaArsip.Veil,
        titleContentColor = WarnaArsip.Tinta,
        textContentColor = WarnaArsip.Tinta2,
        title = { Text("Ubah koleksi") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
            ) {
                com.arsipsenja.ui.components.KolomIsian(
                    label = "Nama",
                    nilai = nama,
                    onUbah = { nama = it },
                )
                Spacer(Modifier.height(10.dp))
                com.arsipsenja.ui.components.KolomIsian(
                    label = "Deskripsi",
                    nilai = deskripsi,
                    onUbah = { deskripsi = it },
                )
                Spacer(Modifier.height(10.dp))
                com.arsipsenja.ui.components.KolomIsian(
                    label = "Label (opsional, maks 24)",
                    nilai = label,
                    onUbah = { label = it.take(24) },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Warna kategori",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarnaArsip.Tinta2,
                )
                Spacer(Modifier.height(6.dp))
                PemilihWarnaKategori(warnaTerpilih = warna, onPilih = { warna = it })
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Tipe berkas untuk rute otomatis",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarnaArsip.Tinta2,
                )
                Spacer(Modifier.height(6.dp))
                PemilihTipeKategori(tipeTerpilih = tipe, onUbah = { tipe = it })

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Kunci koleksi",
                        style = MaterialTheme.typography.labelMedium,
                        color = WarnaArsip.Tinta,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (koleksi.terkunci) {
                        LencanaTerkunci()
                    } else {
                        Text(
                            text = "Tidak dikunci",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarnaArsip.Tinta3,
                        )
                    }
                }
                if (koleksi.terkunci) {
                    Spacer(Modifier.height(8.dp))
                    if (tipeLama == TipeKunci.POLA) {
                        Text(
                            text = "Kunci lama (gambar polamu)",
                            style = MaterialTheme.typography.labelMedium,
                            color = WarnaArsip.Tinta2,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.arsipsenja.ui.components.PapanPola(
                                titik = polaLama,
                                onUbah = { polaLama = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { polaLama = emptyList() }) {
                                Text("Hapus", color = WarnaArsip.Tinta2)
                            }
                        }
                    } else {
                        com.arsipsenja.ui.components.KolomIsian(
                            label = "Kunci lama",
                            nilai = kunciLama,
                            onUbah = { kunciLama = it },
                            tempat = if (tipeLama == TipeKunci.PIN) "••••" else "Kunci lama",
                            sandi = true,
                            keyboardType = if (tipeLama == TipeKunci.PIN) {
                                androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            } else {
                                androidx.compose.ui.text.input.KeyboardType.Text
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                BagianKunciBaru(
                    diaktifkan = kunciAktif,
                    onAktif = { kunciAktif = it },
                    tipe = kunciTipe,
                    onTipe = { kunciTipe = it },
                    rahasia = kunciRahasia,
                    onRahasia = { kunciRahasia = it },
                    konfirmasi = kunciKonfirmasi,
                    onKonfirmasi = { kunciKonfirmasi = it },
                    pola = kunciPola,
                    onPola = { kunciPola = it },
                    polaUlang = kunciPolaUlang,
                    onPolaUlang = { kunciPolaUlang = it },
                    biometrik = kunciBio,
                    onBiometrik = { kunciBio = it },
                    tampilBiometrik = dukungBio,
                    galat = galatKunci,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { kirimUbahKunci(hapus = false) }) {
                        Text(
                            text = if (koleksi.terkunci) "Ubah kunci" else "Pasang kunci",
                            color = WarnaArsip.Amber,
                        )
                    }
                    if (koleksi.terkunci) {
                        TextButton(onClick = { kirimUbahKunci(hapus = true) }) {
                            Text("Hapus kunci", color = WarnaArsip.Bahaya)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSimpan(nama.trim(), deskripsi.trim(), label.trim(), warna, tipe) },
                enabled = nama.isNotBlank(),
            ) {
                Text("Simpan", color = WarnaArsip.Amber)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onHapus) {
                    Text("Hapus", color = WarnaArsip.Bahaya)
                }
                TextButton(onClick = onTutup) {
                    Text("Batal", color = WarnaArsip.Tinta2)
                }
            }
        },
    )
}

/** Delapan preset warna kategori; 0 = warna bawaan tema. */
private val WarnaKategori = listOf(
    0xFFE8A33D.toInt(),
    0xFFA48AE0.toInt(),
    0xFF6FBF9B.toInt(),
    0xFFE2695F.toInt(),
    0xFF7FB3E8.toInt(),
    0xFFE88FB1.toInt(),
    0xFF7FD4C1.toInt(),
    0xFFF2ECDF.toInt(),
)

@Composable
private fun PemilihWarnaKategori(warnaTerpilih: Int, onPilih: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwatchKategori(
            warna = null,
            terpilih = warnaTerpilih == 0,
            onPilih = { onPilih(0) },
        )
        WarnaKategori.forEach { w ->
            SwatchKategori(
                warna = w,
                terpilih = warnaTerpilih == w,
                onPilih = { onPilih(w) },
            )
        }
    }
}

@Composable
private fun SwatchKategori(warna: Int?, terpilih: Boolean, onPilih: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .then(
                if (warna == null) Modifier.background(WarnaArsip.GarisKuat)
                else Modifier.background(Color(warna)),
            )
            .then(
                if (terpilih) Modifier.border(2.dp, WarnaArsip.Amber, androidx.compose.foundation.shape.CircleShape)
                else Modifier,
            )
            .clickable(onClick = onPilih),
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PemilihTipeKategori(
    tipeTerpilih: Set<TipeFile>,
    onUbah: (Set<TipeFile>) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TipeFile.entries.forEach { t ->
            ChipArsip(
                label = t.label,
                terpilih = t in tipeTerpilih,
                onClick = {
                    onUbah(if (t in tipeTerpilih) tipeTerpilih - t else tipeTerpilih + t)
                },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (tipeTerpilih.isEmpty()) {
            "Tidak ada yang dipilih = menerima semua tipe berkas."
        } else {
            "${tipeTerpilih.size} dari ${TipeFile.entries.size} tipe dipilih."
        },
        style = MaterialTheme.typography.bodySmall,
        color = WarnaArsip.Tinta3,
    )
}

/** Ringkasan tipe untuk baris koleksi: "Semua tipe" atau "Gambar, Video". */
private fun ringkasanTipe(koleksi: KoleksiEntity): String {
    val semua = TipeFile.entries.toSet()
    return if (koleksi.tipeDitangani == semua) "Semua tipe"
    else koleksi.tipeDitangani.sortedBy { it.ordinal }.joinToString(", ") { it.label }
}
