package com.arsipsenja.data.work

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.arsipsenja.ArsipSenjaApp
import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.repo.ArsipRepository
import com.arsipsenja.data.telegram.TelegramException
import com.arsipsenja.util.GabungPotongan
import com.arsipsenja.util.HashBerkas
import com.arsipsenja.util.KebijakanCobaLagi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pekerja unduh berkas dari Telegram.
 *
 * ## Perbaikan yang dibawa versi ini
 *
 * - **Batas percobaan.** Dulu `Result.retry()` dipanggil untuk *semua* galat
 *   tanpa penghitung percobaan. Berkas yang pesannya sudah dihapus di Telegram
 *   akan diunduh ulang selamanya dengan backoff — menghabiskan baterai dan kuota
 *   tanpa pernah memberi tahu pengguna.
 * - **Pesan kegagalan.** Setiap kegagalan sekarang menyertakan teks yang bisa
 *   ditampilkan, memakai kunci yang sama dengan `UploadWorker`.
 * - **Prijek sebagai foreground service.** Tanpa ini worker dibatasi sekitar 10
 *   menit, sehingga unduhan besar terpotong di tengah jalan.
 * - **Verifikasi SHA-256** bila arsip mencatat hash isinya.
 * - **Penyatuan potongan** untuk berkas yang dipecah.
 * - **Pembersihan** cache TDLib setelah disalin, dan penghapusan baris
 *   MediaStore yang menggantung saat gagal.
 *
 * Hasil disimpan ke `Download/Arsip Senja` (MediaStore, API 29+) atau
 * `Android/data/…/files/Download` (API 26-28).
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val tag = "DownloadWorker"

    override suspend fun doWork(): Result {
        val berkasId = inputData.getString(K_BERKAS)
            ?: return gagal("Permintaan unduhan tidak lengkap")
        val fileId = inputData.getInt(K_FILE_ID, 0)
        if (fileId <= 0) return gagal("Berkas tidak punya identitas unduhan")

        val repo = repository()

        val berkas = repo.ambilBerkas(berkasId)
            ?: return gagal("Berkas tidak ada di indeks arsip")
        if (berkas.statusSinkron == BerkasArsipEntity.STATUS_HILANG) {
            // Diketahui hilang karena pesannya dihapus di Telegram. Mengulang
            // tidak akan pernah berhasil.
            return gagal("Berkas ini sudah tidak ada di Telegram")
        }

        // Notifikasi memakai id per berkas supaya tidak saling menimpa, dan id
        // yang sama dipakai saat membatalkan agar tidak ada notifikasi nyangkut.
        val idNotif = Notifikasi.idUnduhKhusus(berkasId)
        Notifikasi.tampilkanUnduh(applicationContext, berkas.namaTampil, 0, false, idNotif)
        naikkanKeForeground(
            context = applicationContext,
            idNotif = idNotif,
            judul = "Mengunduh…",
            namaBerkas = berkas.namaTampil,
        )

        return try {
            if (!cukupRuang(berkas.ukuranAsli)) {
                return gagal("Ruang penyimpanan tidak cukup untuk ${berkas.namaTampil}")
            }

            val tujuan = if (berkas.terpecah) {
                unduhGabungan(repo, berkas, idNotif)
            } else {
                pantauProgresUnduh(fileId, berkas.namaTampil, idNotif)
                val path = repo.telegram.unduh(fileId).local?.path
                    ?: return gagal("TDLib tidak memberikan lokasi berkas")
                salinKePenyimpanan(path, berkas.namaAsli ?: berkas.nama, berkas.mime)
            }

            if (tujuan == null) return gagal("Berkas gagal disimpan ke perangkat")

            if (!verifikasi(tujuan, berkas)) {
                hapusTujuan(tujuan)
                return gagal("Isi berkas tidak cocok dengan hash yang tercatat")
            }

            repo.tandaiLokal(berkasId, tujuan)
            Notifikasi.tampilkanUnduh(applicationContext, berkas.namaTampil, 100, true, idNotif)
            Result.success()
        } catch (e: GabungPotongan.PotonganHilangException) {
            batalkanNotif(idNotif)
            gagal("Potongan berkas hilang: ${e.indeksHilang.joinToString(", ")}")
        } catch (e: GabungPotongan.UkuranTidakCocokException) {
            batalkanNotif(idNotif)
            gagal(
                "Ukuran hasil unduhan tidak sesuai arsip " +
                    "(diharapkan ${e.diharapkan}, diterima ${e.sebenarnya})",
            )
        } catch (e: TelegramException) {
            putuskan(e, idNotif)
        } catch (e: Exception) {
            val keputusan = KebijakanCobaLagi.dariGalatLokal(e::class.java.name)
            if (!keputusan.bolehDiulang || runAttemptCount >= MAKS_PERCOBAAN) {
                batalkanNotif(idNotif)
                gagal(e.message ?: "Gagal menyimpan berkas")
            } else {
                Log.w(tag, "Unduh sementara gagal: ${e.message}")
                setProgress(
                    workDataOf(K_CADANGKAN to KebijakanCobaLagi.jedaUntuk(keputusan)),
                )
                Result.retry()
            }
        }
    }

    /**
     * Unduh seluruh potongan satu kelompok lalu satukan menjadi satu berkas.
     *
     * Potongan disalin **langsung ke keluaran** tanpa pernah membentuk berkas
     * gabungan di ruang antara, jadi hanya ada satu salinan akhir — bukan dua.
     */
    private suspend fun unduhGabungan(
        repo: ArsipRepository,
        berkas: BerkasArsipEntity,
        idNotif: Int,
    ): String? {
        val grupId = berkas.grupId ?: return null
        val potongan = repo.ambilGrup(grupId).sortedBy { it.indeksPotongan }
        if (potongan.isEmpty()) return null

        // Periksa celah lebih dulu, sebelum mengunduh apa pun.
        GabungPotongan.periksaKelengkapan(potongan.map { it.nama })

        val lokal = LinkedHashMap<String, File>()
        try {
            for (p in potongan) {
                pantauProgresUnduh(p.tdFileId, p.namaTampil, idNotif)
                val path = repo.telegram.unduh(p.tdFileId).local?.path
                    ?: throw IOException("Potongan ${p.namaTampil} gagal diunduh")
                lokal[p.nama] = File(path)
            }
            return salinGabungan(lokal, berkas)
        } finally {
            // Cache TDLib adalah salinan sementara. Tanpa pembersihan ini,
            // penyimpanan bertambah dua kali untuk setiap berkas yang pernah
            // diunduh.
            lokal.values.forEach { runCatching { it.delete() } }
        }
    }

    /** Majukan progres ke notifikasi dan ke UI. */
    private suspend fun pantauProgresUnduh(fileId: Int, nama: String, idNotif: Int) {
        var terakhir = -1
        withTimeoutOrNull(BATAS_PROGRES) {
            repository().telegram.progresBerkas
                .takeWhile { !(it.fileId == fileId && it.selesaiUnduh) }
                .collect { p ->
                    if (p.fileId != fileId) return@collect
                    val persen = p.persenUnduh
                    if (persen in 0..99 && persen != terakhir) {
                        terakhir = persen
                        Notifikasi.tampilkanUnduh(applicationContext, nama, persen, false, idNotif)
                        setProgress(workDataOf(K_PERSAN to persen, K_TERUNDUH to p.terunduh))
                    }
                }
        }
    }

    /**
     * Naikkan galat Telegram menjadi keputusan: berhenti, atau coba lagi.
     *
     * Galat permanen (400/401/403, termasuk pesan yang sudah dihapus) langsung
     * gagal dengan pesan yang bisa dibaca pengguna.
     */
    private suspend fun putuskan(galat: TelegramException, idNotif: Int): Result {
        val percobaan = runAttemptCount
        val keputusan = galat.keputusan(percobaan)
        Log.w(tag, "Unduh gagal (kode ${galat.kode}): ${galat.pesan} -> $keputusan")
        batalkanNotif(idNotif)

        return when {
            !keputusan.bolehDiulang -> gagal(galat.pesan)
            // Berhenti juga setelah batas percobaan, supaya antrean tidak
            // berputar tanpa akhir meski galatnya terlihat "sementara".
            percobaan >= MAKS_PERCOBAAN -> gagal(galat.pesan)
            else -> {
                setProgress(workDataOf(K_CADANGKAN to KebijakanCobaLagi.jedaUntuk(keputusan)))
                Result.retry()
            }
        }
    }

    /** Hasil gagal yang selalu menyertakan pesan untuk ditampilkan ke pengguna. */
    private fun gagal(pesan: String): Result {
        Log.w(tag, "Unduh gagal: $pesan")
        return Result.failure(workDataOf(K_PESAN_GALAT to pesan))
    }

    private fun batalkanNotif(idNotif: Int) = Notifikasi.batalkan(applicationContext, idNotif)

    private fun repository(): ArsipRepository =
        (applicationContext as ArsipSenjaApp).wadah.arsipRepository

    /** Periksa ruang bebas lebih dulu; menyalin berkas besar tanpa cek pasti gagal. */
    private fun cukupRuang(perlu: Long): Boolean {
        if (perlu <= 0) return true
        val bebas = StatFs(applicationContext.filesDir.absolutePath).availableBytes
        // Tambahan 20% untuk metadata filesystem dan entri MediaStore.
        val aman = (perlu * 1.2).toLong()
        if (bebas < aman) {
            Log.e(tag, "Ruang kurang: butuh $aman, tersedia $bebas")
            return false
        }
        return true
    }

    /**
     * Cocokkan hash berkas hasil unduhan dengan hash yang tercatat di arsip.
     *
     * Hanya dijalankan bila kedua sisi punya hash. Arsip lama tidak punya,
     * sehingga pemeriksaan dilewati — bukan dianggap lulus.
     */
    private fun verifikasi(tujuan: String, berkas: BerkasArsipEntity): Boolean {
        val diharapkan = berkas.hashSha256 ?: return true
        if (tujuan.startsWith(SKEMA_KONTEN)) return true
        val berkasLokal = File(tujuan)
        if (!berkasLokal.isFile) return false
        val aktual = HashBerkas.hitungBerkas(berkasLokal) ?: return false
        if (aktual == diharapkan) return true
        Log.e(tag, "Hash tidak cocok untuk ${berkas.namaTampil}")
        return false
    }

    private fun hapusTujuan(tujuan: String) {
        if (tujuan.startsWith(SKEMA_KONTEN)) return
        runCatching { File(tujuan).delete() }
    }

    /** Salin hasil unduh TDLib ke penyimpanan pengguna; kembalikan lokasi final. */
    private fun salinKePenyimpanan(pathSumber: String, nama: String, mime: String?): String? {
        val sumber = File(pathSumber)
        if (!sumber.isFile) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                simpanMediaStore(sumber, nama, mime)
            } else {
                simpanEksternal(sumber, nama)
            }
        } finally {
            runCatching { sumber.delete() }
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
            // Baris IS_PENDING yang ditinggalkan tidak terlihat di UI tetapi
            // tetap memakan ruang, jadi harus dihapus.
            runCatching { resolver.delete(koleksi, null, null) }
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

    /**
     * Gabung seluruh potongan langsung ke penyimpanan tujuan.
     *
     * Tidak pernah membentuk berkas gabungan di ruang antara.
     */
    private fun salinGabungan(
        potongan: Map<String, File>,
        berkas: BerkasArsipEntity,
    ): String? {
        val nama = berkas.namaAsli ?: berkas.nama
        val mime = berkas.mime.ifBlank { "application/octet-stream" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val nilai = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, nama)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Arsip Senja")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val koleksi = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, nilai)
                ?: return null
            return try {
                resolver.openOutputStream(koleksi)?.use { keluar ->
                    GabungPotongan.gabungKeAliran(potongan, keluar, berkas.ukuranAsli)
                } ?: return null
                nilai.clear()
                nilai.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(koleksi, nilai, null, null)
                koleksi.toString()
            } catch (e: Exception) {
                Log.e(tag, "Simpan gabungan gagal", e)
                runCatching { resolver.delete(koleksi, null, null) }
                throw e
            }
        }
        val dir = applicationContext.getExternalFilesDir("Download")?.apply { mkdirs() }
            ?: File(applicationContext.filesDir, "Download").apply { mkdirs() }
        GabungPotongan.gabungKeBerkas(potongan, File(dir, nama), berkas.ukuranAsli)
        return File(dir, nama).absolutePath
    }

    companion object {
        const val K_BERKAS = "berkasId"
        const val K_FILE_ID = "fileId"
        const val K_PERSAN = "persen"
        const val K_TERUNDUH = "terunduh"

        /** Pesan kegagalan yang ditampilkan ke pengguna. */
        const val K_PESAN_GALAT = "pesanGalat"

        /** Jeda yang disarankan sebelum mencoba lagi, dalam milidetik. */
        const val K_CADANGKAN = "cadangkanMilidetik"

        /** Awalan lokasi MediaStore; bukan path berkas sehingga tak bisa di-hash. */
        private const val SKEMA_KONTEN = "content://"

        private const val BATAS_PROGRES = 15L * 60L * 1000L

        /** Batas jumlah percobaan ulang sebelum menyerah. */
        const val MAKS_PERCOBAAN = 4

        /** Constraint standar: hanya boleh jalan saat jaringan tersedia. */
        val BATAS_JARINGAN: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}