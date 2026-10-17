package com.arsipsenja.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arsipsenja.ArsipSenjaApp

/**
 * Penerap ulang operasi mutasi yang tertunda (Paket B1).
 *
 * Dijadwalkan setiap kali mutasi dicatat ke log operasi dan saat aplikasi
 * mulai (untuk sisa dari sesi sebelumnya). Menerapkan ulang entri PENDING
 * secara idempoten lewat
 * [com.arsipsenja.data.repo.ArsipRepository.terapkanOperasiTertunda].
 * Gagal jaringan → entri tetap PENDING dan dicoba lagi lain waktu;
 * tidak ada mutasi yang hilang diam-diam.
 */
class SinkronOperasiWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as ArsipSenjaApp).wadah.arsipRepository
        return try {
            repo.terapkanOperasiTertunda()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val NAMA_UNIK = "sinkron-operasi-tertunda"

        /** Antrekan satu kali penerapan ulang; aman dipanggil berulang. */
        fun antrekanSekali(wm: WorkManager) {
            val permintaan = OneTimeWorkRequestBuilder<SinkronOperasiWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            wm.enqueueUniqueWork(NAMA_UNIK, ExistingWorkPolicy.APPEND, permintaan)
        }
    }
}
