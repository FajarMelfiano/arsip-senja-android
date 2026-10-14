package com.arsipsenja.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arsipsenja.data.repo.StatusRealtime
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialog progres sinkron otomatis (realtime). Dibuka dari tombol status
 * di header Dasbor. Menjelaskan keadaan mesin secara jujur:
 * - sinkron otomatis hanya aktif saat aplikasi berjalan,
 * - jumlah berkas masuk / diabaikan / duplikat via jalur realtime,
 * - waktu sinkron terakhir dan berapa kali sinkron otomatis berjalan.
 */
@Composable
fun DialogRealtime(
    status: StatusRealtime,
    onTutup: () -> Unit,
) {
    val (judul, ikon, warna) = when {
        status.menyinkronkan ->
            Triple("Menyinkronkan…", ArsipIcons.LoaderCircle, WarnaArsip.Amber)
        status.tersambung ->
            Triple("Sinkron otomatis aktif", ArsipIcons.CircleCheck, WarnaArsip.Sukses)
        else ->
            Triple("Menghubungkan…", ArsipIcons.WifiOff, WarnaArsip.Tinta3)
    }
    AlertDialog(
        onDismissRequest = onTutup,
        icon = {
            Icon(
                imageVector = ikon,
                contentDescription = null,
                tint = warna,
            )
        },
        title = {
            Text(
                text = judul,
                style = MaterialTheme.typography.titleLarge,
                color = WarnaArsip.Tinta,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BarisRealtime(
                    label = "Koneksi Telegram",
                    nilai = if (status.tersambung) "Tersambung" else "Menghubungkan…",
                )
                BarisRealtime(
                    label = "Terakhir diperbarui",
                    nilai = status.sinkronTerakhir?.let { formatJam(it) } ?: "Belum pernah",
                )
                BarisRealtime(
                    label = "Berkas masuk otomatis",
                    nilai = "${status.pesanDiproses}",
                )
                BarisRealtime(
                    label = "Pesan diabaikan",
                    nilai = "${status.pesanDiabaikan}",
                )
                BarisRealtime(
                    label = "Duplikat dilewati",
                    nilai = "${status.duplikatDilewati}",
                )
                BarisRealtime(
                    label = "Sinkron otomatis berjalan",
                    nilai = "${status.sinkronOtomatis} kali",
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sinkron otomatis aktif saat aplikasi berjalan — berkas baru " +
                        "langsung masuk tanpa menekan tombol. Bila aplikasi dimatikan " +
                        "total, perubahan baru masuk saat aplikasi dibuka lagi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta3,
                )
            }
        },
        confirmButton = {
            TombolUtama(
                teks = "Tutup",
                onClick = onTutup,
                penuh = false,
            )
        },
        containerColor = WarnaArsip.Veil,
        titleContentColor = WarnaArsip.Tinta,
        textContentColor = WarnaArsip.Tinta2,
    )
}

@Composable
private fun BarisRealtime(label: String, nilai: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = WarnaArsip.Tinta2,
        )
        Text(
            text = nilai,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = WarnaArsip.Tinta,
        )
    }
}

/** Format "HH:mm" untuk cap waktu sinkron terakhir. */
fun formatJam(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale("id", "ID")).format(Date(ms))
