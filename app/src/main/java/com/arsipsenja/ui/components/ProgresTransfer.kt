package com.arsipsenja.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.util.formatUkuran

/**
 * Progres transfer satu berkas: baris ikon + nama/ukuran + persen,
 * lalu rel progres amber di bawahnya. [persen] -1 = tak diketahui (indeterminat).
 */
@Composable
fun ProgresTransfer(
    nama: String,
    persen: Int,
    modifier: Modifier = Modifier,
    arah: ArahTransfer = ArahTransfer.UNGGAH,
    ukuranSekarang: Long = -1L,
    ukuranTotal: Long = -1L,
    ketik: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(WarnaArsip.AmberRedup, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (arah == ArahTransfer.UNGGAH) ArsipIcons.Upload
                    else ArsipIcons.Download,
                    contentDescription = null,
                    tint = WarnaArsip.Amber,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = nama,
                    style = MaterialTheme.typography.titleSmall,
                    color = WarnaArsip.Tinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ketik ?: barisMeta(persen, ukuranSekarang, ukuranTotal, arah),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (persen in 0..100) "$persen%" else "—",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = com.arsipsenja.ui.theme.Fraunces,
                ),
                color = if (persen >= 100) WarnaArsip.Sukses else WarnaArsip.Amber,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (persen in 0..100) {
            LinearProgressIndicator(
                progress = { persen / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (persen >= 100) WarnaArsip.Sukses else WarnaArsip.Amber,
                trackColor = WarnaArsip.Permukaan3,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = WarnaArsip.Amber,
                trackColor = WarnaArsip.Permukaan3,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

/** Arah transfer — menentukan ikon dan label satuan. */
enum class ArahTransfer(val label: String) {
    UNGGAH("Mengunggah"),
    UNDUH("Mengunduh"),
}

private fun barisMeta(persen: Int, sekarang: Long, total: Long, arah: ArahTransfer): String {
    val aksi = if (arah == ArahTransfer.UNGGAH) "Mengunggah" else "Mengunduh"
    return when {
        persen >= 100 -> "Selesai"
        sekarang in 1..Long.MAX_VALUE && total > 0 ->
            "$aksi ${formatUkuran(sekarang)} dari ${formatUkuran(total)}"
        else -> "$aksi…"
    }
}
