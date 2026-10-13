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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip

/**
 * Tombol utama Arsip Senja: isi amber, teks gelap, sudut 10dp.
 * Tekan → tonenya menua sedikit (AmberTua) sebagai umpan balik.
 */
@Composable
fun TombolUtama(
    teks: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ikon: ImageVector? = null,
    penuh: Boolean = true,
    aktif: Boolean = true,
    memuat: Boolean = false,
) {
    val interaksi = remember { MutableInteractionSource() }
    val ditekan by interaksi.collectIsPressedAsState()
    val latar = when {
        !aktif -> WarnaArsip.Permukaan3
        ditekan -> WarnaArsip.AmberTua
        else -> WarnaArsip.Amber
    }
    val tinta = if (aktif) WarnaArsip.Kanvas else WarnaArsip.Tinta3

    Row(
        modifier = modifier
            .then(if (penuh) Modifier.fillMaxWidth() else Modifier)
            .background(latar, RoundedCornerShape(10.dp))
            .clickable(interactionSource = interaksi, indication = null, enabled = aktif, onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (memuat) {
            Icon(
                imageVector = ArsipIcons.LoaderCircle,
                contentDescription = null,
                tint = tinta,
                modifier = Modifier.size(16.dp),
            )
        } else if (ikon != null) {
            Icon(
                imageVector = ikon,
                contentDescription = null,
                tint = tinta,
                modifier = Modifier.size(16.dp),
            )
        }
        if (memuat || ikon != null) Spacer(Modifier.width(8.dp))
        Text(
            text = teks,
            style = MaterialTheme.typography.labelLarge,
            color = tinta,
        )
    }
}

/**
 * Tombol cadangan: transparan dengan outline tipis — untuk aksi sekunder.
 */
@Composable
fun TombolCadangan(
    teks: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ikon: ImageVector? = null,
    warnaGaris: Color = WarnaArsip.GarisKuat,
    warnaTinta: Color = WarnaArsip.Tinta2,
    penuh: Boolean = true,
) {
    val interaksi = remember { MutableInteractionSource() }
    val ditekan by interaksi.collectIsPressedAsState()

    Row(
        modifier = modifier
            .then(if (penuh) Modifier.fillMaxWidth() else Modifier)
            .border(
                BorderStroke(1.dp, if (ditekan) WarnaArsip.AmberGaris else warnaGaris),
                RoundedCornerShape(10.dp),
            )
            .background(
                if (ditekan) WarnaArsip.AmberRedup else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(interactionSource = interaksi, indication = null, onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (ikon != null) {
            Icon(
                imageVector = ikon,
                contentDescription = null,
                tint = if (ditekan) WarnaArsip.AmberTerang else warnaTinta,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = teks,
            style = MaterialTheme.typography.labelLarge,
            color = if (ditekan) WarnaArsip.AmberTerang else warnaTinta,
        )
    }
}

/** Tombol ikon melingkar kecil (toolbar/aksi baris). */
@Composable
fun TombolIkon(
    ikon: ImageVector,
    aksi: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = WarnaArsip.Tinta2,
) {
    val interaksi = remember { MutableInteractionSource() }
    val ditekan by interaksi.collectIsPressedAsState()

    Row(
        modifier = modifier
            .background(
                if (ditekan) WarnaArsip.Permukaan3 else Color.Transparent,
                RoundedCornerShape(999.dp),
            )
            .clickable(interactionSource = interaksi, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ikon,
            contentDescription = aksi,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Isi tombol dengan ikon + teks dalam satu baris (untuk penggunaan di RowScope). */
@Composable
fun RowScope.LabelAksi(ikon: ImageVector, teks: String, warna: Color = WarnaArsip.Tinta2) {
    Icon(
        imageVector = ikon,
        contentDescription = null,
        tint = warna,
        modifier = Modifier.size(15.dp),
    )
    Spacer(Modifier.width(6.dp))
    Text(text = teks, style = MaterialTheme.typography.labelMedium, color = warna)
}
