package com.arsipsenja.ui.components

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.data.local.KunciRahasia
import com.arsipsenja.data.local.TipeKunci
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.theme.WarnaArsip
import kotlin.math.hypot

/**
 * Komponen UI kunci koleksi: papan pola 3x3, pilihan tipe kunci, bagian form
 * kunci baru, dan sheet buka kunci + pelacak cooldown percobaan.
 *
 * Kunci adalah gerbang privasi lokal, BUKAN enkripsi — teks di bawah
 * menjelaskannya dengan jujur dan tidak pernah mengklaim enkripsi.
 */

// ---------------------------------------------------------------------
// Cooldown in-memory: salah 5x berturut-turut → blokir 30 detik.
// ---------------------------------------------------------------------

/** Pelacak percobaan gagal buka kunci (in-memory, per koleksi). */
internal object UpayaBukaKunci {
    const val MAKS_GAGAL = 5
    const val DETIK_BLOKIR = 30L

    private data class Catatan(var gagal: Int = 0, var blokirSampai: Long = 0L)

    private val peta = mutableMapOf<KunciKoleksi, Catatan>()

    @Synchronized
    fun sisaDetik(kunci: KunciKoleksi): Long {
        val sisa = peta[kunci]?.blokirSampai?.minus(System.currentTimeMillis()) ?: 0L
        return (sisa / 1000L).coerceAtLeast(0L)
    }

    /**
     * Catat satu kegagalan.
     *
     * @return pesan cooldown bila baru saja mencapai batas, null bila
     * sekadar salah biasa (pemanggil menampilkan "Kunci salah.").
     */
    @Synchronized
    fun catatGagal(kunci: KunciKoleksi): String? {
        val catatan = peta.getOrPut(kunci) { Catatan() }
        catatan.gagal += 1
        if (catatan.gagal >= MAKS_GAGAL) {
            catatan.gagal = 0
            catatan.blokirSampai = System.currentTimeMillis() + DETIK_BLOKIR * 1000L
            return "Terlalu banyak percobaan. Coba lagi dalam $DETIK_BLOKIR detik."
        }
        return null
    }

    @Synchronized
    fun aturUlang(kunci: KunciKoleksi) {
        peta.remove(kunci)
    }
}

// ---------------------------------------------------------------------
// Biometrik
// ---------------------------------------------------------------------

