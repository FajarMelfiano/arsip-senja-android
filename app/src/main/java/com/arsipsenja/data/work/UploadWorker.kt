package com.arsipsenja.data.work

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arsipsenja.ArsipSenjaApp
import com.arsipsenja.data.repo.PenjagaVault
import com.arsipsenja.data.telegram.keBerkasMentah
import com.arsipsenja.data.telegram.keEntity
import com.arsipsenja.util.TipeFile
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

/**
 * Pekerja unggah berkas dari URI SAF ke koleksi Telegram.
 *
 * Berkas >2 GiB dipecah menjadi potongan ~1,9 GB (nama `asli.partN.ekstensi`)
 * karena batas unggah Telegram per pesan. Progres muncul di notifikasi.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val tag = "UploadWorker"

    /** Percobaan WorkManager saat ini (0 = percobaan pertama). */
    private val percobaanKe = params.runAttemptCount

    /**
     * Bungkus [kerjakan]: kegagalan yang lolos dari jalur dalam (mis.
     * pengecualian tak tertangkap) tetap membawa pesan galat di outputData,
     * agar UI bisa menampilkan alasan gagalnya — bukan hanya ID pekerjaan.
     */
    override suspend fun doWork(): Result {
        return try {
            kerjakan()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Pembatalan harus diteruskan apa adanya, bukan jadi FAILED.
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Unggah gagal tak tertangkap", e)
            gagalDenganPesan(e.message ?: "Unggah gagal tanpa keterangan.")
        }
    }

    private suspend fun kerjakan(): Result {
        val uri = inputData.getString(K_URI)?.let(Uri::parse)
            ?: return gagalDenganPesan("Berkas tidak ditemukan — pilih ulang berkasnya.")
        // ChatId Telegram grup/supergroup selalu NEGATIF (-100...), jadi
        // guard lama `<= 0` menolak SEMUA tujuan topik yang sah — itulah
        // akar kenapa upload tidak pernah berhasil sejak awal. Hanya 0
        // (tidak ada nilai) yang tidak sah.
        val koleksiId = inputData.getLong(K_KOLEKSI, 0L)
        if (koleksiId == 0L) return gagalDenganPesan("Tujuan unggah tidak sah — pilih ulang koleksi.")
        val topikId = inputData.getInt(K_TOPIK, 0)

        val wadah = (applicationContext as ArsipSenjaApp).wadah
        val repo = wadah.arsipRepository
        val telegram = repo.telegram

        val kunci = com.arsipsenja.data.local.KunciKoleksi(koleksiId, topikId)
        val koleksi = repo.alirKoleksi(kunci).first()
        if (koleksi == null) {
            Log.e(tag, "Koleksi $kunci tidak ada")
            return gagalDenganPesan("Koleksi tujuan tidak ditemukan — mungkin sudah dihapus. Pilih koleksi lain.")
        }

        val namaAsli = namaDariUri(uri)
        val mime = applicationContext.contentResolver.getType(uri) ?: "*/*"
        val tipe = TipeFile.dariMime(mime, namaAsli)

        val berkasCache = salinKeCache(uri, namaAsli)
            ?: return gagalDenganPesan("Gagal menyalin berkas — izin akses berkas mungkin sudah tidak berlaku. Pilih ulang berkasnya.")
        var gagal = false
        var pesanGagalTerakhir: String? = null

        try {
            val potongan = pecahBilaBerlu(berkasCache)
            var nomorKatalog = repo.jumlahBerkas(kunci)

            for ((indeks, potonganFile) in potongan.withIndex()) {
                val namaPotongan = if (potongan.size == 1) {
                    namaAsli
                } else {
                    com.arsipsenja.util.namaPotongan(namaAsli, indeks + 1)
                }
                try {
                    val terkirim = telegram.kirimBerkas(
                        chatId = koleksi.chatId,
                        path = potonganFile.absolutePath,
                        tipe = tipe,
                        caption = if (indeks == 0) "Unggah manual" else "",
                        topicId = koleksi.topicId,
                    )

                    pantauProgresUnggah(
                        telegram = telegram,
                        pesan = terkirim,
                        nama = namaPotongan,
                    )

                    val dikonfirmasi = telegram.tungguTerkirim(terkirim.id)
                    val mentah = dikonfirmasi.pesan.keBerkasMentah() ?: continue
                    nomorKatalog += 1
                    val entri = mentah
                        .keEntity(
                            koleksiId = koleksi.chatId,
                            topicId = koleksi.topicId,
                            nomorKatalog = nomorKatalog,
                            sekarang = System.currentTimeMillis(),
                        )
                        .copy(tdLocalPath = null)
                    repo.simpanHasilUnggah(entri)

                    Notifikasi.tampilkanUnggah(applicationContext, namaPotongan, 100, selesai = true)
                } catch (e: Exception) {
                    Log.e(tag, "Gagal unggah potongan $namaPotongan", e)
                    Notifikasi.tampilkanUnggah(applicationContext, namaPotongan, 0, selesai = true)
                    // Simpan alasan gagalnya (galat forum diterjemahkan);
                    // dipakai bila percobaan retry sudah habis.
                    pesanGagalTerakhir =
                        PenjagaVault.terjemahkanGalatForum(e.message ?: "Unggah gagal tanpa keterangan.")
                    gagal = true
                } finally {
                    potonganFile.delete()
                }
            }
        } finally {
            berkasCache.delete()
        }

        // Gagal per-potongan: beri kesempatan retry terbatas untuk galat
        // transien (jaringan), tapi pada percobaan terakhir kembalikan
        // FAILED dengan pesan — jangan gagal diam-diam tanpa alasan.
        if (!gagal) return Result.success()
        if (percobaanKe < MAKS_PERCOBAAN_GAGAL) return Result.retry()
        return gagalDenganPesan(pesanGagalTerakhir ?: "Unggah gagal — coba lagi.")
    }

    /** Kegagalan terminal yang membawa pesan galat untuk ditampilkan UI. */
    private fun gagalDenganPesan(pesan: String): Result =
        Result.failure(workDataOf(K_PESAN_GALAT to pesan))

    /** Pantau progres unggah berdasarkan fileId pada pesan terkirim. */
    private suspend fun pantauProgresUnggah(
        telegram: com.arsipsenja.data.telegram.TelegramClient,
        pesan: TdApi.Message,
        nama: String,
    ) {
        val fileId = idFileDariPesan(pesan) ?: return
        var terakhir = -1
        withTimeoutOrNull(BATAS_PROGRES) {
            telegram.progresBerkas
                .takeWhile { !(it.fileId == fileId && it.selesaiUnggah) }
                .collect { p ->
                    if (p.fileId != fileId) return@collect
                    val persen = p.persenUnggah
                    if (persen in 0..99 && persen != terakhir) {
                        terakhir = persen
                        Notifikasi.tampilkanUnggah(applicationContext, nama, persen, selesai = false)
                    }
                }
        }
        Notifikasi.tampilkanUnggah(applicationContext, nama, 99, selesai = false)
    }

    private fun salinKeCache(uri: Uri, nama: String): File? {
        return try {
            val dir = File(applicationContext.cacheDir, "unggahan").apply { mkdirs() }
            val keluar = File(dir, "${System.currentTimeMillis()}_$nama")
            applicationContext.contentResolver.openInputStream(uri)?.use { masuk ->
                FileOutputStream(keluar).use { output ->
                    masuk.copyTo(output)
                }
            } ?: return null
            keluar
        } catch (e: Exception) {
            Log.e(tag, "Salin berkas $nama gagal", e)
            null
        }
    }

    /**
     * Pecah berkas besar memakai [com.arsipsenja.util.pecahBerkas]; sumber
     * dihapus setelah dipecah agar cache tidak membengkak.
     */
    private fun pecahBilaBerlu(sumber: File): List<File> {
        if (!com.arsipsenja.util.perluPecah(sumber.length())) return listOf(sumber)
        val dir = File(applicationContext.cacheDir, "unggahan").apply { mkdirs() }
        val hasil = com.arsipsenja.util.pecahBerkas(sumber, dir)
        sumber.delete()
        return hasil
    }

    private fun idFileDariPesan(pesan: TdApi.Message): Int? {
        val isi = pesan.content ?: return null
        return when (isi) {
            is TdApi.MessageDocument -> isi.document?.document?.id
            is TdApi.MessageVideo -> isi.video?.video?.id
            is TdApi.MessageAudio -> isi.audio?.audio?.id
            is TdApi.MessagePhoto -> isi.photo?.sizes?.lastOrNull()?.photo?.id
            is TdApi.MessageAnimation -> isi.animation?.animation?.id
            else -> null
        }
    }

    private fun namaDariUri(uri: Uri): String {
        applicationContext.contentResolver.query(uri, null, null, null, null)?.use { kursor ->
            val indeks = kursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (indeks >= 0 && kursor.moveToFirst()) {
                kursor.getString(indeks)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "berkas"
    }

    companion object {
        const val K_URI = "uri"
        const val K_KOLEKSI = "koleksiId"

        /** Kunci outputData untuk pesan galat terminal (dibaca UI antrean). */
        const val K_PESAN_GALAT = "pesanGalat"

        /** Id topik forum tujuan; 0 = channel lama / tanpa topik. */
        const val K_TOPIK = "topicId"

        /** Batas retry untuk gagal per-potongan (runAttemptCount 0..1 → retry, ke-2 → FAILED berpesan). */
        private const val MAKS_PERCOBAAN_GAGAL = 2

        /** 1,9 miliar byte — di bawah batas ~2 GiB unggah Telegram. */
        private const val BATAS_POTONGAN = 1_900_000_000L
        private const val BUFFER = 1 shl 20
        private const val BATAS_PROGRES = 120_000L
    }
}
