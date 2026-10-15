package com.arsipsenja.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.telegram.ProgresBerkas
import com.arsipsenja.data.work.UploadWorker
import com.arsipsenja.ui.components.ArahTransfer
import com.arsipsenja.ui.components.ChipArsip
import com.arsipsenja.ui.components.KeadaanKosong
import com.arsipsenja.ui.components.ProgresTransfer
import com.arsipsenja.ui.components.PopupStatus
import com.arsipsenja.ui.components.SheetBukaKunci
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
    val hanyaWifi by vm.hanyaWifi.collectAsState()
    val namaTerpilih by vm.namaKoleksiTerpilih.collectAsState()
    val antrean by vm.antrean.collectAsState()
    val prioritasUnggah by vm.prioritasUnggah.collectAsState()
    val transfer by vm.transfer.collectAsState()
    val pesan by vm.pesan.collectAsState()
    val galatUnggah by vm.galatUnggah.collectAsState()
    val dibuka by vm.koleksiTerbuka.collectAsState()

    var bukaUntuk by remember { mutableStateOf<KoleksiEntity?>(null) }

    // LocalContext hanya boleh dibaca langsung di dalam fungsi @Composable,
    // bukan di dalam lambda pemicu activity result.
    val konteks = LocalContext.current
    val pemilihBerkas = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { hasil ->
        if (hasil.isEmpty()) return@rememberLauncherForActivityResult
        // Simpan izin akses secara permanen.
        //
        // Tanpa ini, izin hanya berlaku selama activity file-picker hidup.
        // Antrean WorkManager bisa berjalan jauh setelah itu — atau setelah
        // proses mati — sehingga reading berkas gagal dengan SecurityException
        // dan tidak ada yang bisa memperbaikinya selain pengguna memilih ulang
        // berkasnya. Karena aplikasi ini memang dipakai untuk memindahkan berkas besar,
        // ini adalah penyebab kegagalan paling merusak.
        hasil.forEach { uri ->
            runCatching {
                konteks.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure {
                Log.w("UnggahScreen", "Izin persisten gagal untuk $uri: ${it.message}")
            }
        }
        vm.mulaiUnggah(hasil)
    }

    // Koleksi tujuan manual + status kuncinya (diingat mengikuti daftar).
    val tujuan: KoleksiEntity? = remember(koleksi, terpilih) {
        koleksi.firstOrNull { it.kunci == terpilih }
    }
    val tujuanTerkunci = remember(tujuan, dibuka) { vm.perluBukaKunci(tujuan) }

    /** Pilih berkas: koleksi terkunci meminta buka kunci dulu sebelum picker. */
    fun pilihBerkas() {
        if (!ruteOtomatis && tujuanTerkunci && tujuan != null) {
            bukaUntuk = tujuan
        } else {
            pemilihBerkas.launch(arrayOf("*/*"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Hanya unggah via Wi-Fi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarnaArsip.Tinta,
                    )
                    Text(
                        text = "Tahan unggahan saat memakai data seluler",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarnaArsip.Tinta3,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = hanyaWifi,
                    onCheckedChange = vm::setHanyaWifi,
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
                onClick = ::pilihBerkas,
            )

            if (!ruteOtomatis && tujuanTerkunci && tujuan != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ArsipIcons.Lock,
                        contentDescription = "Terkunci",
                        tint = WarnaArsip.Tinta3,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Tujuan terkunci — buka dulu sebelum mengunggah.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarnaArsip.Tinta2,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { bukaUntuk = tujuan }) {
                        Text(
                            text = "Buka",
                            style = MaterialTheme.typography.labelLarge,
                            color = WarnaArsip.Amber,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            TombolCadangan(
                teks = "Batalkan seluruh antrean",
                ikon = ArsipIcons.X,
                onClick = vm::batalkanSemua,
                warnaGaris = WarnaArsip.Bahaya.copy(alpha = 0.5f),
                warnaTinta = WarnaArsip.Bahaya,
            )

            Spacer(Modifier.height(10.dp))

            TombolCadangan(
                teks = "Bersihkan riwayat antrean",
                ikon = ArsipIcons.Trash2,
                onClick = vm::bersihkanRiwayat,
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
                    val workId = vm.workIdDariInfo(info)
                    BarisAntrean(
                        info = info,
                        vm = vm,
                        prioritas = workId != null && workId in prioritasUnggah,
                        onTandaiPrioritas = { p ->
                            if (workId != null) vm.tandaiPrioritas(workId, p)
                        },
                        onBatalkan = { vm.batalkan(info.id) },
                    )
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

    bukaUntuk?.let { target ->
        SheetBukaKunci(
            koleksi = target,
            onTutup = { bukaUntuk = null },
            onBerhasil = {
                bukaUntuk = null
                // Lanjutkan memilih berkas setelah kunci dibuka.
                pemilihBerkas.launch(arrayOf("*/*"))
            },
            verifikasi = { rahasia, panggilBalik ->
                vm.bukaKunci(target.kunci, rahasia, panggilBalik)
            },
            onBiometrikLolos = { vm.bukaKunciBiometrik(target.kunci) },
        )
    }

    // Satu popup status dalam satu waktu: pesan aksi (antrean, batal)
    // didahulukan; galat FAILED terminal satu pekerjaan menyusul setelah
    // popup aksi ditutup — habiskanGalatUnggah lalu memindai FAILED
    // berikutnya yang belum dilaporkan.
    PopupStatus(
        pesan = pesan ?: galatUnggah,
        onTutup = {
            if (pesan != null) vm.habiskanPesan() else vm.habiskanGalatUnggah()
        },
    )
}

@Composable
private fun BarisAntrean(
    info: WorkInfo,
    vm: UnggahViewModel,
    prioritas: Boolean,
    onTandaiPrioritas: (Boolean) -> Unit,
    onBatalkan: () -> Unit,
) {
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
            val namaBerkas = info.progress.getString(UploadWorker.K_NAMA)
                ?.takeIf { it.isNotBlank() }
                ?: "Pekerjaan ${info.id.toString().take(8)}"
            Text(
                text = namaBerkas,
                style = MaterialTheme.typography.titleSmall,
                color = WarnaArsip.Tinta,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                // Bila gagal, tampilkan alasan asli dari worker (bila ada)
                // agar pengguna tahu kenapa — bukan hanya ID pekerjaan.
                text = if (info.state == WorkInfo.State.FAILED) {
                    info.outputData.getString(UploadWorker.K_PESAN_GALAT)
                        ?.takeIf { it.isNotBlank() }
                        ?: label
                } else {
                    label
                },
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
            )
        }
        if (info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val workId = vm.workIdDariInfo(info)
                com.arsipsenja.ui.components.TombolIkon(
                    ikon = ArsipIcons.Bookmark,
                    aksi = if (prioritas) "Hapus tanda prioritas" else "Tandai prioritas",
                    tint = if (prioritas) WarnaArsip.Amber else WarnaArsip.Tinta3,
                    onClick = { onTandaiPrioritas(!prioritas) },
                )
                Spacer(Modifier.width(8.dp))
                if (workId != null) {
                    val dijeda by vm.statusJeda(workId).collectAsState(initial = false)
                    com.arsipsenja.ui.components.TombolIkon(
                        ikon = if (dijeda) ArsipIcons.Play else ArsipIcons.Pause,
                        aksi = if (dijeda) "Lanjutkan unggah" else "Jeda unggah",
                        tint = WarnaArsip.Tinta,
                        onClick = {
                            if (dijeda) vm.lanjutkanUnggah(workId)
                            else vm.jedaUnggah(workId)
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                com.arsipsenja.ui.components.TombolIkon(
                    ikon = ArsipIcons.X,
                    aksi = "Batalkan unggah",
                    tint = WarnaArsip.Bahaya,
                    onClick = onBatalkan,
                )
            }
        }
    }
}
