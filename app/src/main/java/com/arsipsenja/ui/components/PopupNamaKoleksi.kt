package com.arsipsenja.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.AntreanPenamaan
import com.arsipsenja.ui.viewmodel.ItemPenamaan

/**
 * Satu-satunya sumber penamaan koleksi (dipakai Buat maupun Tarik).
 *
 * Satu popup dalam satu waktu, antrean diproses berurutan oleh ViewModel.
 * - [onSimpan]: nama valid → ViewModel membuat/mengubah koleksi lalu
 *   lanjut ke item antrean berikutnya.
 * - [onLewati]: item ini dilewati — Buat: tipe itu tidak dibuat;
 *   Tarik: nama topik/chat dari Telegram dipertahankan.
 * - [onBatalkanSemua]: hentikan antrean; yang sudah tersimpan TETAP
 *   tersimpan. Hanya tampil bila masih ada item sesudah yang ini
 *   ([adaLagi]).
 *
 * Validasi: nama kosong → Simpan nonaktif; nama duplikat koleksi lain →
 * ditolak di sini ("Nama sudah dipakai koleksi lain"). Nama sama dengan
 * entri yang sedang dinamai ulang (Tarik) BOLEH — itu Simpan tanpa ubah.
 */
@Composable
fun PopupNamaKoleksi(
    item: ItemPenamaan,
    namaTerpakai: List<String>,
    adaLagi: Boolean,
    onSimpan: (String) -> Unit,
    onLewati: () -> Unit,
    onBatalkanSemua: () -> Unit,
) {
    var nama by remember(item) { mutableStateOf(item.prefill) }
    val bersih = nama.trim()
    val kosong = bersih.isEmpty()
    val duplikat = AntreanPenamaan.namaDuplikat(bersih, namaTerpakai, item.namaAbaikan)
    val bolehSimpan = !kosong && !duplikat

    AlertDialog(
        onDismissRequest = onLewati,
        title = {
            Text(
                text = "Beri nama koleksi",
                style = MaterialTheme.typography.titleLarge,
                color = WarnaArsip.Tinta,
            )
        },
        text = {
            Column {
                item.tipe?.let { tipe ->
                    Text(
                        text = "Untuk tipe: ${tipe.label}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = WarnaArsip.AmberTerang,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = item.konteks,
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                )
                Spacer(Modifier.height(16.dp))
                KolomIsian(
                    label = "Nama koleksi",
                    nilai = nama,
                    onUbah = { nama = it },
                    tempat = "mis. ${item.prefill}",
                    onKirim = { if (bolehSimpan) onSimpan(bersih) },
                )
                if (duplikat) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Nama sudah dipakai koleksi lain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarnaArsip.Bahaya,
                    )
                } else if (kosong) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Nama koleksi wajib diisi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarnaArsip.Tinta3,
                    )
                }
            }
        },
        confirmButton = {
            TombolUtama(
                teks = "Simpan",
                onClick = { onSimpan(bersih) },
                penuh = false,
                aktif = bolehSimpan,
            )
        },
        dismissButton = {
            Row {
                TombolCadangan(
                    teks = "Lewati",
                    onClick = onLewati,
                    penuh = false,
                )
                if (adaLagi) {
                    Spacer(Modifier.width(8.dp))
                    TombolCadangan(
                        teks = "Batalkan semua",
                        onClick = onBatalkanSemua,
                        penuh = false,
                    )
                }
            }
        },
        containerColor = WarnaArsip.Veil,
        titleContentColor = WarnaArsip.Tinta,
        textContentColor = WarnaArsip.Tinta2,
    )
}
