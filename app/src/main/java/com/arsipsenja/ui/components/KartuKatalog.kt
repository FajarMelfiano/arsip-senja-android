package com.arsipsenja.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.SudutKartu
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.util.StatusLokal
import com.arsipsenja.util.TipeFile
import com.arsipsenja.util.formatTanggal
import com.arsipsenja.util.formatUkuran

/**
 * Kartu katalog bernomor untuk kisi dasbor: nomor indeks, aksen tipe,
 * nama berkas, meta ukuran/tanggal, dan stempel koleksi.
 * Tekan → glow amber (permukaan + tepi menyala, elevasi naik).
 */
@Composable
fun KartuKatalog(
    berkas: BerkasArsipEntity,
    modifier: Modifier = Modifier,
    namaKoleksi: String? = null,
    statusLokal: StatusLokal = StatusLokal.BELUM,
    onClick: () -> Unit,
) {
    val interaksi = remember { MutableInteractionSource() }
    val ditekan by interaksi.collectIsPressedAsState()
    val tipe = tipeDari(berkas)

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = SudutKartu,
        border = BorderStroke(
            1.dp,
            if (ditekan) WarnaArsip.AmberGaris else WarnaArsip.GarisRambut,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (ditekan) WarnaArsip.AmberRedup else WarnaArsip.Permukaan,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (ditekan) 6.dp else 0.dp,
            pressedElevation = 6.dp,
        ),
        interactionSource = interaksi,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NomorKatalog(
                    nomor = berkas.nomorKatalog,
                    warna = if (ditekan) WarnaArsip.AmberTerang else WarnaArsip.Amber,
                )
                Spacer(Modifier.weight(1f))
                IkonBerkas(tipe = tipe, ukuran = 30.dp, ikon = 15.dp)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = berkas.nama,
                style = MaterialTheme.typography.titleMedium,
                color = WarnaArsip.Tinta,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IkonTipeDatar(tipe = tipe, ukuran = 12.dp)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "${formatUkuran(berkas.ukuran)} · ${formatTanggal(berkas.tanggal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            StempelKoleksi(nama = namaKoleksi, statusLokal = statusLokal)
        }
    }
}

/**
 * Baris daftar: versi padat dari kartu katalog — badge tipe di kiri,
 * nomor + nama di tengah, status unduh di kanan.
 */
@Composable
fun BarisKatalog(
    berkas: BerkasArsipEntity,
    modifier: Modifier = Modifier,
    namaKoleksi: String? = null,
    statusLokal: StatusLokal = StatusLokal.BELUM,
    onClick: () -> Unit,
) {
    val interaksi = remember { MutableInteractionSource() }
    val ditekan by interaksi.collectIsPressedAsState()
    val tipe = tipeDari(berkas)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = SudutKartu,
        border = BorderStroke(
            1.dp,
            if (ditekan) WarnaArsip.AmberGaris else WarnaArsip.GarisRambut,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (ditekan) WarnaArsip.AmberRedup else WarnaArsip.Permukaan,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (ditekan) 4.dp else 0.dp),
        interactionSource = interaksi,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IkonBerkas(tipe = tipe, ukuran = 40.dp, ikon = 19.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NomorKatalog(nomor = berkas.nomorKatalog)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = berkas.nama,
                        style = MaterialTheme.typography.titleSmall,
                        color = WarnaArsip.Tinta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(formatUkuran(berkas.ukuran))
                        append(" · ")
                        append(formatTanggal(berkas.tanggal))
                        if (namaKoleksi != null) {
                            append(" · ")
                            append(namaKoleksi)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            PenandaStatus(statusLokal)
        }
    }
}

/** Stempel koleksi: pil berbingkai ungu senja; "Tanpa koleksi" bila yatim. */
@Composable
private fun StempelKoleksi(nama: String?, statusLokal: StatusLokal) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = if (nama != null) WarnaArsip.Violet.copy(alpha = 0.5f)
                    else WarnaArsip.GarisKuat,
                    shape = RoundedCornerShape(999.dp),
                )
                .background(
                    color = if (nama != null) WarnaArsip.VioletRedup
                    else WarnaArsip.Permukaan2,
                    shape = RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (nama != null) ArsipIcons.Folder else ArsipIcons.Inbox,
                    contentDescription = null,
                    tint = if (nama != null) WarnaArsip.VioletTerang else WarnaArsip.Tinta3,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = nama ?: "Tanpa koleksi",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (nama != null) WarnaArsip.Tinta else WarnaArsip.Tinta3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 120.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        PenandaStatus(statusLokal)
    }
}

/** Penanda status penyimpanan lokal di sisi kanan kartu/baris. */
@Composable
private fun PenandaStatus(status: StatusLokal) {
    when (status) {
        StatusLokal.BELUM -> Unit
        StatusLokal.PROSES -> Icon(
            imageVector = ArsipIcons.ArrowUpDown,
            contentDescription = "Sedang diunduh",
            tint = WarnaArsip.Amber,
            modifier = Modifier.size(15.dp),
        )
        StatusLokal.ADA -> Icon(
            imageVector = ArsipIcons.CircleCheck,
            contentDescription = "Tersedia di perangkat",
            tint = WarnaArsip.Sukses,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** Nilai [BerkasArsipEntity.tipe] aman terhadap data lama/tak dikenal. */
fun tipeDari(berkas: BerkasArsipEntity): TipeFile =
    TipeFile.entries.firstOrNull { it.name == berkas.tipe }
        ?: TipeFile.dariMime(berkas.mime, berkas.nama)
