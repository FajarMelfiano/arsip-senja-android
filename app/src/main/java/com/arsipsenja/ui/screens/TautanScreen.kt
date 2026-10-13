@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.arsipsenja.ui.screens

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arsipsenja.data.local.JenisTautan
import com.arsipsenja.data.local.TautanEntity
import com.arsipsenja.ui.components.BilahPesan
import com.arsipsenja.ui.components.ChipArsip
import com.arsipsenja.ui.components.KeadaanKosong
import com.arsipsenja.ui.components.NadaPesan
import com.arsipsenja.ui.components.TombolIkon
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.TautanViewModel
import com.arsipsenja.util.formatTanggalWaktu

/**
 * Layar tautan berbagi: buat tautan undangan koleksi, salin tautan pesan,
 * cabut undangan yang sudah terlanjur tersebar.
 */
@Composable
fun TautanScreen() {
    val vm: TautanViewModel = viewModel(factory = TautanViewModel.Pabrik)

    val tautan by vm.tautan.collectAsState()
    val koleksi by vm.koleksi.collectAsState()
    val pesan by vm.pesan.collectAsState()
    val sibuk by vm.sibuk.collectAsState()
    val hasilBaru by vm.hasilBaru.collectAsState()

    val papanKlip = LocalClipboardManager.current
    var koleksiDipilih by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Long?>(null)
    }

    LaunchedEffect(hasilBaru) {
        val url = hasilBaru ?: return@LaunchedEffect
        papanKlip.setText(AnnotatedString(url))
        vm.habiskanHasil()
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

        Text(
            text = "Tautan berbagi",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Fraunces),
            color = WarnaArsip.Tinta,
        )
        Text(
            text = "Tautan pesan per berkas, undangan per koleksi — semua bisa dicabut",
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta3,
        )

        Spacer(Modifier.height(16.dp))

        if (koleksi.isNotEmpty()) {
            Text(
                text = "Buat tautan undangan koleksi",
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
                        terpilih = koleksiDipilih == k.chatId,
                        onClick = { koleksiDipilih = k.chatId },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TombolUtama(
                teks = "Buat & salin tautan undangan",
                ikon = ArsipIcons.Link,
                aktif = koleksiDipilih != null && !sibuk,
                memuat = sibuk,
                onClick = {
                    koleksiDipilih?.let { vm.buatTautanUndangan(it) }
                },
            )
            Spacer(Modifier.height(16.dp))
        }

        if (tautan.isEmpty()) {
            KeadaanKosong(
                ikon = ArsipIcons.Link,
                judul = "Belum ada tautan",
                pesan = "Buat tautan undangan di atas, atau buat tautan pesan dari " +
                    "halaman detail sebuah berkas.",
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = 24.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(tautan, key = { it.id }) { t ->
                    KartuTautan(
                        tautan = t,
                        namaKoleksi = koleksi.firstOrNull { it.chatId == t.chatId }?.nama,
                        sibuk = sibuk,
                        onSalin = { papanKlip.setText(AnnotatedString(t.url)) },
                        onCabut = { vm.cabutTautan(t.id) },
                        onHapus = { vm.hapusCatatan(t.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KartuTautan(
    tautan: TautanEntity,
    namaKoleksi: String?,
    sibuk: Boolean,
    onSalin: () -> Unit,
    onCabut: () -> Unit,
    onHapus: () -> Unit,
) {
    val undangan = tautan.jenis == JenisTautan.UNDANGAN.name

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (undangan) ArsipIcons.Folder else ArsipIcons.Link,
                contentDescription = null,
                tint = if (tautan.dicabut) WarnaArsip.Tinta3 else WarnaArsip.Amber,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = tautan.label.ifBlank { namaKoleksi ?: "Tautan" },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (tautan.dicabut) WarnaArsip.Tinta3 else WarnaArsip.Tinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(if (undangan) "Undangan koleksi" else "Tautan pesan")
                        append(" · ")
                        append(formatTanggalWaktu(tautan.dibuatPada))
                        if (tautan.dicabut) append(" · dicabut")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta3,
                )
            }
            if (!tautan.dicabut) {
                TombolIkon(
                    ikon = ArsipIcons.Copy,
                    aksi = "Salin tautan",
                    onClick = onSalin,
                )
            }
            if (undangan && !tautan.dicabut) {
                TombolIkon(
                    ikon = ArsipIcons.Unlink,
                    aksi = "Cabut tautan",
                    tint = WarnaArsip.Bahaya,
                    onClick = onCabut,
                )
            }
            TombolIkon(
                ikon = ArsipIcons.Trash2,
                aksi = "Hapus catatan",
                tint = WarnaArsip.Tinta3,
                onClick = onHapus,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = tautan.url,
            style = MaterialTheme.typography.bodySmall,
            color = if (tautan.dicabut) WarnaArsip.Tinta3 else WarnaArsip.VioletTerang,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
