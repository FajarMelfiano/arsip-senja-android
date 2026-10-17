package com.arsipsenja.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arsipsenja.ArsipSenjaApp
import java.util.concurrent.TimeUnit

/**
 * Pemeriksa kesehatan vault berkala (Paket A2).
 *
 * Berjalan tiap 6 jam (hanya saat ada koneksi), memanggil
 * [com.arsipsenja.data.repo.ArsipRepository.infoVaultAktif], lalu menyimpan
 * hasil + cap waktu ke [com.arsipsenja.data.local.PengaturanStore] sehingga
 * Pengaturan selalu menampilkan status segar ("Sehat • dicek 12 menit lalu")
 * bukan status basi dari terakhir kali layar dibuka.
 *
 * Bila tidak ada vault yang dipilih, worker tidak menulis apa-apa —
 * tidak ada yang perlu dilaporkan sebagai "bermasalah".
 */
class VaultHealthWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as ArsipSenjaApp).wadah.arsipRepository
        return try {
            val info = repo.infoVaultAktif()
            if (info.chatId != null) {
                repo.simpanHasilCekVault(
                    sehat = info.forumSiap,
                    masalah = info.masalah,
                    dicekPada = System.currentTimeMillis(),
                )
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Jaringan putus dsb.: coba lagi di jadwal berikutnya.
            Result.retry()
        }
    }

    companion object {
        const val NAMA_UNIK = "cek-vault-berkala"

        /** Jadwalkan cek berkala; aman dipanggil berulang (kebijakan KEEP). */
        fun jadwalkan(wm: WorkManager) {
            val permintaan = PeriodicWorkRequestBuilder<VaultHealthWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            wm.enqueueUniquePeriodicWork(NAMA_UNIK, ExistingPeriodicWorkPolicy.KEEP, permintaan)
        }
    }
}
