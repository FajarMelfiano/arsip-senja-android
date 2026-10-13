@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.arsipsenja.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.ui.components.BarisKatalog
import com.arsipsenja.ui.components.BilahPesan
import com.arsipsenja.ui.components.ChipArsip
import com.arsipsenja.ui.components.KartuKatalog
import com.arsipsenja.ui.components.KeadaanKosong
import com.arsipsenja.ui.components.KolomCari
import com.arsipsenja.ui.components.NadaPesan
import com.arsipsenja.ui.components.StatistikArsip
import com.arsipsenja.ui.components.TombolIkon
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.DasborViewModel
import com.arsipsenja.util.TampilanDasbor
import com.arsipsenja.util.TipeFile
import com.arsipsenja.util.UrutBerkas
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Dasbor arsip: statistik, pencarian as-you-type, filter tipe & koleksi,
 * urutan, toggle kisi/daftar, sinkronisasi, dan kelola koleksi.
 */
@Composable
fun DasborScreen(onBukaBerkas: (String) -> Unit) {
    val vm: DasborViewModel = viewModel(factory = DasborViewModel.Pabrik)

    val berkas by vm.berkas.collectAsState()
    val statistik by vm.statistik.collectAsState()
    val koleksi by vm.koleksi.collectAsState()
    val tampilan by vm.tampilan.collectAsState()
    val kata by vm.kata.collectAsState()
    val saringan by vm.saringan.collectAsState()
    val memuat by vm.memuat.collectAsState()
    val pesan by vm.pesan.collectAsState()

    var kelolaKoleksi by remember { mutableStateOf(false) }
    var menuUrutTerbuka by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        BilahPesan(
            pesan = pesan,
            nada = if (pesan?.contains("gagal", true) == true) NadaPesan.SALAH else NadaPesan.BAIK,
            onTutup = vm::habiskanPesan,
        )
        if (pesan != null) Spacer(Modifier.height(8.dp))

        KepalaDasbor(
            jumlahBerkas = statistik.jumlah,
            tampilan = tampilan,
            memuat = memuat,
            onSinkron = vm::sinkron,
            onKelola = { kelolaKoleksi = true },
            onGantiTampilan = vm::gantiTampilan,
        )

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
                terpilih = saringan.koleksiId,
                onPilih = vm::saringKoleksi,
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

        if (berkas.isEmpty()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    if (kata.isBlank() && saringan.tipe == null && saringan.koleksiId == null) {
                        KeadaanKosong(
                            ikon = ArsipIcons.Inbox,
                            judul = "Arsip masih kosong",
                            pesan = "Tarik riwayat koleksi Telegram untuk mengisi katalog, " +
                                "atau unggah berkas pertamamu dari layar Unggah.",
                            aksiLabel = "Sinkronkan sekarang",
                            onAksi = vm::sinkron,
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
                daftar = berkas,
                namaKoleksi = { id -> koleksi.firstOrNull { it.chatId == id }?.nama },
                status = vm::statusLokal,
                onBuka = onBukaBerkas,
            )
        } else {
            DaftarBaris(
                daftar = berkas,
                namaKoleksi = { id -> koleksi.firstOrNull { it.chatId == id }?.nama },
                status = vm::statusLokal,
                onBuka = onBukaBerkas,
            )
        }
    }

    if (kelolaKoleksi) {
        LembarKoleksi(
            vm = vm,
            koleksi = koleksi,
            onTutup = { kelolaKoleksi = false },
        )
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
    onSinkron: () -> Unit,
    onKelola: () -> Unit,
    onGantiTampilan: () -> Unit,
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
        }
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
    terpilih: Long?,
    onPilih: (Long?) -> Unit,
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
            ChipArsip(
                label = k.nama,
                terpilih = terpilih == k.chatId,
                jumlah = k.jumlahBerkas,
                onClick = { onPilih(if (terpilih == k.chatId) null else k.chatId) },
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
    namaKoleksi: (Long?) -> String?,
    status: (BerkasArsipEntity) -> com.arsipsenja.util.StatusLokal,
    onBuka: (String) -> Unit,
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
                namaKoleksi = namaKoleksi(berkas.koleksiId),
                statusLokal = status(berkas),
                onClick = { onBuka(berkas.id) },
            )
        }
    }
}

