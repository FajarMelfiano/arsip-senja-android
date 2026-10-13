package com.arsipsenja.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip

/**
 * Bilah pesan sementara di bawah kepala layar (pengganti snackbar):
 * strip amber lembut dengan teks pesan dan tombol tutup.
 */
@Composable
fun BilahPesan(
    pesan: String?,
    onTutup: () -> Unit,
    modifier: Modifier = Modifier,
    nada: NadaPesan = NadaPesan.NETRAL,
) {
    if (pesan.isNullOrBlank()) return

    val (latar, garis, tinta) = when (nada) {
        NadaPesan.NETRAL -> Triple(WarnaArsip.Permukaan2, WarnaArsip.GarisKuat, WarnaArsip.Tinta2)
        NadaPesan.BAIK -> Triple(WarnaArsip.Permukaan, WarnaArsip.Sukses.copy(alpha = 0.5f), WarnaArsip.Sukses)
        NadaPesan.SALAH -> Triple(WarnaArsip.Permukaan, WarnaArsip.Bahaya.copy(alpha = 0.6f), WarnaArsip.Bahaya)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(latar, RoundedCornerShape(10.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = when (nada) {
                NadaPesan.BAIK -> ArsipIcons.CircleCheck
                NadaPesan.SALAH -> ArsipIcons.CircleAlert
                NadaPesan.NETRAL -> ArsipIcons.Info
            },
            contentDescription = null,
            tint = garis,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = pesan,
            style = MaterialTheme.typography.bodySmall,
            color = tinta,
            modifier = Modifier.weight(1f),
        )
        TombolIkon(
            ikon = ArsipIcons.X,
            aksi = "Tutup pesan",
            tint = WarnaArsip.Tinta3,
            onClick = onTutup,
        )
    }
}

enum class NadaPesan { NETRAL, BAIK, SALAH }
