package com.arsipsenja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arsipsenja.BuildConfig
import com.arsipsenja.data.telegram.StatusOtorisasi
import com.arsipsenja.ui.components.BilahPesan
import com.arsipsenja.ui.components.ChipArsip
import com.arsipsenja.ui.components.KolomIsian
import com.arsipsenja.ui.components.NadaPesan
import com.arsipsenja.ui.components.StatistikArsip
import com.arsipsenja.ui.components.TombolCadangan
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.PengaturanViewModel
import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.TampilanDasbor
import com.arsipsenja.util.UrutBerkas

/**
 * Layar pengaturan: statistik, tampilan dasbor, urutan, kunci biometrik,
 * kredensial API, sesi Telegram, dan info aplikasi.
 */
@Composable
fun PengaturanScreen() {
    val vm: PengaturanViewModel = viewModel(factory = PengaturanViewModel.Pabrik)

    val tampilan by vm.tampilan.collectAsState()
    val urut by vm.urut.collectAsState()
    val arah by vm.arah.collectAsState()
    val biometrik by vm.biometrik.collectAsState()
    val statistik by vm.statistik.collectAsState()
    val statusSesi by vm.statusSesi.collectAsState()
    val adaKredensial by vm.adaKredensial.collectAsState()
    val galat by vm.galat.collectAsState()
    val sibuk by vm.sibuk.collectAsState()

    var konfirmasiKeluar by remember { mutableStateOf(false) }
    var kredensialTerbuka by remember { mutableStateOf(false) }
    var apiIdBaru by remember { mutableStateOf("") }
    var apiHashBaru by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        BilahPesan(
            pesan = galat,
            nada = NadaPesan.SALAH,
            onTutup = vm::tanganiGalat,
        )
        if (galat != null) Spacer(Modifier.height(8.dp))

        Text(
            text = "Pengaturan",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Fraunces),
            color = WarnaArsip.Tinta,
        )

        Spacer(Modifier.height(16.dp))

        StatistikArsip(statistik = statistik)

        Spacer(Modifier.height(24.dp))

        // ---- Tampilan ----
        JudulBagian("Tampilan dasbor")
        Spacer(Modifier.height(10.dp))
        BarisLabel("Mode tampilan", "Kisi kartu atau daftar padat")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipArsip(
                label = "Kisi",
                terpilih = tampilan == TampilanDasbor.KISI,
                ikon = ArsipIcons.LayoutGrid,
                onClick = { vm.setTampilan(TampilanDasbor.KISI) },
            )
            ChipArsip(
                label = "Daftar",
                terpilih = tampilan == TampilanDasbor.DAFTAR,
                ikon = ArsipIcons.List,
                onClick = { vm.setTampilan(TampilanDasbor.DAFTAR) },
            )
        }

        Spacer(Modifier.height(14.dp))
        BarisLabel("Urutan berkas", "Dipakai di seluruh dasbor")
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UrutBerkas.entries.forEach { u ->
                ChipArsip(
                    label = u.label,
                    terpilih = urut == u,
                    onClick = { vm.setUrutan(u, arah) },
                )
            }
            ChipArsip(
                label = arah.label,
                terpilih = true,
                ikon = ArsipIcons.ArrowUpDown,
                onClick = {
                    vm.setUrutan(
                        urut,
                        if (arah == ArahUrut.TURUN) ArahUrut.NAIK else ArahUrut.TURUN,
                    )
                },
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- Keamanan ----
        JudulBagian("Keamanan")
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(WarnaArsip.Permukaan, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(
                imageVector = ArsipIcons.Fingerprint,
                contentDescription = null,
                tint = WarnaArsip.Amber,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Kunci biometrik",
                    style = MaterialTheme.typography.titleSmall,
                    color = WarnaArsip.Tinta,
                )
                Text(
                    text = "Minta sidik jari/wajah setiap kali Arsip Senja dibuka.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta3,
                )
            }
            Switch(
                checked = biometrik,
                onCheckedChange = vm::setBiometrik,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = WarnaArsip.Amber,
                    checkedThumbColor = WarnaArsip.Kanvas,
                    uncheckedTrackColor = WarnaArsip.Permukaan3,
                    uncheckedThumbColor = WarnaArsip.Tinta3,
                    uncheckedBorderColor = WarnaArsip.GarisKuat,
                ),
            )
        }

        Spacer(Modifier.height(24.dp))

        // ---- Akun Telegram ----
        JudulBagian("Akun Telegram")
        Spacer(Modifier.height(10.dp))
        val labelSesi = when (statusSesi) {
            is StatusOtorisasi.Siap -> "Tersambung"
            is StatusOtorisasi.Menyiapkan -> "Menyiapkan…"
            is StatusOtorisasi.MenungguKredensial -> "Belum ada kredensial"
            is StatusOtorisasi.Keluar -> "Sesi berakhir"
            else -> "Menunggu proses login"
        }
        BarisLabel("Status sesi", labelSesi)
        Spacer(Modifier.height(8.dp))

        TombolCadangan(
            teks = if (kredensialTerbuka) "Tutup kredensial API"
            else "Ganti kredensial api_id / api_hash",
            ikon = ArsipIcons.KeyRound,
            onClick = { kredensialTerbuka = !kredensialTerbuka },
        )

        if (kredensialTerbuka) {
            Spacer(Modifier.height(12.dp))
            KolomIsian(
                label = "api_id baru",
                nilai = apiIdBaru,
                onUbah = { apiIdBaru = it.filter(Char::isDigit).take(10) },
                tempat = if (adaKredensial) "terisi di penyimpanan aman" else "12345678",
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(10.dp))
            KolomIsian(
                label = "api_hash baru",
                nilai = apiHashBaru,
                onUbah = { apiHashBaru = it.trim() },
                tempat = if (adaKredensial) "terisi di penyimpanan aman" else "32 karakter hex",
                sandi = true,
                imeAction = ImeAction.Done,
                onKirim = { vm.gantiKredensial(apiIdBaru, apiHashBaru) },
            )
            Spacer(Modifier.height(12.dp))
            TombolUtama(
                teks = "Simpan kredensial",
                memuat = sibuk,
                aktif = !sibuk,
                onClick = { vm.gantiKredensial(apiIdBaru, apiHashBaru) },
            )
        }

        Spacer(Modifier.height(16.dp))

        TombolCadangan(
            teks = "Sinkronkan semua koleksi",
            ikon = ArsipIcons.RefreshCw,
            onClick = vm::sinkronSemua,
        )
        Spacer(Modifier.height(10.dp))
        TombolCadangan(
            teks = "Keluar dari sesi Telegram",
            ikon = ArsipIcons.LogOut,
            warnaGaris = WarnaArsip.Bahaya.copy(alpha = 0.5f),
            warnaTinta = WarnaArsip.Bahaya,
            onClick = { konfirmasiKeluar = true },
        )

        Spacer(Modifier.height(24.dp))

        // ---- Tentang ----
        JudulBagian("Tentang")
        Spacer(Modifier.height(8.dp))
        BarisLabel("Versi", BuildConfig.VERSION_NAME)
        BarisLabel("Mesin", "TDLib (MTProto) — tanpa server perantara")
        BarisLabel("Penyimpanan", "Indeks lokal Room + channel Telegram")
        Spacer(Modifier.height(24.dp))
    }

    if (konfirmasiKeluar) {
        AlertDialog(
            onDismissRequest = { konfirmasiKeluar = false },
            containerColor = WarnaArsip.Veil,
            titleContentColor = WarnaArsip.Tinta,
            textContentColor = WarnaArsip.Tinta2,
            title = { Text("Keluar dari sesi?") },
            text = {
                Text(
                    "Kamu akan kembali ke layar login. Kredensial api_id/api_hash " +
                        "tetap tersimpan agar masuk lagi cepat.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    konfirmasiKeluar = false
                    vm.keluarSesi()
                }) {
                    Text("Keluar", color = WarnaArsip.Bahaya)
                }
            },
            dismissButton = {
                TextButton(onClick = { konfirmasiKeluar = false }) {
                    Text("Batal", color = WarnaArsip.Tinta2)
                }
            },
        )
    }
}

@Composable
private fun JudulBagian(teks: String) {
    Text(
        text = teks,
        style = MaterialTheme.typography.titleSmall,
        color = WarnaArsip.Amber,
    )
}

@Composable
private fun BarisLabel(label: String, nilai: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = WarnaArsip.Tinta2,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = nilai,
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta,
        )
    }
}
