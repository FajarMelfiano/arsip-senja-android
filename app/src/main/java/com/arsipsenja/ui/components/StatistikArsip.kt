package com.arsipsenja.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arsipsenja.data.local.StatistikBerkas
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.util.formatUkuran

/**
 * Panel statistik dasbor: jumlah berkas, total ukuran, dan penanda "tak terbatas".
 * Angka memakai Fraunces agar terasa seperti label katalog.
 */
@Composable
fun StatistikArsip(
    statistik: StatistikBerkas,
    modifier: Modifier = Modifier,
    onKlikTotal: (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        KolomStatistik(
            label = "Berkas terindeks",
            nilai = statistik.jumlah.toString(),
            ikon = ArsipIcons.Database,
            warna = WarnaArsip.Amber,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        KolomStatistik(
            label = "Total ukuran",
            nilai = formatUkuran(statistik.total),
            ikon = ArsipIcons.HardDrive,
            warna = WarnaArsip.VioletTerang,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onKlikTotal != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onKlikTotal,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
        Spacer(Modifier.width(10.dp))
        KolomStatistik(
            label = "Kapasitas",
            // "Tak terbatas" kepotong jadi "Tak" di kartu 1/3 lebar — simbol ∞
            // (ikonnya juga infinity) muat dan terbaca dengan labelnya.
            nilai = "∞",
            ikon = ArsipIcons.Infinity,
            warna = WarnaArsip.Sukses,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KolomStatistik(
    label: String,
    nilai: String,
    ikon: ImageVector,
    warna: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .border(BorderStroke(1.dp, WarnaArsip.GarisRambut), RoundedCornerShape(10.dp))
            .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = ikon,
            contentDescription = null,
            tint = warna,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = nilai,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = Fraunces,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = WarnaArsip.Tinta,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = WarnaArsip.Tinta3,
            maxLines = 1,
        )
    }
}
