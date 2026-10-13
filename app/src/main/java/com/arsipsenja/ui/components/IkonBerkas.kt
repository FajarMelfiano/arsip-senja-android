package com.arsipsenja.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.util.TipeFile

/** Ikon vektor untuk tipe berkas —Lucide outline, tanpa emoji. */
fun ikonTipe(tipe: TipeFile): ImageVector = when (tipe) {
    TipeFile.GAMBAR -> ArsipIcons.Image
    TipeFile.VIDEO -> ArsipIcons.Film
    TipeFile.AUDIO -> ArsipIcons.Music
    TipeFile.DOKUMEN -> ArsipIcons.FileText
    TipeFile.ARSIP -> ArsipIcons.Archive
    TipeFile.LAINNYA -> ArsipIcons.File
}

/** Warna aksen per tipe berkas (badge & penanda kartu). */
fun warnaTipe(tipe: TipeFile): Color = when (tipe) {
    TipeFile.GAMBAR -> WarnaArsip.VioletTerang
    TipeFile.VIDEO -> WarnaArsip.Amber
    TipeFile.AUDIO -> WarnaArsip.Sukses
    TipeFile.DOKUMEN -> WarnaArsip.Tinta
    TipeFile.ARSIP -> WarnaArsip.AmberTerang
    TipeFile.LAINNYA -> WarnaArsip.Tinta2
}

/**
 * Badge ikon berkas: kotak membulat dengan warna aksen tipe.
 * Dipakai di kartu katalog (kecil) dan baris daftar (besar).
 */
@Composable
fun IkonBerkas(
    tipe: TipeFile,
    modifier: Modifier = Modifier,
    ukuran: Dp = 36.dp,
    ikon: Dp = 18.dp,
) {
    val warna = warnaTipe(tipe)
    Box(
        modifier = modifier
            .size(ukuran)
            .background(warna.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ikonTipe(tipe),
            contentDescription = tipe.label,
            tint = warna,
            modifier = Modifier.size(ikon),
        )
    }
}

/**
 * Ikon tipe berkas tanpa latar — untuk baris meta yang padat.
 */
@Composable
fun IkonTipeDatar(tipe: TipeFile, modifier: Modifier = Modifier, ukuran: Dp = 14.dp) {
    Icon(
        imageVector = ikonTipe(tipe),
        contentDescription = tipe.label,
        tint = warnaTipe(tipe),
        modifier = modifier.size(ukuran),
    )
}

/**
 * Nomor indeks katalog bergaya Fraunces ("No. 024") — penanda utama
 * metafora kartu katalog Arsip Senja.
 */
@Composable
fun NomorKatalog(
    nomor: Int,
    modifier: Modifier = Modifier,
    warna: Color = WarnaArsip.Amber,
) {
    Text(
        text = if (nomor > 0) "No. " + String.format(java.util.Locale.US, "%03d", nomor) else "No. —",
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = com.arsipsenja.ui.theme.Fraunces,
            fontSize = 12.sp,
            letterSpacing = 0.6.sp,
        ),
        color = warna,
    )
}
