package com.arsipsenja

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ForegroundInfo
import com.arsipsenja.data.work.TIPE_FOREGROUND_TRANSFER
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uji instrumented: membuktikan mechanismenya di runtime Android sungguhan.
 *
 * Catatan: nama method di sini memakai camelCase tanpa spasi. Method androidTest
 * menjadi nama di DEX, dan DEX menolak spasi pada SimpleName.
 *
 * ## Yang dibuktikan
 *
 * Hipotesis awal: force close saat unggah disebabkan
 * `MissingForegroundServiceTypeException` karena `ForegroundInfo` dua argumen
 * menyetel tipe foreground service menjadi 0.
 *
 * Uji ini memeriksa bagian yang bisa diperiksa di sini: nilai tipe yang
 * benar-benar dibawa objek `ForegroundInfo` yang kita buat. Kalau nilainya 0,
 * pemanggilan `startForeground()` di Android 14 pasti ditolak sistem — dan
 * itu yang membuat proses ditutup paksa di thread
 * `SystemForegroundService`, di luar jangkauan `try` pada `doWork()`.
 *
 * ## Kenapa ini instrumented, bukan unit test biasa
 *
 * `ForegroundInfo` dan `ServiceInfo` berasal dari framework Android, sehingga
 * nilainya hanya bisa dipercaya di runtime. Unit test JVM dengan
 * `isReturnDefaultValues` akan mengembalikan 0 untuk semuanya dan membuat
 * pengujian ini berbohong.
 */
@RunWith(AndroidJUnit4::class)
class TipeForegroundInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun perangkat_ini_API_34_atau_lebih() {
        // Penjaga: kalau AVD ini bukan API 34+, pengujian di bawah tidak
        // mewakili kondisi yang membuat crash.
        assertThat(Build.VERSION.SDK_INT).isAtLeast(34)
    }

    @Test
    fun foregroundInfo_tiga_argumen_membawa_tipe_dataSync() {
        val info = ForegroundInfo(
            1234,
            notifikasiUji(),
            TIPE_FOREGROUND_TRANSFER,
        )
        assertThat(info.foregroundServiceType)
            .isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertThat(info.foregroundServiceType).isNotEqualTo(0)
    }

    @Test
    fun foregroundInfoDuaArgumenBernilaiNol() {
        // Inilah bukti hipotesis: constructor lama menghasilkan tipe 0.
        // Nilai inilah yang memicu MissingForegroundServiceTypeException
        // di Android 14.
        val info = ForegroundInfo(1234, notifikasiUji())
        assertThat(info.foregroundServiceType).isEqualTo(0)
    }

    @Test
    fun manifest_mendeklarasikan_izin_foreground_lengkap() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS,
        )
        val diizinkan = info.requestedPermissions?.toSet() ?: emptySet()
        assertThat(diizinkan).contains("android.permission.FOREGROUND_SERVICE")
        assertThat(diizinkan).contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC")
    }

    @Test
    fun service_workmanager_menyatakan_tipe_dataSync() {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_SERVICES,
        )
        val service = info.services?.firstOrNull {
            it.name.endsWith("SystemForegroundService")
        }
        assertThat(service).isNotNull()
        // Penting: tanpa deklarasi ini di manifest, startForeground ditolak
        // walau tipe sudah diteruskan lewat ForegroundInfo.
        val tipe = service!!.foregroundServiceType
        assertThat(tipe and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC).isNotEqualTo(0)
        Log.i(TAG, "Service type terverifikasi: $tipe")
    }

    private companion object {
        const val TAG = "TipeForegroundTest"
    }
}

/** Notifikasi kosong yang sah, cukup untuk menguji konstruktor ForegroundInfo. */
private fun notifikasiUji(): Notification =
    Notification.Builder(ApplicationProvider.getApplicationContext(), "uji")
        .setContentTitle("uji")
        .build()