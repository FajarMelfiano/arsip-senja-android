package com.arsipsenja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciRahasia
import com.arsipsenja.ui.components.BilahPesan
import com.arsipsenja.ui.components.KartuKatalog
import com.arsipsenja.ui.components.KeadaanKosong
import com.arsipsenja.ui.components.LencanaTerkunci
import com.arsipsenja.ui.components.NadaPesan
import com.arsipsenja.ui.components.SheetBukaKunci
import com.arsipsenja.ui.components.TombolCadangan
import com.arsipsenja.ui.components.TombolIkon
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.ImporViewModel

/**
 * Tab Impor Telegram: hasil "Tarik dari Telegram" dari semua sumber
 * (vault aktif, grup forum-siap milik sendiri, channel lama) — terpisah
 * dari file-file pribadi di Dasbor.
 *
 * Alur: daftar koleksi impor → ketuk satu koleksi → daftar berkasnya
 * (detail, pratinjau, unduh, bagikan lewat layar Berkas yang sama dengan
 * berkas pribadi). Koleksi terkunci meminta buka kunci dulu.
 */
@Composable
fun ImporScreen(onBukaBerkas: (String) -> Unit) {
    val vm: ImporViewModel = viewModel(factory = ImporViewModel.Pabrik)

    val impor by vm.impor.collectAsState()
    val dipilih by vm.dipilih.collectAsState()
    val berkas by vm.berkas.collectAsState()
    val memuat by vm.memuat.collectAsState()
    val pesan by vm.pesan.collectAsState()
    val dibuka by vm.koleksiTerbuka.collectAsState()

    var bukaUntuk by remember { mutableStateOf<KoleksiEntity?>(null) }
    var tertundaBerkasId by remember { mutableStateOf<String?>(null) }

    val koleksiDipilih = dipilih?.let { kunci ->
        impor.firstOrNull { it.kunci == kunci }
    }

    // Berkas milik koleksi terkunci yang belum dibuka sesi ini disembunyikan.
    val tampil = remember(berkas, impor, dibuka) {
        val peta = impor.associateBy { it.chatId to it.topicId }
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
        BilahPesan(
            pesan = pesan,
            nada = if (pesan?.contains("gagal", true) == true) NadaPesan.SALAH else NadaPesan.BAIK,
            onTutup = vm::habiskanPesan,
        )
        if (pesan != null) Spacer(Modifier.height(8.dp))

        KepalaImpor(
            jumlahKoleksi = impor.size,
            memuat = memuat,
            onTarik = vm::tarik,
            onSinkron = vm::sinkron,
        )

        Spacer(Modifier.height(12.dp))

        if (koleksiDipilih == null) {
            if (impor.isEmpty()) {
                KeadaanKosong(
                    ikon = ArsipIcons.Inbox,
                    judul = "Belum ada hasil impor",
                    pesan = "Tarik dari Telegram untuk mengimpor topik dari semua " +
                        "grup dan channel di akunmu. File pribadimu tetap di Dasbor.",
                    aksiLabel = "Tarik dari Telegram",
                    onAksi = vm::tarik,
                )
            } else {
                Text(
                    text = "Ketuk satu koleksi untuk melihat berkasnya.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta3,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(impor, key = { it.kunci.toString() }) { k ->
                        BarisImpor(
                            koleksi = k,
                            terkunci = vm.perluBukaKunci(k),
                            onBuka = { vm.pilih(k.kunci) },
                            onMintaBuka = { bukaUntuk = k },
                        )
                    }
                }
            }
        } else {
            BarisKembali(
                nama = koleksiDipilih.nama,
                jumlah = tampil.size,
                onKembali = { vm.pilih(null) },
            )
            Spacer(Modifier.height(8.dp))
            if (vm.perluBukaKunci(koleksiDipilih)) {
                KeadaanKosong(
                    ikon = ArsipIcons.Lock,
                    judul = "Koleksi terkunci",
                    pesan = "Buka dulu kuncinya untuk melihat berkas impor ini.",
                    aksiLabel = "Buka kunci",
                    onAksi = { bukaUntuk = koleksiDipilih },
                )
            } else if (tampil.isEmpty()) {
                KeadaanKosong(
                    ikon = ArsipIcons.Inbox,
                    judul = "Belum ada berkas",
                    pesan = "Koleksi ini kosong atau sinkronisasi belum selesai. " +
                        "Tekan Sinkron untuk mengambil riwayat terbaru.",
                    aksiLabel = "Sinkronkan impor",
                    onAksi = vm::sinkron,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = rememberLazyGridState(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(tampil, key = { it.id }) { b ->
                        KartuKatalog(
                            berkas = b,
                            namaKoleksi = koleksiDipilih.nama,
                            statusLokal = vm.statusLokal(b),
                            onMintaThumbnail = vm::mintaThumbnail,
                            onClick = { ketukBerkas(b) },
                        )
                    }
                }
            }
        }
    }

    bukaUntuk?.let { target ->
        val segar = impor.firstOrNull { it.kunci == target.kunci } ?: target
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
}

@Composable
private fun KepalaImpor(
    jumlahKoleksi: Int,
    memuat: Boolean,
    onTarik: () -> Unit,
    onSinkron: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Impor Telegram",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Fraunces),
                    color = WarnaArsip.Tinta,
                )
                Text(
                    text = "$jumlahKoleksi koleksi dari akunmu",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta3,
                )
            }
            TombolIkon(
                ikon = ArsipIcons.RefreshCw,
                aksi = "Sinkronkan impor",
                tint = if (memuat) WarnaArsip.Amber else WarnaArsip.Tinta2,
                onClick = onSinkron,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Hasil tarikan terpisah dari file pribadi di Dasbor.",
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta3,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TombolUtama(
                teks = "Tarik dari Telegram",
                ikon = ArsipIcons.Download,
                aktif = !memuat,
                penuh = false,
                onClick = onTarik,
                modifier = Modifier.weight(1f),
            )
            TombolCadangan(
                teks = "Sinkron",
                ikon = ArsipIcons.RefreshCw,
                onClick = onSinkron,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BarisKembali(nama: String, jumlah: Int, onKembali: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TombolIkon(
            ikon = ArsipIcons.ArrowLeft,
            aksi = "Kembali ke daftar impor",
            onClick = onKembali,
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = nama,
                style = MaterialTheme.typography.titleMedium,
                color = WarnaArsip.Tinta,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = "$jumlah berkas",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
            )
        }
    }
}

/** Baris koleksi impor: ikon, nama, asal (topik/channel) + jumlah, aksi buka. */
@Composable
private fun BarisImpor(
    koleksi: KoleksiEntity,
    terkunci: Boolean,
    onBuka: () -> Unit,
    onMintaBuka: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = if (koleksi.adalahTopik) ArsipIcons.Hash else ArsipIcons.Folder,
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
                if (terkunci) {
                    Spacer(Modifier.width(6.dp))
                    LencanaTerkunci()
                }
            }
            Text(
                text = "${koleksi.jumlahBerkas} berkas · " +
                    if (koleksi.adalahTopik) "topik grup" else "channel",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
                maxLines = 1,
            )
        }
        if (terkunci) {
            TextButton(onClick = onMintaBuka) {
                Text("Buka", color = WarnaArsip.Amber)
            }
        }
        TombolIkon(
            ikon = ArsipIcons.ChevronRight,
            aksi = "Lihat berkas ${koleksi.nama}",
            onClick = onBuka,
        )
    }
}
