package com.arsipsenja.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.arsipsenja.data.telegram.ProgresBerkas
import com.arsipsenja.ui.components.ArahTransfer
import com.arsipsenja.ui.components.BilahPesan
import com.arsipsenja.ui.components.ChipArsip
import com.arsipsenja.ui.components.KeadaanKosong
import com.arsipsenja.ui.components.NadaPesan
import com.arsipsenja.ui.components.ProgresTransfer
import com.arsipsenja.ui.components.TombolCadangan
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.UnggahViewModel
import com.arsipsenja.util.namaBerkas
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Layar unggah: pilih koleksi tujuan, pilih berkas lewat SAF,
 * pantau transfer langsung + antrean WorkManager.
 */
@Composable
fun UnggahScreen() {
    val vm: UnggahViewModel = viewModel(factory = UnggahViewModel.Pabrik)

    val koleksi by vm.koleksi.collectAsState()
    val terpilih by vm.koleksiTerpilih.collectAsState()
    val ruteOtomatis by vm.ruteOtomatis.collectAsState()
    val namaTerpilih by vm.namaKoleksiTerpilih.collectAsState()
    val antrean by vm.antrean.collectAsState()
    val transfer by vm.transfer.collectAsState()
    val pesan by vm.pesan.collectAsState()

    val pemilihBerkas = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { hasil ->
        if (hasil.isNotEmpty()) vm.mulaiUnggah(hasil)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        BilahPesan(
            pesan = pesan,
            nada = if (pesan?.contains("Gagal", true) == true) NadaPesan.SALAH
            else NadaPesan.BAIK,
            onTutup = vm::habiskanPesan,
        )
        if (pesan != null) Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Unggah",
                    style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Fraunces),
                    color = WarnaArsip.Tinta,
                )
                Text(
                    text = "Berkas dikirim langsung ke channel Telegram pilihanmu",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta3,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (koleksi.isEmpty()) {
            KeadaanKosong(
                ikon = ArsipIcons.Folder,
                judul = "Belum ada koleksi tujuan",
                pesan = "Buat koleksi dari Dasbor (ikon folder) sebelum mengunggah. " +
                    "Koleksi tersimpan sebagai channel privat atau topik di vault Telegram.",
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Rute otomatis (ikut kategori)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarnaArsip.Tinta,
                    )
                    Text(
                        text = "Tiap berkas dikirim ke kategori sesuai tipenya",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarnaArsip.Tinta3,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = ruteOtomatis,
                    onCheckedChange = vm::setRuteOtomatis,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = WarnaArsip.Amber,
                        checkedTrackColor = WarnaArsip.AmberRedup,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))

            if (!ruteOtomatis) {
                Text(
                    text = "Koleksi tujuan",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarnaArsip.Tinta2,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    koleksi.forEach { k ->
                        ChipArsip(
                            label = k.nama,
                            terpilih = terpilih == k.kunci,
                            jumlah = k.jumlahBerkas,
                            onClick = { vm.pilihKoleksi(k.kunci) },
                        )
                    }
                }
            } else {
                Text(
                    text = "Kategori tujuan ditentukan otomatis dari tipe tiap berkas. " +
                        "Atur tipe tiap kategori di Dasbor → Kelola koleksi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                )
            }

            Spacer(Modifier.height(16.dp))

            TombolUtama(
                teks = "Pilih berkas untuk diunggah",
                ikon = ArsipIcons.Upload,
                onClick = { pemilihBerkas.launch(arrayOf("*/*")) },
            )

            Spacer(Modifier.height(10.dp))

            TombolCadangan(
                teks = "Batalkan seluruh antrean",
                ikon = ArsipIcons.X,
                onClick = vm::batalkanSemua,
                warnaGaris = WarnaArsip.Bahaya.copy(alpha = 0.5f),
                warnaTinta = WarnaArsip.Bahaya,
            )

            if (namaTerpilih != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tujuan: $namaTerpilih",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Amber,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        if (transfer.isNotEmpty()) {
            Text(
                text = "Sedang berjalan",
                style = MaterialTheme.typography.titleSmall,
                color = WarnaArsip.Tinta,
            )
            Spacer(Modifier.height(10.dp))
            transfer.values.sortedByDescending { it.terkirim }.forEach { p ->
                ProgresTransfer(
                    nama = namaBerkas(p.path).ifBlank { "berkas" },
                    persen = p.persenUnggah,
                    arah = ArahTransfer.UNGGAH,
                    ukuranSekarang = p.terkirim,
                    ukuranTotal = p.ukuran,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
        }

        if (antrean.isNotEmpty()) {
            Text(
                text = "Antrean unggah (${antrean.size})",
                style = MaterialTheme.typography.titleSmall,
                color = WarnaArsip.Tinta,
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(antrean, key = { it.id }) { info ->
                    BarisAntrean(info, onBatalkan = { vm.batalkan(info.id) })
                }
            }
        } else if (transfer.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                KeadaanKosong(
                    ikon = ArsipIcons.Upload,
                    judul = "Tidak ada transfer",
                    pesan = "Antrean kosong. Berkas yang kamu pilih akan muncul di sini " +
                        "beserta progresnya.",
                )
            }
        }
    }
}

@Composable
private fun BarisAntrean(info: WorkInfo, onBatalkan: () -> Unit) {
    val (label, warna) = when (info.state) {
        WorkInfo.State.ENQUEUED -> "Antre" to WarnaArsip.Tinta2
        WorkInfo.State.RUNNING -> "Berjalan" to WarnaArsip.Amber
        WorkInfo.State.SUCCEEDED -> "Selesai" to WarnaArsip.Sukses
        WorkInfo.State.FAILED -> "Gagal" to WarnaArsip.Bahaya
        WorkInfo.State.CANCELLED -> "Dibatalkan" to WarnaArsip.Tinta3
        WorkInfo.State.BLOCKED -> "Tertahan" to WarnaArsip.VioletTerang
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = when (info.state) {
                WorkInfo.State.SUCCEEDED -> ArsipIcons.CircleCheck
                WorkInfo.State.FAILED -> ArsipIcons.CircleX
                WorkInfo.State.CANCELLED -> ArsipIcons.X
                else -> ArsipIcons.Clock
            },
            contentDescription = null,
            tint = warna,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = WarnaArsip.Tinta,
            )
            Text(
                text = "Pekerjaan ${info.id.toString().take(8)}",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
            )
        }
        if (info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING) {
            com.arsipsenja.ui.components.TombolIkon(
                ikon = ArsipIcons.X,
                aksi = "Batalkan unggah",
                tint = WarnaArsip.Bahaya,
                onClick = onBatalkan,
            )
        }
    }
}
