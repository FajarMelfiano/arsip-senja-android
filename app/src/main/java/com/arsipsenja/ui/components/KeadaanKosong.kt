package com.arsipsenja.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip

/**
 * Keadaan kosong/salah standar: ikon dalam medali, judul, pesan, aksi opsional.
 * Satu komponen untuk seluruh layar agar ritme kosongnya konsisten.
 */
@Composable
fun KeadaanKosong(
    judul: String,
    pesan: String,
    modifier: Modifier = Modifier,
    ikon: ImageVector = ArsipIcons.Inbox,
    aksiLabel: String? = null,
    onAksi: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .border(1.dp, WarnaArsip.GarisKuat, RoundedCornerShape(999.dp))
                .background(WarnaArsip.Permukaan, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ikon,
                contentDescription = null,
                tint = WarnaArsip.Tinta3,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = judul,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = com.arsipsenja.ui.theme.Fraunces,
            ),
            color = WarnaArsip.Tinta,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = pesan,
            style = MaterialTheme.typography.bodyMedium,
            color = WarnaArsip.Tinta2,
            textAlign = TextAlign.Center,
        )

        if (aksiLabel != null && onAksi != null) {
            Spacer(Modifier.height(20.dp))
            TombolUtama(aksiLabel, onAksi)
        }
    }
}