@Composable
private fun DaftarBaris(
    daftar: List<BerkasArsipEntity>,
    namaKoleksi: (Long?) -> String?,
    status: (BerkasArsipEntity) -> com.arsipsenja.util.StatusLokal,
    onBuka: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(daftar, key = { it.id }) { berkas ->
            BarisKatalog(
                berkas = berkas,
                namaKoleksi = namaKoleksi(berkas.koleksiId),
                statusLokal = status(berkas),
                onClick = { onBuka(berkas.id) },
            )
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
) {
    var namaBaru by remember { mutableStateOf("") }
    var deskripsiBaru by remember { mutableStateOf("") }
    var diedit by remember { mutableStateOf<KoleksiEntity?>(null) }

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
                text = "Setiap koleksi adalah channel privat Telegram — rak arsip " +
                    "yang bisa kamu namai sendiri.",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta2,
            )

            Spacer(Modifier.height(16.dp))

            com.arsipsenja.ui.components.KolomIsian(
                label = "Nama koleksi baru",
                nilai = namaBaru,
                onUbah = { namaBaru = it },
                tempat = "Kenangan 2026",
            )
            Spacer(Modifier.height(10.dp))
            com.arsipsenja.ui.components.KolomIsian(
                label = "Deskripsi (opsional)",
                nilai = deskripsiBaru,
                onUbah = { deskripsiBaru = it },
                tempat = "Foto, surat, dan catatan perjalanan",
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onKirim = {
                    vm.buatKoleksi(namaBaru, deskripsiBaru)
                    namaBaru = ""
                    deskripsiBaru = ""
                },
            )
            Spacer(Modifier.height(12.dp))
            TombolUtama(
                teks = "Buat koleksi",
                ikon = ArsipIcons.Plus,
                aktif = namaBaru.isNotBlank(),
                onClick = {
                    vm.buatKoleksi(namaBaru, deskripsiBaru)
                    namaBaru = ""
                    deskripsiBaru = ""
                },
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Koleksi di akunmu (${koleksi.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = WarnaArsip.Tinta,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::tarikDaftarKoleksi) {
                    Text("Tarik dari Telegram", color = WarnaArsip.Amber)
                }
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
                        onHapus = { vm.hapusKoleksi(k.chatId, tinggalkanChannel = false) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    diedit?.let { k ->
        DialogUbahKoleksi(
            koleksi = k,
            onSimpan = { nama, deskripsi ->
                vm.ubahKoleksi(k.chatId, nama, deskripsi)
                diedit = null
            },
            onHapus = {
                vm.hapusKoleksi(k.chatId, tinggalkanChannel = false)
                diedit = null
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
            tint = WarnaArsip.VioletTerang,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = koleksi.nama,
                style = MaterialTheme.typography.titleSmall,
                color = WarnaArsip.Tinta,
                maxLines = 1,
            )
            Text(
                text = "${koleksi.jumlahBerkas} berkas" +
                    (koleksi.deskripsi?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
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
    onSimpan: (String, String) -> Unit,
    onHapus: () -> Unit,
    onTutup: () -> Unit,
) {
    var nama by remember { mutableStateOf(koleksi.nama) }
    var deskripsi by remember { mutableStateOf(koleksi.deskripsi.orEmpty()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onTutup,
        containerColor = WarnaArsip.Veil,
        titleContentColor = WarnaArsip.Tinta,
        textContentColor = WarnaArsip.Tinta2,
        title = { Text("Ubah koleksi") },
        text = {
            Column {
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSimpan(nama.trim(), deskripsi.trim()) },
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
