package com.arsipsenja.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.NadaStatus
import com.arsipsenja.ui.viewmodel.PesanStatus

/**
 * Satu-satunya sumber pesan status (sukses, galat, info) di aplikasi.
 * Menggantikan strip status lama yang rapuh (nada ditebak dari substring).
 *
 * Modal: wajib ditutup manual via tombol "Mengerti" (atau tombol aksi
 * kedua bila ada) — tidak ada auto-dismiss. Satu popup dalam satu waktu;
 * pesan baru dari ViewModel mengganti isi popup yang sedang tampil.
 *
 * @param onAksiUlang aksi tombol kedua "Buat vault baru & coba lagi".
 * Dipanggil TANPA menutup popup dulu — aksi ViewModel yang menutupnya
 * sendiri (agar state retry tidak ikut terhapus oleh habiskanPesan).
 */
@Composable
fun PopupStatus(
    pesan: PesanStatus?,
    onTutup: () -> Unit,
    onAksiUlang: (() -> Unit)? = null,
) {
    if (pesan == null) return
    val (judul, ikon, warna) = when (pesan.nada) {
        NadaStatus.SUKSES -> Triple("Berhasil", ArsipIcons.CircleCheck, WarnaArsip.Sukses)
        NadaStatus.GALAT -> Triple("Terjadi galat", ArsipIcons.CircleAlert, WarnaArsip.Bahaya)
        NadaStatus.INFO -> Triple("Info", ArsipIcons.Info, WarnaArsip.Tinta2)
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
            Text(
                text = pesan.teks,
                style = MaterialTheme.typography.bodyMedium,
                color = WarnaArsip.Tinta2,
            )
        },
        confirmButton = {
            TombolUtama(
                teks = "Mengerti",
                onClick = onTutup,
                penuh = false,
            )
        },
        dismissButton = if (pesan.aksiUlang && onAksiUlang != null) {
            {
                TombolCadangan(
                    teks = "Buat vault baru & coba lagi",
                    onClick = onAksiUlang,
                    penuh = false,
                )
            }
        } else {
            null
        },
        containerColor = WarnaArsip.Veil,
        titleContentColor = WarnaArsip.Tinta,
        textContentColor = WarnaArsip.Tinta2,
    )
}
