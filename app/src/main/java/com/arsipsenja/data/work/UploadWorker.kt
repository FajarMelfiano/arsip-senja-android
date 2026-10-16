package com.arsipsenja.data.work

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arsipsenja.ArsipSenjaApp
import com.arsipsenja.data.repo.PenjagaVault
import com.arsipsenja.data.telegram.keBerkasMentah
import com.arsipsenja.data.telegram.keEntity
import com.arsipsenja.util.HashBerkas
import com.arsipsenja.util.HeaderArsip
import com.arsipsenja.util.susun
import com.arsipsenja.util.TipeFile
import java.io.File
import java.io.FileOutputStream
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
            // Periksa status jeda dari database sebelum memulai pekerjaan.
            val workId = inputData.getString(K_WORK_ID)
            if (workId.isNullOrBlank()) {
                return gagalDenganPesan("ID pekerjaan tidak valid")
            }
            val repo = (applicationContext as ArsipSenjaApp).wadah.arsipRepository
            // Status jeda dibaca dari Room: persisten melewati tutup aplikasi
            // maupun reboot. Selama dijeda, worker keluar via Result.retry() —
            // WorkManager menjadwalkannya ulang dengan backoff, dan unggahan
            // TIDAK berjalan sampai pengguna menekan "lanjutkan".
            // Status jeda sudah terekspos ke UI lewat Room (amatiJedaUnggah),
            // jadi tidak perlu outputData tambahan di sini.
            if (repo.sedangJeda(workId)) {
                return Result.retry()
            }
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
        // Umumkan nama berkas lewat progress agar UI antrean bisa
        // menampilkannya (WorkInfo tidak membawa inputData).
        setProgress(workDataOf(K_NAMA to namaAsli))
        val mime = applicationContext.contentResolver.getType(uri) ?: "*/*"
        val tipe = TipeFile.dariMime(mime, namaAsli)

        // Notifikasi per berkas + foreground service. Tanpa foreground,
        // WorkManager membatasi worker sekitar 10 menit sehingga unggahan besar
        // terpotong dan seluruh pekerjaan diulang dari awal.
        val idNotif = Notifikasi.idUnggahKhusus(uri.toString())
        Notifikasi.tampilkanUnggah(applicationContext, namaAsli, 0, false, idNotif)
        naikkanKeForeground(
            context = applicationContext,
            idNotif = idNotif,
            judul = "Mengunggah…",
            namaBerkas = namaAsli,
        )

        val ukuran = ukuranDari(uri)
        val berkasCache = salinKeCache(uri, namaAsli)
            ?: return gagalDenganPesan(
                "Tidak bisa membaca berkas — izin akses mungkin sudah dicabut. Pilih ulang berkasnya.",
            )

        // Hash dihitung sekali dari berkas asli, lalu ikut di setiap header
        // caption. Inilah yang membuat arsip dapat diverifikasi dan duplikat
        // dapat dikenali.
        val hashAsli = HashBerkas.hitungBerkas(berkasCache)
        val grupId = if (hashAsli != null) HeaderArsip.uuidDariHash(hashAsli) else java.util.UUID.randomUUID().toString()
        var gagal = false
        var pesanGagalTerakhir: String? = null

        try {
            ensureCukupRuang(berkasCache.length())
            var nomorKatalog = repo.jumlahBerkas(kunci)

            // Kirim satu berkas logis. Untuk berkas kecil ini satu kali; untuk
            // berkas besar dipecah berurutan agar cache tidak pernah menyimpan
            // seluruh potongan sekaligus.
            val kirimPotongan: suspend (File, Int, Int) -> Unit =
                suspendPotongan@{ potonganFile, indeks, total ->
                val namaPotongan = if (total == 1) {
                    namaAsli
                } else {
                    com.arsipsenja.util.namaPotongan(namaAsli, indeks)
                }
                try {
                    // Guard idempoten: percobaan ulang (attempt > 0) ATAU lanjutan setelah jeda
                    // tidak boleh mengirim potongan yang sudah sampai sebelum jeda/gagal.
                    val isLanjutan = inputData.getBoolean(K_LANJUTAN, false)
                    if ((runAttemptCount > 0 || isLanjutan) &&
                        repo.sudahTerindeks(namaPotongan, potonganFile.length(), kunci.chatId)
                    ) {
                        Log.i(tag, "Percobaan $runAttemptCount (lanjutan=$isLanjutan): $namaPotongan sudah terindeks — lewati.")
                        return@suspendPotongan
                    }

                    val header = HeaderArsip.potongan(
                        grupId = grupId,
                        indeks = indeks,
                        jumlah = total,
                        namaAsli = namaAsli,
                        ukuranTotal = ukuran,
                        hashPotongan = HashBerkas.hitungBerkas(potonganFile),
                    )
                    val caption = header.susun()

                    val terkirim = telegram.kirimBerkas(
                        chatId = koleksi.chatId,
                        path = potonganFile.absolutePath,
                        tipe = tipe,
                        caption = caption,
                        topicId = koleksi.topicId,
                    )

                    // Pemantauan progres tidak boleh menggagalkan konfirmasi server
                    runCatching {
                        pantauProgresUnggah(telegram, terkirim, namaPotongan, idNotif)
                    }

                    val dikonfirmasi = try {
                        telegram.tungguTerkirim(terkirim.id)
                    } catch (e: Exception) {
                        // Cek apakah server sebenarnya sudah mengonfirmasi pengiriman pesan ini
                        val cekPesan = runCatching {
                            telegram.ambilPesan(koleksi.chatId, terkirim.id)
                        }.getOrNull()
                        if (cekPesan != null && cekPesan.sendingState == null) {
                            com.arsipsenja.data.telegram.PesanTerkirim(cekPesan, terkirim.id)
                        } else {
                            throw e
                        }
                    }

                    // Server sudah mengonfirmasi pesan terkirim.
                    var mentah = dikonfirmasi.pesan.keBerkasMentah()
                    if (mentah != null && mentah.ukuran == 0L && mentah.tdFileId > 0) {
                        val ukuranSegar = runCatching {
                            telegram.ambilFile(mentah.tdFileId).ukuranTotal()
                        }.getOrNull() ?: 0L
                        if (ukuranSegar > 0L) {
                            mentah = mentah.copy(ukuran = ukuranSegar)
                        }
                    }

                    nomorKatalog += 1
                    val sekarang = System.currentTimeMillis()
                    val entri = if (mentah != null) {
                        mentah
                            .keEntity(
                                koleksiId = koleksi.chatId,
                                topicId = koleksi.topicId,
                                nomorKatalog = nomorKatalog,
                                sekarang = sekarang,
                            )
                            .copy(tdLocalPath = null)
                    } else {
                        // Catat jejak idempoten (nama + ukuran + chatId + waktu)
                        com.arsipsenja.data.local.BerkasArsipEntity(
                            id = com.arsipsenja.data.local.BerkasArsipEntity.kunci(
                                koleksi.chatId,
                                dikonfirmasi.pesan.id,
                            ),
                            messageId = dikonfirmasi.pesan.id,
                            chatId = koleksi.chatId,
                            koleksiId = koleksi.chatId,
                            topicId = koleksi.topicId,
                            nama = namaPotongan,
                            ukuran = potonganFile.length(),
                            tanggal = sekarang,
                            mime = "",
                            tipe = tipe.name,
                            nomorKatalog = nomorKatalog,
                            tdFileId = 0,
                            tdLocalPath = null,
                            tdThumbFileId = 0,
                            thumbPath = null,
                            deskripsi = null,
                            dibuatPada = sekarang,
                            hashSha256 = null,
                            grupId = grupId,
                            indeksPotongan = indeks,
                            jumlahPotongan = total,
                            namaAsli = namaAsli,
                            ukuranTotal = ukuran,
                        )
                    }
                    repo.simpanHasilUnggah(entri)

                    Notifikasi.tampilkanUnggah(
                        applicationContext, namaPotongan, 100, total == 1, idNotif,
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Gagal unggah potongan $namaPotongan", e)
                    pesanGagalTerakhir = PenjagaVault.terjemahkanGalatForum(
                        e.message ?: "Unggah gagal tanpa keterangan.",
                    )
                    gagal = true
                }
            }

            pecahBerturut(berkasCache) { potongan, indeks, total ->
                kirimPotongan(potongan, indeks, total)
            }
        } catch (e: SpaceHabisException) {
            return gagalDenganPesan(e.message ?: "Ruang penyimpanan tidak cukup")
        } finally {
            berkasCache.delete()
        }

        // Gagal per-potongan: beri kesempatan retry terbatas untuk galat
        // transien (jaringan), tapi pada percobaan terakhir kembalikan
        // FAILED dengan pesan — jangan gagal diam-diam tanpa alasan.
        if (!gagal) {
            Notifikasi.batalkan(applicationContext, idNotif)
            return Result.success()
        }
        if (percobaanKe < MAKS_PERCOBAAN_GAGAL) return Result.retry()
        Notifikasi.batalkan(applicationContext, idNotif)
        return gagalDenganPesan(pesanGagalTerakhir ?: "Unggah gagal — coba lagi.")
    }

    /**
     * Id kelompok yang stabil untuk isi yang sama.
     *
     * Berkas dengan hash sama diunggah ke dua koleksi berbeda akan memakai
     * kelompok yang sama, sehingga penyatuan dan deteksi duplikat tetap bekerja.
     */

    /** Kegagalan terminal yang membawa pesan galat untuk ditampilkan UI. */
    private fun gagalDenganPesan(pesan: String): Result =
        Result.failure(workDataOf(K_PESAN_GALAT to pesan))

    /** Pantau progres unggah berdasarkan fileId pada pesan terkirim. */
    private suspend fun pantauProgresUnggah(
        telegram: com.arsipsenja.data.telegram.TelegramClient,
        pesan: TdApi.Message,
        nama: String,
        idNotif: Int,
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
                        Notifikasi.tampilkanUnggah(applicationContext, nama, persen, false, idNotif)
                    }
                }
        }
        // Jangan mengarang angka 99% bila progres sebenarnya belum sampai.
        // Versi lama menulis 99% apa pun yang terjadi, sehingga UI menampilkan
        // barra hampir penuh untuk potongan yang mungkin masih 10%.
        if (terakhir == 99) {
            Notifikasi.tampilkanUnggah(applicationContext, nama, 99, false, idNotif)
        }
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
        } catch (e: SecurityException) {
            // Izin akses berkas dicabut. Kegagalan ini **tidak** bisa
            // diperbaiki dengan mencoba lagi: pengguna harus memilih ulang
            // berkasnya. Karena itu pesannya harus menyebut hal itu.
            Log.e(tag, "Izin akses berkas dicabut untuk $nama", e)
            null
        } catch (e: Exception) {
            Log.e(tag, "Salin berkas $nama gagal", e)
            null
        }
    }

    /**
     * Ruang bebas di `cacheDir`.
     *
     * Cache berada di penyimpanan internal, sehingga tidak bisa dipindah ke
     * kartu SD seperti MediaStore. Untuk berkas besar inilah alasan pemecahan
     * harus satu per satu: seluruhnya sekaligus bisa memakan belasan gigabyte.
     */
    private fun ruangCacheBebas(): Long =
        StatFs(applicationContext.cacheDir.absolutePath).availableBytes

    /** Throws [SpaceHabisException] bila ruang tidak cukup. */
    private fun ensureCukupRuang(perlu: Long) {
        if (perlu <= 0) return
        // 20% cadangan untuk metadata filesystem.
        val aman = (perlu * 1.2).toLong()
        if (ruangCacheBebas() < aman) {
            throw SpaceHabisException("Ruang penyimpanan tidak cukup")
        }
    }

    /** Melempar galat bila ruang kosong tidak mencukupi. */
    class SpaceHabisException(pesan: String) : Exception(pesan)

    /** Ukuran berkas dari metadata SAF, atau 0 bila tidak diketahui. */
    private fun ukuranDari(uri: Uri): Long = try {
        applicationContext.contentResolver.query(uri, null, null, null, null)?.use { kursor ->
            val indeks = kursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (indeks >= 0 && kursor.moveToFirst() && !kursor.isNull(indeks)) {
                kursor.getLong(indeks)
            } else {
                0L
            }
        } ?: 0L
    } catch (e: Exception) {
        0L
    }

    /**
     * Pecah berkas besar **satu per satu**.
     *
     * Versi lama memakai [com.arsipsenja.util.pecahBerkas] yang menulis seluruh
     * potongan lebih dulu. Untuk berkas 6 GB itu berarti ~12 GB peak di cache
     * (satu salinan asli ditambah seluruh potongan) sebelum sumber dihapus —
     * dan tanpa ada pemeriksaan ruang, hasilnya `ENOSPC` di tengah jalan.
     * Versi ini menyimpan paling banyak satu potongan pada satu waktu.
     *
     * Setiap potongan langsung diberi header caption berisi kelompok dan
     * indeksnya, sehingga dapat disatukan kembali meski perangkat di-reset.
     */
    private fun pecahBilaBerlu(sumber: File): List<File> {
        if (!com.arsipsenja.util.perluPecah(sumber.length())) return listOf(sumber)
        val dir = File(applicationContext.cacheDir, "unggahan").apply { mkdirs() }
        val hasil = com.arsipsenja.util.pecahBerkas(sumber, dir)
        sumber.delete()
        return hasil
    }

    /**
     * Pecah satu per satu: satu potongan dibuat, dikirim, lalu dihapus
     * sebelum potongan berikutnya dibuat.
     *
     * Hanya satu potongan yang ada di cache pada setiap saat, sehingga puncak
     * penggunaan ruang untuk berkas 6 GB turun dari ~12 GB menjadi ~2 GB.
     */
    private suspend fun pecahBerturut(
        sumber: File,
        kirim: suspend (File, Int, Int) -> Unit,
    ) {
        com.arsipsenja.util.pecahBerturutan(
            sumber = sumber,
            direktori = File(applicationContext.cacheDir, "unggahan").apply { mkdirs() },
            hapusSetelah = true,
            onPotongan = { berkas, indeks, total -> kirim(berkas, indeks, total) },
        )
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

        /** UUID pekerjaan unggah; kunci baris status jeda di Room. */
        const val K_WORK_ID = "workId"

        /** Kunci outputData untuk pesan galat terminal (dibaca UI antrean). */
        const val K_PESAN_GALAT = "pesanGalat"

        /** Id topik forum tujuan; 0 = channel lama / tanpa topik. */
        const val K_TOPIK = "topicId"

        /** Nama tampil berkas (DISPLAY_NAME SAF); dibaca UI antrean. */
        const val K_NAMA = "namaBerkas"

        /**
         * Penanda bahwa worker ini dijalankan ulang dari "lanjutkan" (bukan percobaan
         * ulang otomatis WorkManager). Dipakai oleh guard idempoten agar potongan yang
         * sudah terkirim sebelum jeda tidak dikirim dua kali.
         */
        const val K_LANJUTAN = "lanjutan"

        /** Batas retry untuk gagal per-potongan (runAttemptCount 0..1 → retry, ke-2 → FAILED berpesan). */
        private const val MAKS_PERCOBAAN_GAGAL = 2

        private const val BATAS_PROGRES = 120_000L

        /**
         * Constraint jaringan untuk pekerjaan unggah.
         * - hanyaWifi = false → jalan di semua koneksi (seluler pun boleh).
         * - hanyaWifi = true  → hanya jaringan tak-bermeter (Wi-Fi), transfer
         *   ditahan WorkManager selama perangkat di jaringan seluler.
         */
        fun batasJaringan(hanyaWifi: Boolean): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (hanyaWifi) NetworkType.UNMETERED else NetworkType.CONNECTED,
                )
                .build()
    }
}
