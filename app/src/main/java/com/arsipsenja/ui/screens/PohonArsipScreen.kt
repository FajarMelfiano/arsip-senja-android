package com.arsipsenja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arsipsenja.ui.components.IkonBerkas
import com.arsipsenja.ui.components.KeadaanKosong
import com.arsipsenja.ui.components.KolomCari
import com.arsipsenja.ui.components.ikonTipe
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.pohon.SimpulBerkas
import com.arsipsenja.ui.pohon.SimpulKoleksi
import com.arsipsenja.ui.pohon.SimpulPohon
import com.arsipsenja.ui.pohon.SimpulTipe
import com.arsipsenja.ui.pohon.tipeDariBerkas
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.PohonArsipViewModel
import com.arsipsenja.util.formatTanggal
import com.arsipsenja.util.formatUkuran

/**
 * Halaman Pohon arsip: struktur folder turunan Vault → Koleksi → Tipe →
 * Berkas, dengan buka/tutup per simpul, buka/tutup semua, pencarian nama
 * berkas, dan ketuk berkas untuk membuka detail (sama seperti Dasbor).
 */
@Composable
fun PohonArsipScreen(onBukaBerkas: (String) -> Unit) {
    val vm: PohonArsipViewModel = viewModel(factory = PohonArsipViewModel.Pabrik)

    val pohon by vm.pohon.collectAsState()
    val kataCari by vm.kataCari.collectAsState()
    val terbuka by vm.terbuka.collectAsState()

    val mencari = kataCari.isNotBlank()
    fun buka(idSimpul: String): Boolean = if (mencari) true else terbuka[idSimpul] == true

    // Ratakan simpul yang terlihat (akar sendiri dirender sebagai kartu ringkasan).
    val baris = mutableListOf<Pair<SimpulPohon, Int>>()
    fun tambah(simpul: SimpulPohon, kedalaman: Int) {
        baris += simpul to kedalaman
        if ((simpul is SimpulKoleksi || simpul is SimpulTipe) && buka(simpul.idSimpul)) {
            simpul.anak.forEach { tambah(it, kedalaman + 1) }
        }
    }
    pohon.akar.anak.forEach { tambah(it, 0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Pohon arsip",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Fraunces),
            color = WarnaArsip.Tinta,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${pohon.jumlahKoleksi} koleksi · ${pohon.jumlahBerkas} berkas · " +
                formatUkuran(pohon.totalUkuran),
            style = MaterialTheme.typography.bodyMedium,
            color = WarnaArsip.Tinta2,
        )
        Text(
            text = "Vault → koleksi → tipe berkas → berkas.",
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta3,
        )

        Spacer(Modifier.height(12.dp))

        KartuAkar(
            jumlahKoleksi = pohon.jumlahKoleksi,
            jumlahBerkas = pohon.jumlahBerkas,
            totalUkuran = pohon.totalUkuran,
        )

        Spacer(Modifier.height(12.dp))

        KolomCari(
            nilai = kataCari,
            onUbah = vm::ubahCari,
            tempat = "Cari nama berkas...",
            onKosongkan = vm::kosongkanCari,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = vm::bukaSemua) {
                Text(
                    text = "Buka semua",
                    style = MaterialTheme.typography.labelLarge,
                    color = WarnaArsip.AmberTerang,
                )
            }
            TextButton(onClick = vm::tutupSemua) {
                Text(
                    text = "Tutup semua",
                    style = MaterialTheme.typography.labelLarge,
                    color = WarnaArsip.Tinta2,
                )
            }
        }

        when {
            pohon.akar.anak.isEmpty() && !mencari -> KeadaanKosong(
                judul = "Belum ada koleksi",
                pesan = "Buat koleksi pertamamu di Dasbor > Kelola koleksi, " +
                    "lalu pohon arsipmu akan tumbuh di sini.",
                ikon = ArsipIcons.Folder,
            )
            baris.isEmpty() -> KeadaanKosong(
                judul = "Tidak ada yang cocok",
                pesan = "Coba kata kunci lain, atau kosongkan pencarian " +
                    "untuk melihat seluruh pohon.",
                ikon = ArsipIcons.Search,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(baris, key = { it.first.idSimpul }) { (simpul, kedalaman) ->
                    when (simpul) {
                        is SimpulKoleksi -> BarisKoleksiPohon(
                            simpul = simpul,
                            kedalaman = kedalaman,
                            terbuka = buka(simpul.idSimpul),
                            onJungkit = { vm.jungkit(simpul.idSimpul) },
                        )
                        is SimpulTipe -> BarisTipePohon(
                            simpul = simpul,
                            kedalaman = kedalaman,
                            terbuka = buka(simpul.idSimpul),
                            onJungkit = { vm.jungkit(simpul.idSimpul) },
                        )
                        is SimpulBerkas -> BarisBerkasPohon(
                            simpul = simpul,
                            kedalaman = kedalaman,
                            onBuka = { onBukaBerkas(simpul.berkas.id) },
                        )
                        else -> Unit
                    }
                    // Baris "Belum ada berkas" untuk koleksi kosong yang dibuka.
                    if (simpul is SimpulKoleksi && !simpul.yatim &&
                        simpul.anak.isEmpty() && buka(simpul.idSimpul)
                    ) {
                        BarisBelumAdaBerkas(kedalaman = kedalaman + 1)
                    }
                }
            }
        }
    }
}

