package com.arsipsenja.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Berkas terindeks di arsip. Sumber kebenaran **isi berkasnya** ada di
 * Telegram; tabel ini adalah indeks lokal untuk pencarian, statistik, dan
 * pratinjau.
 *
 * ## Kolom yang ditambahkan di v6
 *
 * - [hashSha256], [grupId], [indeksPotongan], [jumlahPotongan], [namaAsli],
 *   [ukuranTotal] — cermin header caption. Disalin ke sini agar pencarian dan
 *   penyatuan potongan bisa jalan tanpa membaca caption tiap kali.
 * - [statusSinkron] — penanda hasil rekonsiliasi. Baris yang pesan Telegram-nya
 *   sudah hilang ditandai, bukan dibiarkan menyesatkan.
 * - [sudahDiverifikasi] — apakah [hashSha256] sudah dicocokkan dengan isi
 *   berkas yang benar-benar diterima.
 *
 * Semua kolom baru punya nilai bawaan, sehingga basis data lama yang belum
 * dimigrasi (dan setiap tempat yang membuat entity secara manual) tetap
 * terkompilasi dan tidak kehilangan data.
 */
@Entity(
    tableName = "berkas_arsip",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["koleksiId"]),
        Index(value = ["koleksiId", "topicId"]),
        Index(value = ["koleksiId", "topicId", "tanggal"]),
        Index(value = ["nama"]),
        Index(value = ["tanggal"]),
        // Pencarian & penyaringan tipe menzalimi seluruh tabel tanpa ini.
        Index(value = ["tipe"]),
        Index(value = ["ukuran"]),
        // Pencarian duplikat dan verifikasi integritas.
        Index(value = ["hashSha256"]),
        // Penyatuan potongan berkas besar.
        Index(value = ["grupId"]),
        // Rekonsiliasi: cari yang belum pernah diperiksa pada sesi sebelumnya.
        Index(value = ["statusSinkron"]),
    ],
)
data class BerkasArsipEntity(
    /** Kunci stabil: "chatId:messageId". */
    @PrimaryKey val id: String,
    val messageId: Long,
    val chatId: Long,
    /** Channel/koleksi tempat berkas berada; null bila yatim. */
    val koleksiId: Long?,
    /** Id topik forum; 0 untuk channel lama / tanpa topik. */
    val topicId: Int = 0,
    val nama: String,
    val ukuran: Long,
    /** Epoch millis. */
    val tanggal: Long,
    val mime: String,
    /** Nilai [com.arsipsenja.util.TipeFile]. */
    val tipe: String,
    @ColumnInfo(defaultValue = "0") val nomorKatalog: Int = 0,
    /** Identitas file di TDLib untuk unduh. */
    val tdFileId: Int,
    /** Jalur berkas lokal bila sudah tersedia di perangkat. */
    val tdLocalPath: String?,
    /** Identitas file thumbnail di TDLib; 0 bila pesan tak punya thumbnail. */
    @ColumnInfo(defaultValue = "0") val tdThumbFileId: Int = 0,
    /** Jalur thumbnail lokal (filesDir/thumbs) bila sudah diunduh. */
    val thumbPath: String? = null,
    val deskripsi: String? = null,
    val dibuatPada: Long = 0L,

    // ---- v6: integritas, kelompok, dan rekonsiliasi -----------------------

    /** SHA-256 isi berkas (hex); null bila belum pernah dihitung. */
    @ColumnInfo(defaultValue = "NULL") val hashSha256: String? = null,
    /** UUID kelompok; sama untuk semua potongan dari satu berkas asli. */
    @ColumnInfo(defaultValue = "NULL") val grupId: String? = null,
    /** Indeks potongan mulai 1; 0 bila berkas tidak dipecah. */
    @ColumnInfo(defaultValue = "0") val indeksPotongan: Int = 0,
    /** Jumlah potongan berkas asli; 0 bila berkas tidak dipecah. */
    @ColumnInfo(defaultValue = "0") val jumlahPotongan: Int = 0,
    /** Nama berkas asli sebelum dipecah; null bila sama dengan [nama]. */
    @ColumnInfo(defaultValue = "NULL") val namaAsli: String? = null,
    /** Ukuran berkas asli dalam byte; 0 bila sama dengan [ukuran]. */
    @ColumnInfo(defaultValue = "0") val ukuranTotal: Long = 0L,
    /** [STATUS_AKTIF] atau [STATUS_HILANG]; lihat companion. */
    @ColumnInfo(defaultValue = "0") val statusSinkron: Int = STATUS_AKTIF,
    /** 1 bila [hashSha256] sudah dicocokkan dengan berkas yang diterima. */
    @ColumnInfo(defaultValue = "0") val sudahDiverifikasi: Int = 0,
) {
    /** Berkas ini bagian dari berkas yang dipecah? */
    val terpecah: Boolean get() = jumlahPotongan > 1

    /** Nama berkas asli: [namaAsli] bila ada, jika tidak [nama]. */
    val namaTampil: String get() = namaAsli?.takeIf { it.isNotBlank() } ?: nama

    /** Ukuran berkas asli: [ukuranTotal] bila ada, jika tidak [ukuran]. */
    val ukuranAsli: Long get() = if (ukuranTotal > 0) ukuranTotal else ukuran

    /** Sudah terverifikasi integritasnya? */
    val terverifikasi: Boolean get() = sudahDiverifikasi == 1

    companion object {
        fun kunci(chatId: Long, messageId: Long) = "$chatId:$messageId"

        /** Baris sudahsinkron dengan Telegram. */
        const val STATUS_AKTIF = 0

        /**
         * Pesan Telegram-nya tidak ditemukan saat rekonsiliasi.
         * Baris **tidak dihapus** agar berkas lokal yang sudah diunduh tetap
         * ditemukan dan pengguna bisa memutuskan.
         */
        const val STATUS_HILANG = 1
    }
}