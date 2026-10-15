package com.arsipsenja.data.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.ForegroundInfo

/**
 * Promosi worker ke foreground service.
 *
 * ## Kenapa tidak sekadar `setForeground(...)`
 *
 * Dua alasan, keduanya dipelajari langsung:
 *
 * **1. Tipe harus disebutkan eksplisit.** Konstruktor
 * `ForegroundInfo(id, notifikasi)` menyetel tipe foreground service menjadi 0.
 * Pada Android 14 (`targetSdk 34`) pemanggilan `startForeground()` tanpa tipe
 * melempar `MissingForegroundServiceTypeException`. Panggilan itu dijalankan di
 * thread `SystemForegroundService`, yaitu **di luar** `try` pada `doWork()`,
 * sehingga tidak tertangkap dan aplikasi langsung tertutup paksa.
 *
 * **2. Kegagalan tidak boleh mematikan proses.** Promosi ke foreground hanyalah
 * optimalisasi: tanpa itu worker tetap jalan, hanya tanpa prioritas dan bisa
 * dibatasi WorkManager. Jadi kegagalan di sini diturunkan menjadi log, bukan
 * diteruskan. Unggahan tetap berjalan.
 *
 * Tipe `dataSync` hanya berlaku sejak API 29; di bawah itu konstruktor dua
 * argumen dipakai seperti biasa.
 */
suspend fun androidx.work.ListenableWorker.naikkanKeForeground(
    context: Context,
    idNotif: Int,
    judul: String,
    namaBerkas: String,
    persen: Int = 0,
    selesai: Boolean = false,
) {
    try {
        val notifikasi = Notifikasi.bangunTransfer(
            context,
            idNotif,
            judul,
            namaBerkas,
            100,
            persen,
            selesai,
        )
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                idNotif,
                notifikasi,
                TIPE_FOREGROUND_TRANSFER,
            )
        } else {
            ForegroundInfo(idNotif, notifikasi)
        }
        setForegroundCompat(info)
    } catch (e: Exception) {
        // Sengaja ditelan: promosi foreground adalah tambahan, bukan syarat.
        Log.w("ArsipSenja", "Gagal naik ke foreground, lanjut tanpa: ${e.message}")
    }
}

/**
 * Panggilan `setForeground` yang hanya tersedia pada `CoroutineWorker`.
 *
 * Dipecah agar pemanggil tidak perlu tahu tipe worker-nya; coroutine-nya
 * sudah aktif karena `doWork()` berjalan di dalam dispatcher WorkManager.
 */
private suspend fun androidx.work.ListenableWorker.setForegroundCompat(
    info: ForegroundInfo,
) {
    when (this) {
        is androidx.work.CoroutineWorker -> setForeground(info)
        else -> Log.w("ArsipSenja", "Tipe worker tidak mendukung foreground")
    }
}
/**
 * Tipe foreground service untuk transfer arsip.
 *
 * Selalu `dataSync`: unggah dan unduh berkas memang sinkronisasi data. Nilainya
 * NamedConstant supaya maksudnya eksplisit dan bisa diuji.
 *
 * Yang menentukan konstruktor mana yang dipakai adalah versi SDK, bukan nilai ini:
 * di bawah API 29 tipe belum berlaku sehingga konstruktor dua argumen cukup.
 */
const val TIPE_FOREGROUND_TRANSFER: Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
