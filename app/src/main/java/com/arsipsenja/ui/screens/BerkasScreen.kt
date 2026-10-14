@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.arsipsenja.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.ui.components.KeadaanKosong
import com.arsipsenja.ui.components.KolomIsian
import com.arsipsenja.ui.components.NomorKatalog
import com.arsipsenja.ui.components.PopupStatus
import com.arsipsenja.ui.components.ProgresTransfer
import com.arsipsenja.ui.components.TombolCadangan
import com.arsipsenja.ui.components.TombolIkon
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.components.tipeDari
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.BerkasViewModel
import com.arsipsenja.util.TipeFile
import com.arsipsenja.util.formatTanggalWaktu
import com.arsipsenja.util.formatUkuran
import java.io.File

/**
 * Detail berkas: nomor katalog, pratinjau (gambar/video/audio),
 * metadata, aksi unduh/buka, dan tautan berbagi.
 */
@Composable
fun BerkasScreen(berkasId: String, onKembali: () -> Unit) {
    val vm: BerkasViewModel = viewModel(
        key = "berkas-$berkasId",
        factory = BerkasViewModel.pabrik(berkasId),
    )

    val berkas by vm.berkas.collectAsState()
    val koleksi by vm.koleksi.collectAsState()
    val tautan by vm.tautan.collectAsState()
    val progres by vm.progres.collectAsState()
    val unduhan by vm.unduhan.collectAsState()
    val pesan by vm.pesan.collectAsState()
    val sibuk by vm.sibuk.collectAsState()
    val tautanBaru by vm.tautanBaru.collectAsState()

    val context = LocalContext.current
    val papanKlip = LocalClipboardManager.current

    LaunchedEffect(tautanBaru) {
        val url = tautanBaru ?: return@LaunchedEffect
        papanKlip.setText(AnnotatedString(url))
        vm.habiskanTautanBaru()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        ) {
            TombolIkon(
                ikon = ArsipIcons.ArrowLeft,
                aksi = "Kembali",
                tint = WarnaArsip.Tinta,
                onClick = onKembali,
            )
            Text(
                text = "Detail berkas",
                style = MaterialTheme.typography.titleMedium,
                color = WarnaArsip.Tinta,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            berkas?.let {
                TombolIkon(
                    ikon = ArsipIcons.Trash2,
                    aksi = "Hapus dari indeks",
                    tint = WarnaArsip.Bahaya,
                    onClick = { vm.hapusBerkas() },
                )
            }
        }

        // Status aksi (unduh, tautan, hapus) lewat popup modal.
        PopupStatus(
            pesan = pesan,
            onTutup = vm::habiskanPesan,
        )

        val b = berkas
        if (b == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WarnaArsip.Amber, modifier = Modifier.size(28.dp))
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { KepalaBerkas(b, namaKoleksi = koleksi?.nama) }

            item {
                Pratinjau(
                    berkas = b,
                    persenUnduh = progres?.persenUnduh ?: -1,
                    sedangUnduh = progres != null && !progres!!.selesaiUnduh ||
                        unduhan?.state == WorkInfo.State.RUNNING,
                    sibuk = sibuk,
                    onUnduh = vm::mulaiUnduh,
                )
            }

            item {
                Section("Katalog") {
                    BarisMeta("Nomor katalog", com.arsipsenja.util.nomorKatalog(b.nomorKatalog))
                    BarisMeta("Nama berkas", b.nama)
                    BarisMeta("Ukuran", formatUkuran(b.ukuran))
                    BarisMeta("Diunggah", formatTanggalWaktu(b.tanggal))
                    BarisMeta("Tipe", tipeDari(b).label)
                    BarisMeta("MIME", b.mime.ifBlank { "—" })
                    BarisMeta("Koleksi", koleksi?.nama ?: "Tanpa koleksi")
                    b.deskripsi?.takeIf { it.isNotBlank() }?.let {
                        BarisMeta("Keterangan", it)
                    }
                }
            }

            item {
                Section("Jejak Telegram") {
                    BarisMeta("ID pesan", b.messageId.toString())
                    BarisMeta("ID chat", b.chatId.toString())
                    BarisMeta("ID file", b.tdFileId.toString())
                    BarisMeta(
                        statusLokalLabel(b, unduhan, progres != null && !progres!!.selesaiUnduh),
                        b.tdLocalPath?.substringAfterLast('/') ?: "Belum diunduh",
                    )
                }
            }

            item {
                AksiBerkas(
                    berkas = b,
                    sibuk = sibuk,
                    sedangUnduh = unduhan?.state == WorkInfo.State.RUNNING ||
                        unduhan?.state == WorkInfo.State.ENQUEUED,
                    onUnduh = vm::mulaiUnduh,
                    onBuatTautan = vm::buatTautanPesan,
                )
            }

            if (tautan.isNotEmpty()) {
                item {
                    Section("Tautan berbagi (${tautan.size})") {
                        tautan.forEach { t ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = ArsipIcons.Link,
                                    contentDescription = null,
                                    tint = if (t.dicabut) WarnaArsip.Tinta3 else WarnaArsip.VioletTerang,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = t.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (t.dicabut) WarnaArsip.Tinta3 else WarnaArsip.Tinta,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (!t.dicabut) {
                                    TombolIkon(
                                        ikon = ArsipIcons.Copy,
                                        aksi = "Salin tautan",
                                        onClick = {
                                            papanKlip.setText(AnnotatedString(t.url))
                                        },
                                    )
                                }
                                TombolIkon(
                                    ikon = ArsipIcons.Trash2,
                                    aksi = "Hapus catatan tautan",
                                    tint = WarnaArsip.Bahaya,
                                    onClick = { vm.hapusTautan(t.id) },
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun statusLokalLabel(b: BerkasArsipEntity, unduh: WorkInfo?, proses: Boolean): String =
    when {
        !b.tdLocalPath.isNullOrBlank() -> "Lokal"
        proses || unduh?.state == WorkInfo.State.RUNNING -> "Sedang diunduh"
        else -> "Status unduh"
    }

// ---------------------------------------------------------------------
// Bagian layar
// ---------------------------------------------------------------------

@Composable
private fun KepalaBerkas(b: BerkasArsipEntity, namaKoleksi: String?) {
    Column {
        NomorKatalog(nomor = b.nomorKatalog)
        Spacer(Modifier.height(4.dp))
        Text(
            text = b.nama,
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Fraunces),
            color = WarnaArsip.Tinta,
        )
        if (namaKoleksi != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Rak: $namaKoleksi",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.VioletTerang,
            )
        }
    }
}

@Composable
private fun Section(judul: String, isi: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column {
        Text(
            text = judul,
            style = MaterialTheme.typography.titleSmall,
            color = WarnaArsip.Amber,
        )
        Spacer(Modifier.height(8.dp))
        Column(content = isi)
    }
}

@Composable
private fun BarisMeta(label: String, nilai: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta3,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = nilai,
            style = MaterialTheme.typography.bodyMedium,
            color = WarnaArsip.Tinta,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------------
// Pratinjau
// ---------------------------------------------------------------------

@Composable
private fun Pratinjau(
    berkas: BerkasArsipEntity,
    persenUnduh: Int,
    sedangUnduh: Boolean,
    sibuk: Boolean,
    onUnduh: () -> Unit,
) {
    val tipe = tipeDari(berkas)
    val path = berkas.tdLocalPath

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        when {
            path.isNullOrBlank() -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        imageVector = com.arsipsenja.ui.components.ikonTipe(tipe),
                        contentDescription = null,
                        tint = WarnaArsip.Tinta3,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (sedangUnduh) "Mengunduh pratinjau…"
                        else "Pratinjau tersedia setelah diunduh ke perangkat",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarnaArsip.Tinta2,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    TombolUtama(
                        teks = if (sedangUnduh) "Mengunduh…" else "Unduh berkas",
                        ikon = ArsipIcons.Download,
                        onClick = onUnduh,
                        penuh = false,
                        aktif = !sibuk && !sedangUnduh,
                        memuat = sedangUnduh,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (sedangUnduh && persenUnduh >= 0) {
                    Spacer(Modifier.height(6.dp))
                    ProgresTransfer(
                        nama = berkas.nama,
                        persen = persenUnduh,
                        arah = com.arsipsenja.ui.components.ArahTransfer.UNDUH,
                        ukuranSekarang = progresUkuran(persenUnduh, berkas.ukuran),
                        ukuranTotal = berkas.ukuran,
                    )
                }
            }

            tipe == TipeFile.GAMBAR -> PreviewGambar(path)
            tipe == TipeFile.VIDEO || tipe == TipeFile.AUDIO -> PreviewMedia(path, video = tipe == TipeFile.VIDEO)
            else -> PreviewUmum(berkas, tipe)
        }
    }
}

private fun progresUkuran(persen: Int, total: Long): Long =
    if (persen in 0..100 && total > 0) (total * persen) / 100 else -1L

@Composable
private fun PreviewGambar(path: String) {
    val file = remember(path) { File(path) }
    if (!file.exists()) {
        Text(
            text = "Berkas gambar tidak ditemukan di perangkat.",
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta3,
        )
        return
    }
    AsyncImage(
        model = file,
        contentDescription = "Pratinjau gambar",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun PreviewMedia(path: String, video: Boolean) {
    val context = LocalContext.current
    val pemutar = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            prepare()
            playWhenReady = video
        }
    }
    DisposableEffect(path) {
        onDispose { pemutar.release() }
    }

    AndroidViewPemutar(
        pemutar = pemutar,
        video = video,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (video) 200.dp else 72.dp),
    )
}

@Composable
private fun AndroidViewPemutar(
    pemutar: ExoPlayer,
    video: Boolean,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = pemutar
                useController = video
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun PreviewUmum(berkas: BerkasArsipEntity, tipe: TipeFile) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = com.arsipsenja.ui.components.ikonTipe(tipe),
            contentDescription = null,
            tint = WarnaArsip.Tinta3,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Berkas sudah tersedia di perangkat",
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        TombolUtama(
            teks = "Buka dengan aplikasi lain",
            ikon = ArsipIcons.ExternalLink,
            penuh = false,
            onClick = { bukaBerkas(context, berkas) },
        )
    }
}

// ---------------------------------------------------------------------
// Aksi & utilitas
// ---------------------------------------------------------------------

@Composable
private fun AksiBerkas(
    berkas: BerkasArsipEntity,
    sibuk: Boolean,
    sedangUnduh: Boolean,
    onUnduh: () -> Unit,
    onBuatTautan: () -> Unit,
) {
    val papanKlip = LocalClipboardManager.current
    val adaLokal = !berkas.tdLocalPath.isNullOrBlank()

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TombolUtama(
                teks = if (adaLokal) "Sudah diunduh" else "Unduh",
                ikon = if (adaLokal) ArsipIcons.CircleCheck else ArsipIcons.Download,
                aktif = !sibuk && !sedangUnduh && !adaLokal,
                memuat = sedangUnduh,
                onClick = onUnduh,
                modifier = Modifier.weight(1f),
                penuh = false,
            )
            TombolCadangan(
                teks = "Buat tautan",
                ikon = ArsipIcons.Link,
                onClick = onBuatTautan,
                penuh = false,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        TombolCadangan(
            teks = "Salin nama berkas",
            ikon = ArsipIcons.Copy,
            penuh = true,
            onClick = { papanKlip.setText(AnnotatedString(berkas.nama)) },
        )
    }
}

private fun bukaBerkas(context: android.content.Context, berkas: BerkasArsipEntity) {
    val path = berkas.tdLocalPath ?: return
    val file = File(path)
    if (!file.exists()) return
    val uri: Uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: IllegalArgumentException) {
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, berkas.mime.ifBlank { "application/octet-stream" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, berkas.nama))
    }
}