/** Kartu ringkasan akar vault: ikon Database + hitungan + total ukuran. */
@Composable
private fun KartuAkar(jumlahKoleksi: Int, jumlahBerkas: Int, totalUkuran: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(WarnaArsip.Permukaan, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = ArsipIcons.Database,
            contentDescription = null,
            tint = WarnaArsip.Amber,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Vault",
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = Fraunces),
                color = WarnaArsip.Tinta,
            )
            Text(
                text = "$jumlahKoleksi koleksi · $jumlahBerkas berkas · ${formatUkuran(totalUkuran)}",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Garis panduan indentasi: lekukan + garis vertikal rambut per kedalaman. */
@Composable
private fun PanduanIndentasi(kedalaman: Int) {
    if (kedalaman > 0) {
        Spacer(Modifier.width(((kedalaman - 1) * 16).dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(WarnaArsip.GarisRambut),
        )
        Spacer(Modifier.width(9.dp))
    }
}

@Composable
private fun IkonBukaTutup(terbuka: Boolean) {
    Icon(
        imageVector = if (terbuka) ArsipIcons.ChevronDown else ArsipIcons.ChevronRight,
        contentDescription = if (terbuka) "Tutup" else "Buka",
        tint = WarnaArsip.Tinta3,
        modifier = Modifier.size(18.dp),
    )
}

/** Baris koleksi: kartu Folder + dot warna + chip label + hitungan. */
@Composable
private fun BarisKoleksiPohon(
    simpul: SimpulKoleksi,
    kedalaman: Int,
    terbuka: Boolean,
    onJungkit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
            .clickable(onClick = onJungkit)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        PanduanIndentasi(kedalaman)
        val warnaIkon = simpul.koleksi?.warna?.takeIf { it != 0 }?.let { Color(it) }
            ?: if (simpul.yatim) WarnaArsip.Tinta3 else WarnaArsip.VioletTerang
        Icon(
            imageVector = ArsipIcons.Folder,
            contentDescription = null,
            tint = warnaIkon,
            modifier = Modifier.size(16.dp),
        )
        if (simpul.koleksi?.warna != null && simpul.koleksi.warna != 0) {
            Spacer(Modifier.width(7.dp))
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(Color(simpul.koleksi.warna)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = simpul.nama,
                    style = MaterialTheme.typography.titleSmall,
                    color = WarnaArsip.Tinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                simpul.koleksi?.label?.takeIf { it.isNotBlank() }?.let { label ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = WarnaArsip.AmberTerang,
                        modifier = Modifier
                            .background(WarnaArsip.AmberRedup, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = "${simpul.jumlahBerkas} berkas · ${formatUkuran(simpul.totalUkuran)}",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        IkonBukaTutup(terbuka)
    }
}

/** Baris tipe berkas: ikon per tipe + hitungan, menjorok di bawah koleksi. */
@Composable
private fun BarisTipePohon(
    simpul: SimpulTipe,
    kedalaman: Int,
    terbuka: Boolean,
    onJungkit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onJungkit)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        PanduanIndentasi(kedalaman)
        Icon(
            imageVector = ikonTipe(simpul.tipe),
            contentDescription = null,
            tint = WarnaArsip.AmberTerang,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = simpul.nama,
                style = MaterialTheme.typography.titleSmall,
                color = WarnaArsip.Tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${simpul.jumlahBerkas} berkas · ${formatUkuran(simpul.totalUkuran)}",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        IkonBukaTutup(terbuka)
    }
}

/** Baris berkas (daun): badge ikon tipe + nama + ukuran/tanggal; ketuk → detail. */
@Composable
private fun BarisBerkasPohon(simpul: SimpulBerkas, kedalaman: Int, onBuka: () -> Unit) {
    val berkas = simpul.berkas
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBuka)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        PanduanIndentasi(kedalaman)
        IkonBerkas(tipe = tipeDariBerkas(berkas), ukuran = 32.dp, ikon = 16.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = berkas.nama,
                style = MaterialTheme.typography.bodyMedium,
                color = WarnaArsip.Tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatUkuran(berkas.ukuran)} · ${formatTanggal(berkas.tanggal)}",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Baris pengganti isi koleksi yang belum punya berkas. */
@Composable
private fun BarisBelumAdaBerkas(kedalaman: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        PanduanIndentasi(kedalaman)
        Text(
            text = "Belum ada berkas",
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta3,
        )
    }
}
