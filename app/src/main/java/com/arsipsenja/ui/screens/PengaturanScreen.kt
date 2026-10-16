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
import androidx.compose.runtime.LaunchedEffect
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
import com.arsipsenja.data.repo.GrupDuplikat
import com.arsipsenja.data.telegram.StatusOtorisasi
import com.arsipsenja.ui.components.ChipArsip
import com.arsipsenja.ui.components.KolomIsian
import com.arsipsenja.ui.components.PopupStatus
import com.arsipsenja.ui.components.StatistikArsip
import com.arsipsenja.ui.components.TombolCadangan
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.components.formatJam
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.PengaturanViewModel
import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.TampilanDasbor
import com.arsipsenja.util.UrutBerkas
import com.arsipsenja.util.formatUkuran
import com.arsipsenja.util.formatTanggal

/**
 * Layar pengaturan: statistik, tampilan dasbor, urutan, kunci biometrik,
 * kredensial API, sesi Telegram, dan info aplikasi.
 */
@Composable
fun PengaturanScreen() {
    val vm: PengaturanViewModel = viewModel(factory = PengaturanViewModel.Pabrik)

    // Refresh tiap kali layar ditampilkan: status vault tidak boleh basi.
    // ViewModel dipertahankan antar-tab (saveState/restoreState), jadi
    // tanpa ini status dari kunjungan sebelumnya akan tampil lagi.
    LaunchedEffect(Unit) {
        vm.muatVaultAktif()
        vm.muatInfoVault()
    }

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
    var dialogDuplikat by remember { mutableStateOf(false) }
    var konfirmasiHapusTelegram1 by remember { mutableStateOf(false) }
    var konfirmasiHapusTelegram2 by remember { mutableStateOf(false) }

    val vaultAktif by vm.vaultAktif.collectAsState()
    val kandidatVault by vm.kandidatVault.collectAsState()
    val galatKandidat by vm.galatKandidat.collectAsState()
    val memuatVault by vm.memuatVault.collectAsState()
    val pesanVault by vm.pesanVault.collectAsState()
    val infoVault by vm.infoVault.collectAsState()
    val statusRealtime by vm.statusRealtime.collectAsState()
    val daftarDuplikat by vm.daftarDuplikat.collectAsState()
    val memuatDuplikat by vm.memuatDuplikat.collectAsState()
    val namaVaultAktif = infoVault?.nama
        ?: kandidatVault.firstOrNull { it.chatId == vaultAktif }?.nama
        ?: if (vaultAktif != null) "Grup ${vaultAktif}" else "Belum dipilih"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
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
            teks = "Pindai duplikat berkas",
            ikon = ArsipIcons.Copy,
            onClick = {
                vm.pindaiDuplikat()
                dialogDuplikat = true
            },
        )
        Spacer(Modifier.height(10.dp))

        TombolCadangan(
            teks = "Sinkronkan semua koleksi",
            ikon = ArsipIcons.RefreshCw,
            onClick = vm::sinkronSemua,
        )
        Spacer(Modifier.height(10.dp))
        BarisLabel(
            "Terakhir diperbarui",
            statusRealtime.sinkronTerakhir?.let { formatJam(it) } ?: "Belum pernah",
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
            galat = galatKandidat,
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

    if (dialogDuplikat) {
        DialogPindaiDuplikat(
            daftar = daftarDuplikat,
            memuat = memuatDuplikat,
            onRapikanLokal = {
                dialogDuplikat = false
                vm.rapikanDuplikatLokal()
            },
            onMintaHapusTelegram = {
                konfirmasiHapusTelegram1 = true
            },
            onTutup = { dialogDuplikat = false },
        )
    }

    if (konfirmasiHapusTelegram1) {
        AlertDialog(
            onDismissRequest = { konfirmasiHapusTelegram1 = false },
            containerColor = WarnaArsip.Veil,
            titleContentColor = WarnaArsip.Tinta,
            textContentColor = WarnaArsip.Tinta2,
            title = { Text("Hapus juga pesan di Telegram?") },
            text = {
                Text(
                    "Pesan duplikat akan dihapus langsung dari grup/channel Telegram sehingga berkas di server juga terhapus. Satu entri katalog awal akan tetap disimpan.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    konfirmasiHapusTelegram1 = false
                    konfirmasiHapusTelegram2 = true
                }) {
                    Text("Lanjutkan", color = WarnaArsip.Bahaya)
                }
            },
            dismissButton = {
                TextButton(onClick = { konfirmasiHapusTelegram1 = false }) {
                    Text("Batal", color = WarnaArsip.Tinta2)
                }
            },
        )
    }

    if (konfirmasiHapusTelegram2) {
        AlertDialog(
            onDismissRequest = { konfirmasiHapusTelegram2 = false },
            containerColor = WarnaArsip.Veil,
            titleContentColor = WarnaArsip.Tinta,
            textContentColor = WarnaArsip.Tinta2,
            title = { Text("Konfirmasi Akhir Penghapusan") },
            text = {
                Text(
                    "Pesan Telegram yang dihapus TIDAK DAPAT dipulihkan kembali. Yakin ingin menghapus seluruh pesan rangkap ini dari Telegram?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    konfirmasiHapusTelegram2 = false
                    dialogDuplikat = false
                    vm.rapikanDuplikatDenganTelegram()
                }) {
                    Text("Ya, Hapus dari Telegram", color = WarnaArsip.Bahaya)
                }
            },
            dismissButton = {
                TextButton(onClick = { konfirmasiHapusTelegram2 = false }) {
                    Text("Batal", color = WarnaArsip.Tinta2)
                }
            },
        )
    }

    // Satu popup status dalam satu waktu: galat kredensial/sinkron
    // didahulukan atas pesan vault. galatKandidat sengaja TETAP teks
    // inline di DialogPilihGrup — kontekstual untuk dialog itu, seperti
    // teks per baris antrean Unggah.
    PopupStatus(
        pesan = galat ?: pesanVault,
        onTutup = {
            if (galat != null) vm.tanganiGalat() else vm.habiskanPesanVault()
        },
    )
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
    galat: String?,
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
                    text = "Hanya grup yang kamu buat (kamu pemiliknya). " +
                        "Grup yang belum mode forum akan diaktifkan otomatis " +
                        "saat dipilih (butuh hak pemilik grup).",
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
                    // Bedakan "belum ada grup" dari "gagal memuat grup":
                    // empty state tanpa alasan membuat pengguna mengira
                    // daftarnya memang kosong padahal muatannya yang gagal.
                    Text(
                        text = galat
                            ?: "Belum ada grup milikmu — buat vault baru.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (galat != null) WarnaArsip.Bahaya else WarnaArsip.Tinta3,
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

/**
 * Dialog hasil pemindaian duplikat berkas: menampilkan daftar kelompok duplikat
 * (nama, ukuran, jumlah rangkap, tanggal) serta aksi rapikan lokal dan opsi lanjutan Telegram.
 */
@Composable
private fun DialogPindaiDuplikat(
    daftar: List<GrupDuplikat>,
    memuat: Boolean,
    onRapikanLokal: () -> Unit,
    onMintaHapusTelegram: () -> Unit,
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
                    text = "Pindai duplikat berkas",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarnaArsip.Tinta,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Menemukan berkas dengan koleksi, topik, nama, dan ukuran yang sama di indeks lokal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                )
                Spacer(Modifier.height(12.dp))

                if (memuat) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = WarnaArsip.Amber,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                } else if (daftar.isEmpty()) {
                    Text(
                        text = "Tidak ditemukan berkas duplikat di indeks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarnaArsip.Tinta3,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onTutup) {
                            Text("Tutup", color = WarnaArsip.Amber)
                        }
                    }
                } else {
                    val totalDuplikat = daftar.sumOf { it.jumlahRangkap - 1 }
                    Text(
                        text = "Ditemukan ${daftar.size} kelompok (${totalDuplikat} baris rangkap).",
                        style = MaterialTheme.typography.labelMedium,
                        color = WarnaArsip.Amber,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(daftar) { grup ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = WarnaArsip.Latar,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = grup.nama,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = WarnaArsip.Tinta,
                                        maxLines = 1,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = formatUkuran(grup.ukuran),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WarnaArsip.Tinta2,
                                        )
                                        Text(
                                            text = "${grup.jumlahRangkap}x rangkap",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WarnaArsip.Amber,
                                        )
                                        Text(
                                            text = formatTanggal(grup.tanggalTerbaru),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = WarnaArsip.Tinta3,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TombolUtama(
                        teks = "Rapikan (Indeks Lokal)",
                        ikon = ArsipIcons.Scissors,
                        onClick = onRapikanLokal,
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onMintaHapusTelegram,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Opsi lanjutan: Hapus juga di Telegram",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarnaArsip.Bahaya,
                        )
                    }
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
}
