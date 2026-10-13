package com.arsipsenja.data.work

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arsipsenja.ArsipSenjaApp
import java.io.File
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pekerja unduh berkas dari Telegram.
 *
 * Hasil disimpan ke `Download/Arsip Senja` (MediaStore, API 29+) atau
 * `Android/data/…/files/Download` (API 26–28). Progres muncul di notifikasi.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val tag = "DownloadWorker"

    override suspend fun doWork(): Result {
        val berkasId = inputData.getString(K_BERKAS) ?: return Result.failure()
        val fileId = inputData.getInt(K_FILE_ID, 0)
        if (fileId <= 0) return Result.failure()

        val wadah = (applicationContext as ArsipSenjaApp).wadah
        val repo = wadah.arsipRepository
        val telegram = repo.telegram

        val berkas = repo.ambilBerkas(berkasId) ?: return Result.failure()
        val idNotif = Notifikasi.idUnduhKhusus(berkasId)
        Notifikasi.tampilkanUnduh(applicationContext, berkas.nama, 0, selesai = false)

        return try {
            var terakhir = -1
            withTimeoutOrNull(BATAS_PROGRES) {
                telegram.progresBerkas
                    .takeWhile { !(it.fileId == fileId && it.selesaiUnduh) }
                    .collect { p ->
                        if (p.fileId != fileId) return@collect
                        val persen = p.persenUnduh
                        if (persen in 0..99 && persen != terakhir) {
                            terakhir = persen
                            Notifikasi.tampilkanUnduh(applicationContext, berkas.nama, persen, false)
                            setProgress(workDataOf(K_PERSAN to persen, K_TERUNDUH to p.terunduh))
                        }
                    }
            }

            val file = telegram.unduh(fileId)
            val path = file.local?.path
            if (path.isNullOrBlank()) {
                Log.e(tag, "TDLib tidak mengembalikan path untuk fileId=$fileId")
                return Result.failure()
            }

            val tujuan = simpanKePenyimpanan(path, berkas.nama, berkas.mime)
            repo.tandaiLokal(berkasId, tujuan)
            Notifikasi.tampilkanUnduh(applicationContext, berkas.nama, 100, selesai = true)
            Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Unduh $berkasId gagal", e)
            Notifikasi.batalkan(applicationContext, idNotif)
            Result.retry()
        }
    }

    /** Salin hasil unduh TDLib ke penyimpanan pengguna; kembalikan path final. */
    private fun simpanKePenyimpanan(pathSumber: String, nama: String, mime: String?): String? {
        val sumber = File(pathSumber)
        if (!sumber.exists()) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            simpanMediaStore(sumber, nama, mime)
        } else {
            simpanEksternal(sumber, nama)
        }
    }

    private fun simpanMediaStore(sumber: File, nama: String, mime: String?): String? {
        val resolver = applicationContext.contentResolver
        val nilai = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, nama)
            put(MediaStore.Downloads.MIME_TYPE, mime ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/Arsip Senja")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val koleksi = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, nilai) ?: return null
        return try {
            resolver.openOutputStream(koleksi)?.use { keluar ->
                sumber.inputStream().use { masuk -> masuk.copyTo(keluar) }
            } ?: return null
            nilai.clear()
            nilai.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(koleksi, nilai, null, null)
            koleksi.toString()
        } catch (e: Exception) {
            Log.e(tag, "Simpan MediaStore gagal", e)
            resolver.delete(koleksi, null, null)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun simpanEksternal(sumber: File, nama: String): String? {
        val dir = applicationContext.getExternalFilesDir("Download")?.apply { mkdirs() }
            ?: File(applicationContext.filesDir, "Download").apply { mkdirs() }
        val tujuan = File(dir, nama)
        return try {
            sumber.copyTo(tujuan, overwrite = true)
            tujuan.absolutePath
        } catch (e: Exception) {
            Log.e(tag, "Simpan eksternal gagal", e)
            null
        }
    }

    companion object {
        const val K_BERKAS = "berkasId"
        const val K_FILE_ID = "fileId"
        const val K_PERSAN = "persen"
        const val K_TERUNDUH = "terunduh"
        private const val BATAS_PROGRES = 15L * 60L * 1000L
    }
}
