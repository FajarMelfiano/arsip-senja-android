package com.arsipsenja.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arsipsenja.R
import com.arsipsenja.data.telegram.StatusOtorisasi
import com.arsipsenja.ui.components.KolomIsian
import com.arsipsenja.ui.components.PopupStatus
import com.arsipsenja.ui.components.TombolCadangan
import com.arsipsenja.ui.components.TombolUtama
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.Fraunces
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.LocalKontainer
import com.arsipsenja.ui.viewmodel.LoginViewModel

/**
 * Layar login Telegram empat langkah: kredensial API → nomor → kode → 2FA.
 * Konten diganti total mengikuti [StatusOtorisasi] dari TDLib.
 */
@Composable
fun LoginScreen() {
    val vm: LoginViewModel = viewModel(factory = LoginViewModel.Pabrik)
    val status by vm.status.collectAsState()
    val galat by vm.galat.collectAsState()
    val sibuk by vm.sibuk.collectAsState()

    val wadah = checkNotNull(LocalKontainer.current)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KepalaMerek()

        Spacer(Modifier.height(32.dp))

        when (val s = status) {
            is StatusOtorisasi.MenungguKredensial -> FormulirKredensial(vm, sibuk)
            is StatusOtorisasi.MenungguNomor -> FormulirNomor(vm, sibuk)
            is StatusOtorisasi.MenungguKode -> FormulirKode(vm, s, sibuk)
            is StatusOtorisasi.MenungguSandi -> FormulirSandi(vm, s, sibuk)
            is StatusOtorisasi.MenungguRegistrasi -> FormulirRegistrasi(vm, s, sibuk)
            is StatusOtorisasi.MenungguPerangkatLain -> KonfirmasiPerangkatLain()
            is StatusOtorisasi.Galat -> StatusGalat(s, onCoba = {
                // Bukan mulai(): method itu keluar lebih dulu begitu klien
                // ada, sehingga tombol ini tidak melakukan apa pun.
                wadah.telegramClient.mulaiUlang()
            })
            is StatusOtorisasi.Siap -> Loading("Membuka arsip…")
            is StatusOtorisasi.Menyiapkan -> Loading("Menyiapkan TDLib…")
            // Keluar kembali ke formulir, bukan spinner.
            //
            // Sebelumnya status ini dirender sebagai "Membuka arsip…".
            // Begitu pengguna menekan "Keluar sesi", aplikasi terjebak
            // selamanya tanpa jalan keluar dan hanya bisa dipaksa ditutup.
            is StatusOtorisasi.Keluar -> FormulirKredensial(vm, sibuk)
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = "api_id & api_hash diambil dari my.telegram.org — disimpan terenkripsi di perangkat.",
            style = MaterialTheme.typography.bodySmall,
            color = WarnaArsip.Tinta3,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Galat login lewat popup modal. StatusGalat (status otorisasi dari
    // TDLib) sengaja tetap inline — itu tampilan status, bukan pesan aksi.
    PopupStatus(
        pesan = galat,
        onTutup = vm::tanganiGalat,
    )
}

// ---------------------------------------------------------------------
// Merek
// ---------------------------------------------------------------------

@Composable
private fun KepalaMerek() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(1.dp, WarnaArsip.AmberGaris, RoundedCornerShape(10.dp))
                .background(WarnaArsip.AmberRedup, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ArsipIcons.BookOpen,
                contentDescription = null,
                tint = WarnaArsip.Amber,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Arsip Senja",
            style = MaterialTheme.typography.displaySmall,
            fontFamily = Fraunces,
            color = WarnaArsip.Tinta,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = WarnaArsip.Tinta2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Penanda langkah: "Langkah 2 dari 3". */
@Composable
private fun Langkah(bilang: Int, dari: Int) {
    Text(
        text = "Langkah $bilang dari $dari",
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = WarnaArsip.Amber,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun JudulLayar(judul: String) {
    Text(
        text = judul,
        style = MaterialTheme.typography.headlineSmall,
        color = WarnaArsip.Tinta,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun TeksBantuan(teks: String) {
    Text(
        text = teks,
        style = MaterialTheme.typography.bodyMedium,
        color = WarnaArsip.Tinta2,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
}

// ---------------------------------------------------------------------
// Formulir per langkah
// ---------------------------------------------------------------------

@Composable
private fun FormulirKredensial(vm: LoginViewModel, sibuk: Boolean) {
    val form by vm.kredensial.collectAsState()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Langkah(1, 3)
        JudulLayar("Kunci aplikasi")
        TeksBantuan(
            "Buka my.telegram.org, masuk dengan nomormu, lalu salin API development " +
                "lengkap (api_id dan api_hash). Kunci ini milikmu sendiri — tidak pernah " +
                "dibagikan ke server mana pun.",
        )
        KolomIsian(
            label = "api_id",
            nilai = form.apiId,
            onUbah = vm::ubahApiId,
            tempat = "12345678",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(14.dp))
        KolomIsian(
            label = "api_hash",
            nilai = form.apiHash,
            onUbah = vm::ubahApiHash,
            tempat = "32 karakter hex",
            imeAction = ImeAction.Done,
            onKirim = vm::simpanKredensial,
        )
        Spacer(Modifier.height(22.dp))
        TombolUtama(
            teks = "Simpan & mulai",
            onClick = vm::simpanKredensial,
            memuat = sibuk,
            aktif = !sibuk,
        )
    }
}

@Composable
private fun FormulirNomor(vm: LoginViewModel, sibuk: Boolean) {
    val nomor by vm.nomor.collectAsState()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Langkah(2, 3)
        JudulLayar("Nomor telepon")
        TeksBantuan(
            "TDLib akan mengirim kode verifikasi ke nomor ini. Pakai kode negara, " +
                "contoh +62 812 3456 7890.",
        )
        KolomIsian(
            label = "Nomor",
            nilai = nomor,
            onUbah = vm::ubahNomor,
            tempat = "+62 812 3456 7890",
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            onKirim = vm::kirimNomor,
        )
        Spacer(Modifier.height(22.dp))
        TombolUtama(
            teks = "Kirim kode",
            onClick = vm::kirimNomor,
            memuat = sibuk,
            aktif = !sibuk,
        )
    }
}

@Composable
private fun FormulirKode(vm: LoginViewModel, status: StatusOtorisasi.MenungguKode, sibuk: Boolean) {
    val kode by vm.kode.collectAsState()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Langkah(3, 3)
        JudulLayar("Kode verifikasi")
        TeksBantuan(
            "Kode dikirim lewat ${status.petunjuk}" +
                (if (status.hitungMundur > 0) " (± ${status.hitungMundur} detik)." else "."),
        )
        KolomIsian(
            label = "Kode",
            nilai = kode,
            onUbah = vm::ubahKode,
            tempat = "12345",
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            onKirim = vm::kirimKode,
        )
        Spacer(Modifier.height(22.dp))
        TombolUtama(
            teks = "Verifikasi",
            onClick = vm::kirimKode,
            memuat = sibuk,
            aktif = !sibuk,
        )
        Spacer(Modifier.height(10.dp))
        TombolCadangan(
            teks = "Kirim ulang kode",
            onClick = vm::kirimNomor,
            penuh = true,
        )
    }
}

@Composable
private fun FormulirSandi(vm: LoginViewModel, status: StatusOtorisasi.MenungguSandi, sibuk: Boolean) {
    val sandi by vm.sandi.collectAsState()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        JudulLayar("Kata sandi dua faktor")
        TeksBantuan(
            buildString {
                append("Akunmu dilindungi kata sandi 2FA.")
                status.petunjuk?.takeIf { it.isNotBlank() }?.let {
                    append(" Petunjuk: $it.")
                }
            },
        )
        KolomIsian(
            label = "Kata sandi",
            nilai = sandi,
            onUbah = vm::ubahSandi,
            tempat = "Kata sandi 2FA",
            sandi = true,
            imeAction = ImeAction.Done,
            onKirim = vm::kirimSandi,
        )
        Spacer(Modifier.height(22.dp))
        TombolUtama(
            teks = "Masuk",
            onClick = vm::kirimSandi,
            memuat = sibuk,
            aktif = !sibuk,
        )
    }
}

@Composable
private fun FormulirRegistrasi(
    vm: LoginViewModel,
    status: StatusOtorisasi.MenungguRegistrasi,
    sibuk: Boolean,
) {
    val r by vm.registrasi.collectAsState()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        JudulLayar("Buat akun baru")
        TeksBantuan(
            buildString {
                append("Nomor ini belum terdaftar. Isi nama untuk mendaftar.")
                status.tos?.takeIf { it.isNotBlank() }?.let {
                    append(" ")
                    append(it.take(280))
                }
            },
        )
        KolomIsian(
            label = "Nama depan",
            nilai = r.depan,
            onUbah = vm::ubahNamaDepan,
            tempat = "Sari",
        )
        Spacer(Modifier.height(14.dp))
        KolomIsian(
            label = "Nama belakang (opsional)",
            nilai = r.belakang,
            onUbah = vm::ubahNamaBelakang,
            tempat = "Prameswari",
            imeAction = ImeAction.Done,
            onKirim = vm::daftarkanAkun,
        )
        Spacer(Modifier.height(22.dp))
        TombolUtama(
            teks = "Daftar & masuk",
            onClick = vm::daftarkanAkun,
            memuat = sibuk,
            aktif = !sibuk,
        )
    }
}

@Composable
private fun KonfirmasiPerangkatLain() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = ArsipIcons.Smartphone,
            contentDescription = null,
            tint = WarnaArsip.Amber,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(12.dp))
        JudulLayar("Buka Telegram di perangkat lain")
        TeksBantuan(
            "Konfirmasi permintaan masuk yang muncul di Telegram pada perangkat " +
                "lain yang sudah masuk, atau pindai kode QR di sana.",
        )
    }
}

@Composable
private fun StatusGalat(status: StatusOtorisasi.Galat, onCoba: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = ArsipIcons.TriangleAlert,
            contentDescription = null,
            tint = WarnaArsip.Bahaya,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Gagal menyambung",
            style = MaterialTheme.typography.headlineSmall,
            color = WarnaArsip.Tinta,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = status.pesan,
            style = MaterialTheme.typography.bodyMedium,
            color = WarnaArsip.Tinta2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        TombolUtama(teks = "Coba lagi", onClick = onCoba)
    }
}

@Composable
private fun Loading(pesan: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        CircularProgressIndicator(
            color = WarnaArsip.Amber,
            strokeWidth = 2.dp,
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = pesan,
            style = MaterialTheme.typography.bodyMedium,
            color = WarnaArsip.Tinta2,
        )
    }
}