/** True bila perangkat mendukung autentikasi biometrik kuat. */
fun perangkatDukungBiometrik(context: Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * Jalankan BiometricPrompt sekali (tanpa CryptoObject — cukup autentikasi).
 * Gagal permanen (tanpa hardware/terdaftar) → [onGagalPermanen] agar UI
 * jatuh kembali ke input rahasia; user tidak dikunci keluar.
 */
internal fun jalankanPromptBiometrik(
    aktivitas: FragmentActivity,
    judul: String,
    onSukses: () -> Unit,
    onBatal: () -> Unit,
    onGagalPermanen: () -> Unit,
) {
    if (!perangkatDukungBiometrik(aktivitas)) {
        onGagalPermanen()
        return
    }
    val executor = ContextCompat.getMainExecutor(aktivitas)
    val prompt = BiometricPrompt(
        aktivitas,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSukses()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    -> onGagalPermanen()
                    else -> onBatal()
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Biometrik tak cocok: biarkan prompt terbuka untuk percobaan berikutnya.
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(judul)
        .setSubtitle("Verifikasi identitas untuk membuka koleksi ini.")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText("Batal")
        .build()
    prompt.authenticate(info)
}

// ---------------------------------------------------------------------
// Papan pola 3x3 (ketuk berurutan atau seret antar titik)
// ---------------------------------------------------------------------

/**
 * Papan pola 3x3: ketuk titik berurutan atau seret jari melintasi titik.
 * Jalur antar titik digambar mengikuti urutan. Titik tak bisa diulang.
 */
@Composable
fun PapanPola(
    titik: List<Int>,
    onUbah: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
    aktif: Boolean = true,
) {
    BoxWithConstraints(
        modifier = modifier.size(216.dp),
        contentAlignment = Alignment.Center,
    ) {
        val kerapatan = LocalDensity.current
        val sisiPx = with(kerapatan) { maxWidth.toPx() }
        val pusat = remember(sisiPx) {
            List(9) { i ->
                val kolom = i % 3
                val baris = i / 3
                Offset(
                    sisiPx * (kolom + 1) / 4f,
                    sisiPx * (baris + 1) / 4f,
                )
            }
        }
        val radiusSentuh = sisiPx / 4f

        fun indeksDekat(posisi: Offset): Int? {
            var terbaik: Int? = null
            var jarakTerbaik = Float.MAX_VALUE
            pusat.forEachIndexed { i, c ->
                val jarak = hypot(posisi.x - c.x, posisi.y - c.y)
                if (jarak < radiusSentuh && jarak < jarakTerbaik) {
                    jarakTerbaik = jarak
                    terbaik = i
                }
            }
            return terbaik
        }

        fun tambah(posisi: Offset) {
            if (!aktif) return
            val i = indeksDekat(posisi) ?: return
            if (i !in titik) onUbah(titik + i)
        }

        Canvas(
            modifier = Modifier
                .size(216.dp)
                .pointerInput(aktif, titik) {
                    detectTapGestures(onTap = ::tambah)
                }
                .pointerInput(aktif) {
                    detectDragGestures(
                        onDragStart = ::tambah,
                        onDrag = { perubahan, _ -> tambah(perubahan.position) },
                    )
                },
        ) {
            // Garis jalur sesuai urutan titik.
            titik.windowed(2).forEach { (a, b) ->
                drawLine(
                    color = WarnaArsip.Amber,
                    start = pusat[a],
                    end = pusat[b],
                    strokeWidth = 14f,
                    cap = StrokeCap.Round,
                )
            }
            // Sembilan titik: terpilih = isi amber, bebas = garis tipis.
            pusat.forEachIndexed { i, c ->
                if (i in titik) {
                    drawCircle(color = WarnaArsip.Amber, radius = 22f, center = c)
                    drawCircle(color = WarnaArsip.Kanvas, radius = 8f, center = c)
                } else {
                    drawCircle(
                        color = WarnaArsip.Tinta3,
                        radius = 16f,
                        center = c,
                        style = Stroke(width = 4f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Pilihan tipe kunci
// ---------------------------------------------------------------------

/** Chip pilihan PIN / Kata sandi / Pola. */
@Composable
fun PilTipeKunci(
    tipe: TipeKunci,
    onPilih: (TipeKunci) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipArsip(
            label = "PIN",
            terpilih = tipe == TipeKunci.PIN,
            ikon = ArsipIcons.Hash,
            onClick = { onPilih(TipeKunci.PIN) },
        )
        ChipArsip(
            label = "Kata sandi",
            terpilih = tipe == TipeKunci.KATA_SANDI,
            ikon = ArsipIcons.KeyRound,
            onClick = { onPilih(TipeKunci.KATA_SANDI) },
        )
        ChipArsip(
            label = "Pola",
            terpilih = tipe == TipeKunci.POLA,
            ikon = ArsipIcons.LayoutGrid,
            onClick = { onPilih(TipeKunci.POLA) },
        )
    }
}

/** Label Bahasa Indonesia untuk tipe kunci (untuk judul sheet/input). */
fun labelTipeKunci(tipe: TipeKunci): String = when (tipe) {
    TipeKunci.PIN -> "PIN"
    TipeKunci.KATA_SANDI -> "kata sandi"
    TipeKunci.POLA -> "pola"
    TipeKunci.TIDAK_ADA -> "kunci"
}

// ---------------------------------------------------------------------
// Bagian form kunci baru (dipakai sheet buat + dialog ubah)
// ---------------------------------------------------------------------

/**
 * Bagian "Kunci koleksi" yang stateless: seluruh state milik pemanggil agar
 * data tidak hilang saat pembuatan gagal (form tidak ter-reset).
 */
@Composable
fun BagianKunciBaru(
    diaktifkan: Boolean,
    onAktif: (Boolean) -> Unit,
    tipe: TipeKunci,
    onTipe: (TipeKunci) -> Unit,
    rahasia: String,
    onRahasia: (String) -> Unit,
    konfirmasi: String,
    onKonfirmasi: (String) -> Unit,
    pola: List<Int>,
    onPola: (List<Int>) -> Unit,
    polaUlang: List<Int>,
    onPolaUlang: (List<Int>) -> Unit,
    biometrik: Boolean,
    onBiometrik: (Boolean) -> Unit,
    tampilBiometrik: Boolean,
    galat: String?,
) {
    var tampilSandi by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Kunci koleksi",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarnaArsip.Tinta,
                )
                Text(
                    text = "Gerbang privasi di perangkat ini — bukan enkripsi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta3,
                )
            }
            Switch(
                checked = diaktifkan,
                onCheckedChange = onAktif,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = WarnaArsip.Amber,
                    checkedThumbColor = WarnaArsip.Kanvas,
                    uncheckedTrackColor = WarnaArsip.Permukaan3,
                    uncheckedThumbColor = WarnaArsip.Tinta3,
                    uncheckedBorderColor = WarnaArsip.GarisKuat,
                ),
            )
        }

        if (!diaktifkan) return@Column

        Spacer(Modifier.height(8.dp))
        PilTipeKunci(tipe = tipe, onPilih = onTipe)
        Spacer(Modifier.height(10.dp))

        when (tipe) {
            TipeKunci.PIN -> {
                KolomIsian(
                    label = "PIN (4–6 angka)",
                    nilai = rahasia,
                    onUbah = { onRahasia(it.filter(Char::isDigit).take(6)) },
                    tempat = "••••",
                    sandi = true,
                    keyboardType = KeyboardType.NumberPassword,
                )
                Spacer(Modifier.height(10.dp))
                KolomIsian(
                    label = "Ketik ulang PIN",
                    nilai = konfirmasi,
                    onUbah = { onKonfirmasi(it.filter(Char::isDigit).take(6)) },
                    tempat = "••••",
                    sandi = true,
                    keyboardType = KeyboardType.NumberPassword,
                )
            }
            TipeKunci.KATA_SANDI -> {
                KolomIsian(
                    label = "Kata sandi (min 4 karakter)",
                    nilai = rahasia,
                    onUbah = onRahasia,
                    tempat = "Kata sandi rahasia",
                    sandi = !tampilSandi,
                    trailing = {
                        IconButton(onClick = { tampilSandi = !tampilSandi }) {
                            Icon(
                                imageVector = ArsipIcons.Eye,
                                contentDescription = if (tampilSandi) {
                                    "Sembunyikan kata sandi"
                                } else {
                                    "Tampilkan kata sandi"
                                },
                                tint = WarnaArsip.Tinta3,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                KolomIsian(
                    label = "Ketik ulang kata sandi",
                    nilai = konfirmasi,
                    onUbah = onKonfirmasi,
                    tempat = "Ketik ulang",
                    sandi = !tampilSandi,
                )
            }
            TipeKunci.POLA -> {
                Text(
                    text = "Gambar polamu (min 4 titik)",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarnaArsip.Tinta2,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PapanPola(titik = pola, onUbah = onPola)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onPola(emptyList()) }) {
                        Text("Hapus", color = WarnaArsip.Tinta2)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ulangi pola yang sama",
                    style = MaterialTheme.typography.labelMedium,
                    color = WarnaArsip.Tinta2,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PapanPola(titik = polaUlang, onUbah = onPolaUlang)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onPolaUlang(emptyList()) }) {
                        Text("Hapus", color = WarnaArsip.Tinta2)
                    }
                }
            }
            TipeKunci.TIDAK_ADA -> {
                Text(
                    text = "Pilih jenis kunci dulu (PIN, kata sandi, atau pola).",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Tinta2,
                )
            }
        }

        if (tampilBiometrik) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = biometrik,
                    onCheckedChange = onBiometrik,
                    colors = CheckboxDefaults.colors(
                        checkedColor = WarnaArsip.Amber,
                        checkmarkColor = WarnaArsip.Kanvas,
                    ),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Buka dengan biometrik",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarnaArsip.Tinta,
                )
            }
        }

        if (galat != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = galat,
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Bahaya,
            )
        }
    }
}

