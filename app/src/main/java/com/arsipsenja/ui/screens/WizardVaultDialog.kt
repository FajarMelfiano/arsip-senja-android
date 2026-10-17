package com.arsipsenja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arsipsenja.ui.components.KolomIsian
import com.arsipsenja.ui.components.TombolCadangan
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.PengaturanViewModel

/**
 * Dialog wizard "Siapkan Vault" (Paket A1): satu alur terpandu berisi
 * langkah CARI → BUAT → AKTIFKAN → VERIFIKASI. Tiap langkah menampilkan
 * indikator status; langkah yang gagal menampilkan pesan galat Bahasa
 * Indonesia beserta tombol "Coba lagi".
 */
@Composable
fun WizardVaultDialog(
    status: PengaturanViewModel.StatusWizard,
    onNamaUbah: (String) -> Unit,
    onBuat: () -> Unit,
    onCobaLagi: (PengaturanViewModel.LangkahWizard) -> Unit,
    onTutup: () -> Unit,
) {
    Dialog(onDismissRequest = onTutup) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = WarnaArsip.Veil,
            contentColor = WarnaArsip.Tinta,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                Text(
                    text = "Siapkan Vault",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarnaArsip.Tinta,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Satu alur membereskan semuanya: cari grup lamamu, " +
                        "buat grup baru bila perlu, aktifkan mode forum, lalu verifikasi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                )
                Spacer(Modifier.height(16.dp))

                PengaturanViewModel.LangkahWizard.entries.forEachIndexed { indeks, langkah ->
                    BarisLangkahWizard(
                        nomor = indeks + 1,
                        langkah = langkah,
                        statusLangkah = status.status[langkah]
                            ?: PengaturanViewModel.StatusLangkah.MENUNGGU,
                        galat = status.galat[langkah],
                        onCobaLagi = { onCobaLagi(langkah) },
                    )
                    if (indeks < PengaturanViewModel.LangkahWizard.entries.size - 1) {
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // Kasus khusus: pencarian selesai tapi tidak ada grup lama —
                // tawarkan pembuatan grup baru (tidak otomatis). Tetap tampil
                // bila pembuatan gagal agar pengguna bisa ubah nama & coba lagi.
                val statusBuat = status.status[PengaturanViewModel.LangkahWizard.BUAT]
                val cariSelesaiKosong =
                    status.status[PengaturanViewModel.LangkahWizard.CARI] ==
                        PengaturanViewModel.StatusLangkah.BERHASIL &&
                        status.chatId == null &&
                        statusBuat != PengaturanViewModel.StatusLangkah.BERHASIL &&
                        statusBuat != PengaturanViewModel.StatusLangkah.BERJALAN
                if (cariSelesaiKosong) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (statusBuat == PengaturanViewModel.StatusLangkah.GAGAL)
                            "Pembuatan grup gagal. Ubah nama bila perlu, lalu coba lagi:"
                        else "Tidak ada grup lama yang cocok. Buat grup vault baru:",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarnaArsip.Tinta2,
                    )
                    Spacer(Modifier.height(8.dp))
                    KolomIsian(
                        label = "Nama grup vault",
                        nilai = status.namaGrup,
                        onUbah = onNamaUbah,
                        tempat = "Arsip Senja",
                        imeAction = ImeAction.Done,
                        onKirim = onBuat,
                    )
                    Spacer(Modifier.height(8.dp))
                    TombolUtama(
                        teks = if (statusBuat == PengaturanViewModel.StatusLangkah.GAGAL)
                            "Coba buat lagi" else "Buat grup vault baru",
                        ikon = ArsipIcons.Plus,
                        onClick = onBuat,
                    )
                }

                if (status.selesai) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ArsipIcons.Check,
                            contentDescription = null,
                            tint = WarnaArsip.Sukses,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Vault \"${status.namaDipilih}\" siap dipakai.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarnaArsip.Sukses,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TombolCadangan(
                        teks = if (status.selesai) "Tutup" else "Nanti saja",
                        ikon = ArsipIcons.X,
                        onClick = onTutup,
                        penuh = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarisLangkahWizard(
    nomor: Int,
    langkah: PengaturanViewModel.LangkahWizard,
    statusLangkah: PengaturanViewModel.StatusLangkah,
    galat: String?,
    onCobaLagi: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        // Indikator status langkah.
        when (statusLangkah) {
            PengaturanViewModel.StatusLangkah.BERHASIL -> {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(WarnaArsip.Sukses.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ArsipIcons.Check,
                        contentDescription = null,
                        tint = WarnaArsip.Sukses,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            PengaturanViewModel.StatusLangkah.BERJALAN -> {
                CircularProgressIndicator(
                    color = WarnaArsip.Amber,
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 3.dp,
                )
            }
            PengaturanViewModel.StatusLangkah.GAGAL -> {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(WarnaArsip.Bahaya.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = ArsipIcons.CircleAlert,
                        contentDescription = null,
                        tint = WarnaArsip.Bahaya,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            PengaturanViewModel.StatusLangkah.MENUNGGU -> {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(WarnaArsip.GarisKuat),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = nomor.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = WarnaArsip.Tinta3,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = langkah.judul,
                style = MaterialTheme.typography.bodyMedium,
                color = WarnaArsip.Tinta,
            )
            Text(
                text = langkah.keterangan,
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
            )
            if (statusLangkah == PengaturanViewModel.StatusLangkah.GAGAL) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = galat ?: "Langkah gagal tanpa keterangan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Bahaya,
                )
                TextButton(onClick = onCobaLagi) {
                    Text(
                        text = "Coba lagi",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarnaArsip.Amber,
                    )
                }
            }
        }
    }
}
