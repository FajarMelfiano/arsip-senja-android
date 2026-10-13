package com.arsipsenja.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arsipsenja.ui.theme.SudutPil
import com.arsipsenja.ui.theme.WarnaArsip

/**
 * Chip filter pil: outline tipis saat nonaktif, menyala amber saat terpilih.
 * Dipakai untuk filter tipe berkas dan saringan koleksi di dasbor.
 */
@Composable
fun ChipArsip(
    label: String,
    terpilih: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ikon: ImageVector? = null,
    jumlah: Int? = null,
) {
    val interaksi = remember { MutableInteractionSource() }
    val ditekan by interaksi.collectIsPressedAsState()

    val latar = when {
        terpilih -> WarnaArsip.AmberRedup
        ditekan -> WarnaArsip.Permukaan3
        else -> WarnaArsip.Permukaan
    }
    val garis = when {
        terpilih -> WarnaArsip.AmberGaris
        ditekan -> WarnaArsip.GarisKuat
        else -> WarnaArsip.GarisRambut
    }
    val tinta = if (terpilih) WarnaArsip.AmberTerang else WarnaArsip.Tinta2

    Row(
        modifier = modifier
            .border(BorderStroke(1.dp, garis), SudutPil)
            .background(latar, SudutPil)
            .clickable(interactionSource = interaksi, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ikon != null) {
            Icon(
                imageVector = ikon,
                contentDescription = null,
                tint = tinta,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tinta,
            maxLines = 1,
        )
        if (jumlah != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = jumlah.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (terpilih) WarnaArsip.Amber else WarnaArsip.Tinta3,
            )
        }
    }
}

/** Deretan chip mendatar; layar membungkusnya dengan LazyRow bila perlu. */
@Composable
fun DeretanChip(modifier: Modifier = Modifier, isi: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier, content = isi)
}

/** Pemisah titik untuk baris meta ("1,4 MB · 14 Okt 2026"). */
@Composable
fun TitikPemisah(warna: Color = WarnaArsip.Tinta3) {
    Text(
        text = "·",
        style = MaterialTheme.typography.bodySmall,
        color = warna,
    )
}