// ---------------------------------------------------------------------
// Sheet buka kunci
// ---------------------------------------------------------------------

/**
 * Sheet "Buka <nama koleksi>": input sesuai tipe kunci + tombol Buka +
 * tombol biometrik (bila diizinkan koleksi & didukung perangkat).
 *
 * @param verifikasi dipanggil dengan rahasia; hasil cocok/tidak lewat callback.
 * @param onBiometrikLolos dipanggil SETELAH BiometricPrompt sukses (pemanggil
 * menandai terbuka sesi + menutup sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetBukaKunci(
    koleksi: KoleksiEntity,
    onTutup: () -> Unit,
    onBerhasil: () -> Unit,
    verifikasi: (String, (Boolean) -> Unit) -> Unit,
    onBiometrikLolos: () -> Unit,
) {
    val context = LocalContext.current
    val tipe = remember(koleksi.kunci) {
        runCatching { TipeKunci.valueOf(koleksi.kunciTipe) }
            .getOrDefault(TipeKunci.TIDAK_ADA)
    }
    var masukan by remember(koleksi.kunci) { mutableStateOf("") }
    var pola by remember(koleksi.kunci) { mutableStateOf(emptyList<Int>()) }
    var galat by remember(koleksi.kunci) { mutableStateOf<String?>(null) }
    var sibuk by remember(koleksi.kunci) { mutableStateOf(false) }
    var tampilSandi by remember { mutableStateOf(false) }
    val dukungBio = remember { perangkatDukungBiometrik(context) }

    fun kirim(rahasia: String) {
        val sisa = UpayaBukaKunci.sisaDetik(koleksi.kunci)
        if (sisa > 0) {
            galat = "Terlalu banyak percobaan. Coba lagi dalam $sisa detik."
            return
        }
        val cek = KunciRahasia.validasi(tipe, rahasia)
        if (cek != null && tipe != TipeKunci.TIDAK_ADA) {
            // Format jelas salah (mis. PIN kosong) — ditolak tanpa
            // menghitung percobaan: rahasia tak valid tak bisa cocok.
            galat = cek
            return
        }
        sibuk = true
        verifikasi(rahasia) { cocok ->
            sibuk = false
            if (cocok) {
                UpayaBukaKunci.aturUlang(koleksi.kunci)
                onBerhasil()
            } else {
                galat = UpayaBukaKunci.catatGagal(koleksi.kunci)
                    ?: "Kunci salah. Coba lagi."
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onTutup,
        containerColor = WarnaArsip.Veil,
        contentColor = WarnaArsip.Tinta,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = ArsipIcons.Lock,
                    contentDescription = null,
                    tint = WarnaArsip.Amber,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Buka ${koleksi.nama}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarnaArsip.Tinta,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Koleksi ini diproteksi ${labelTipeKunci(tipe)}. " +
                    "Masukkan ${labelTipeKunci(tipe)} untuk melihat isinya.",
                style = MaterialTheme.typography.bodySmall,
                color = WarnaArsip.Tinta2,
            )
            Spacer(Modifier.height(16.dp))

            when (tipe) {
                TipeKunci.PIN -> KolomIsian(
                    label = "PIN",
                    nilai = masukan,
                    onUbah = { masukan = it.filter(Char::isDigit).take(6) },
                    tempat = "••••",
                    sandi = true,
                    keyboardType = KeyboardType.NumberPassword,
                    onKirim = { kirim(masukan) },
                )
                TipeKunci.KATA_SANDI -> KolomIsian(
                    label = "Kata sandi",
                    nilai = masukan,
                    onUbah = { masukan = it },
                    tempat = "Kata sandi",
                    sandi = !tampilSandi,
                    onKirim = { kirim(masukan) },
                    trailing = {
                        IconButton(onClick = { tampilSandi = !tampilSandi }) {
                            Icon(
                                imageVector = ArsipIcons.Eye,
                                contentDescription = if (tampilSandi) {
                                    "Sembunyikan kata sandi"
                                } else {
                                    "Tampilkan kata sandi"
                                },
                                tint = WarnaArsip.Tinta3,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
                TipeKunci.POLA -> Row(verticalAlignment = Alignment.CenterVertically) {
                    PapanPola(titik = pola, onUbah = { pola = it })
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { pola = emptyList() }) {
                        Text("Hapus", color = WarnaArsip.Tinta2)
                    }
                }
                TipeKunci.TIDAK_ADA -> Text(
                    text = "Koleksi ini tidak dikunci.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarnaArsip.Tinta2,
                )
            }

            if (galat != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = galat.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnaArsip.Bahaya,
                )
            }

            Spacer(Modifier.height(16.dp))
            TombolUtama(
                teks = "Buka",
                ikon = ArsipIcons.Unlock,
                aktif = !sibuk && tipe != TipeKunci.TIDAK_ADA,
                onClick = {
                    kirim(if (tipe == TipeKunci.POLA) KunciRahasia.polaKeRahasia(pola) else masukan)
                },
            )

            if (koleksi.bolehBiometrik && dukungBio) {
                Spacer(Modifier.height(10.dp))
                TombolCadangan(
                    teks = "Buka dengan sidik jari",
                    ikon = ArsipIcons.Fingerprint,
                    onClick = {
                        val aktivitas = context as? FragmentActivity ?: run {
                            galat = "Biometrik tak tersedia — pakai kunci rahasia."
                            return@TombolCadangan
                        }
                        jalankanPromptBiometrik(
                            aktivitas = aktivitas,
                            judul = "Buka ${koleksi.nama}",
                            onSukses = {
                                UpayaBukaKunci.aturUlang(koleksi.kunci)
                                onBiometrikLolos()
                                onBerhasil()
                            },
                            onBatal = { /* tetap di sheet, pakai kunci rahasia */ },
                            onGagalPermanen = {
                                galat = "Biometrik tak tersedia — pakai kunci rahasia."
                            },
                        )
                    },
                )
            }
        }
    }
}

/** Lencana "Terkunci" kecil (ikon gembok + teks) untuk baris koleksi. */
@Composable
fun LencanaTerkunci(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(WarnaArsip.Permukaan3, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = ArsipIcons.Lock,
            contentDescription = "Terkunci",
            tint = WarnaArsip.Tinta3,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Terkunci",
            style = MaterialTheme.typography.labelSmall,
            color = WarnaArsip.Tinta3,
        )
    }
}
