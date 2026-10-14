package com.arsipsenja

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.arsipsenja.ui.components.KeadaanKosong
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.nav.ArsipNavHost
import com.arsipsenja.ui.theme.ArsipSenjaTheme
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.LocalKontainer

/**
 * Aktivitas utama: FragmentActivity (wajib untuk BiometricPrompt) yang
 * memuat Compose — gerbang biometrik opsional lalu rangka navigasi penuh.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wadah = (application as ArsipSenjaApp).wadah

        setContent {
            ArsipSenjaTheme {
                CompositionLocalProvider(LocalKontainer provides wadah) {
                    GerbangKunci(wadah = wadah)
                }
            }
        }
    }

    /**
     * Trigger foreground mesin realtime: sinkron ringan sekali bila
     * sinkron terakhir sudah melewati ambang (anti-spam).
     */
    override fun onStart() {
        super.onStart()
        (application as ArsipSenjaApp).wadah.arsipRepository.saatKembaliForeground()
    }
}

/**
 * Gerbang kunci biometrik: bila diaktifkan di Pengaturan, minta verifikasi
 * saat aplikasi dibuka. Perangkat tanpa biometrik/sandi selalu lolos
 * (gagal terbuka lebih buruk daripada gagal terkunci bagi pengguna awam).
 */
@Composable
private fun GerbangKunci(wadah: AppContainer) {
    val context = LocalContext.current
    var terkunci by remember { mutableStateOf(false) }
    var perluBiometrik by remember { mutableStateOf(false) }
    var percobaan by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val harusKunci = try {
            wadah.pengaturan.perluBiometrik()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membaca pengaturan biometrik: ${e.message}")
            false
        }
        perluBiometrik = harusKunci
        terkunci = harusKunci
        if (harusKunci) percobaan = 1
    }

    LaunchedEffect(percobaan) {
        if (!perluBiometrik || !terkunci || percobaan == 0) return@LaunchedEffect
        val aktivitas = context as? FragmentActivity ?: run {
            terkunci = false
            return@LaunchedEffect
        }
        mintaBiometrik(
            aktivitas = aktivitas,
            onLolos = { terkunci = false },
            onGagalPermanen = { terkunci = false },
            onBatal = { /* tetap terkunci; tersedia tombol coba lagi */ },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarnaArsip.Kanvas),
        contentAlignment = Alignment.Center,
    ) {
        if (terkunci) {
            KeadaanKosong(
                ikon = ArsipIcons.Fingerprint,
                judul = "Arsip terkunci",
                pesan = "Verifikasi sidik jari atau wajah untuk membuka arsip " +
                    "pribadimu.",
                aksiLabel = "Verifikasi sekarang",
                onAksi = { percobaan += 1 },
            )
        } else {
            ArsipNavHost()
        }
    }
}

/**
 * Jalankan BiometricPrompt. [onGagalPermanen] dipanggil bila perangkat tidak
 * punya biometrik/sama sekali — pengguna tetap boleh masuk.
 */
private fun mintaBiometrik(
    aktivitas: FragmentActivity,
    onLolos: () -> Unit,
    onGagalPermanen: () -> Unit,
    onBatal: () -> Unit,
) {
    val tersedia = BiometricManager.from(aktivitas)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    if (tersedia != BiometricManager.BIOMETRIC_SUCCESS) {
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
                onLolos()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_VENDOR,
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
        .setTitle(aktivitas.getString(R.string.judul_prompt_biometrik))
        .setSubtitle(aktivitas.getString(R.string.deskripsi_prompt_biometrik))
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        )
        .setNegativeButtonText(aktivitas.getString(R.string.aksi_batal))
        .build()

    prompt.authenticate(info)
}

private const val TAG = "MainActivity"
