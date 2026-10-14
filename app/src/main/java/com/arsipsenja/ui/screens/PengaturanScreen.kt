package com.arsipsenja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arsipsenja.BuildConfig
import com.arsipsenja.data.repo.ArsipRepository
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
    var dialogGrup by remember { mutableStateOf(false) }
    var dialogVaultBaru by remember { mutableStateOf(false) }

    val vaultAktif by vm.vaultAktif.collectAsState()
    val kandidatVault by vm.kandidatVault.collectAsState()
    val memuatVault by vm.memuatVault.collectAsState()
    val pesanVault by vm.pesanVault.collectAsState()
    val infoVault by vm.infoVault.collectAsState()
    val namaVaultAktif = infoVault?.nama
        ?: kandidatVault.firstOrNull { it.chatId == vaultAktif }?.nama
        ?: if (vaultAktif != null) "Grup ${vaultAktif}" else "Belum dipilih"

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

        // ---- Vault Telegram ----
        JudulBagian("Vault Telegram")
        Spacer(Modifier.height(10.dp))

        BarisLabel("Vault aktif", namaVaultAktif)
        Spacer(Modifier.height(6.dp))
        // Status kesehatan vault: nama grup + Forum siap/Bermasalah + Perbaiki.
        val siap = infoVault?.forumSiap == true
        val adaVault = vaultAktif != null
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        when {
                            !adaVault -> WarnaArsip.Tinta3
                            siap -> WarnaArsip.Sukses
                            else -> WarnaArsip.Bahaya
                        },
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    !adaVault -> "Belum ada vault"
                    siap -> "Forum siap"
                    else -> "Bermasalah"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !adaVault -> WarnaArsip.Tinta3
                    siap -> WarnaArsip.Sukses
                    else -> WarnaArsip.Bahaya
                },
                modifier = Modifier.weight(1f),
            )
            if (adaVault && !siap) {
                TextButton(onClick = vm::perbaikiVault, enabled = !memuatVault) {
                    Text(
                        text = "Perbaiki",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarnaArsip.Amber,
                    )
                }
            }
        }
        if (infoVault?.masalah != null && adaVault && !siap) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = infoVault?.masalah.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta3,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Vault = grup penyimpanan; koleksi = topik di dalamnya. Aplikasi " +
                "menyiapkan vault otomatis saat kamu membuat koleksi.",
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta3,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TombolCadangan(
                teks = "Pilih grup lama",
                ikon = ArsipIcons.Folder,
                onClick = {
                    vm.muatKandidatVault()
                    dialogGrup = true
                },
                modifier = Modifier.weight(1f),
                penuh = false,
            )
            TombolCadangan(
                teks = "Buat vault baru",
                ikon = ArsipIcons.Plus,
                onClick = { dialogVaultBaru = true },
                modifier = Modifier.weight(1f),
                penuh = false,
            )
        }
        if (pesanVault != null) {
            Spacer(Modifier.height(8.dp))
            BilahPesan(
                pesan = pesanVault,
                nada = NadaPesan.NETRAL,
                onTutup = vm::habiskanPesanVault,
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

    if (dialogGrup) {
        DialogPilihGrup(
            kandidat = kandidatVault,
            memuat = memuatVault,
            vaultAktif = vaultAktif,
            onPilih = { chatId ->
                dialogGrup = false
                vm.pilihVault(chatId)
            },
            onTutup = { dialogGrup = false },
        )
    }

    if (dialogVaultBaru) {
        var namaBaru by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dialogVaultBaru = false },
            containerColor = WarnaArsip.Veil,
            titleContentColor = WarnaArsip.Tinta,
            textContentColor = WarnaArsip.Tinta2,
            title = { Text("Buat vault baru") },
            text = {
                KolomIsian(
                    label = "Nama grup vault",
                    nilai = namaBaru,
                    onUbah = { namaBaru = it },
                    tempat = "Arsip Senja",
                    imeAction = ImeAction.Done,
                    onKirim = {
                        dialogVaultBaru = false
                        vm.buatVaultBaru(namaBaru)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogVaultBaru = false
                    vm.buatVaultBaru(namaBaru)
                }) {
                    Text("Buat", color = WarnaArsip.Amber)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogVaultBaru = false }) {
                    Text("Batal", color = WarnaArsip.Tinta2)
                }
            },
        )
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

/**
 * Dialog pilihan grup vault: daftar supergroup milik pengguna, radio
 * single-select, lencana mode forum, daftar bisa scroll.
 */
@Composable
private fun DialogPilihGrup(
    kandidat: List<ArsipRepository.KandidatVault>,
    memuat: Boolean,
    vaultAktif: Long?,
    onPilih: (Long) -> Unit,
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
                    text = "Pilih grup vault",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarnaArsip.Tinta,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Grup yang belum mode forum akan diaktifkan otomatis saat dipilih " +
                        "(butuh hak pemilik grup).",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                )
                Spacer(Modifier.height(12.dp))

                if (memuat) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = WarnaArsip.Amber,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                } else if (kandidat.isEmpty()) {
                    Text(
                        text = "Tidak ada supergroup ditemukan di akun ini.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarnaArsip.Tinta3,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(kandidat, key = { it.chatId }) { k ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPilih(k.chatId) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                RadioButton(
                                    selected = k.chatId == vaultAktif,
                                    onClick = { onPilih(k.chatId) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = WarnaArsip.Amber,
                                        unselectedColor = WarnaArsip.Tinta3,
                                    ),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = k.nama,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = WarnaArsip.Tinta,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = if (k.forumSiap) "Forum siap"
                                        else "Forum akan diaktifkan",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (k.forumSiap) WarnaArsip.Sukses
                                        else WarnaArsip.AmberTerang,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onTutup) {
                        Text("Tutup", color = WarnaArsip.Tinta2)
                    }
                }
            }
        }
    }
}
