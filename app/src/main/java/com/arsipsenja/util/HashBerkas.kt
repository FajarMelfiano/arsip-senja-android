package com.arsipsenja.util

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Perhitungan SHA-256 untuk berkas dan aliran data.
 *
 * Hash inilah yang membuat arsip bisa **content-addressed**: nama berkas pada
 * Telegram dapat dibuat sama dengan hash-nya, sehingga nama berkas itu sendiri
 * menjadi bukti isi tanpa perlu mengunduh untuk memverifikasinya.
 *
 * Fungsi di sini murni JVM (tanpa Android) agar bisa diuji sebagai unit test.
 */
object HashBerkas {

    /** Ukuran buffer streaming; cukup untuk semua Android. */
    private const val BUFFER = 1 shl 20

    /**
     * Hitung SHA-256 berkas dan kembalikan dalam hex huruf kecil.
     *
     * @param masihJalan bernilai false untuk membatalkan iterasi lebih awal
     *   (dipakai worker agar unggahan bisa dihentikan tanpa menunggu selesai).
     * @return hash hex, atau null bila dibatalkan lebih awal.
     * @throws java.io.IOException bila gagal membaca.
     */
    fun hitungBerkas(berkas: File, masihJalan: () -> Boolean = { true }): String? {
        require(berkas.isFile) { "Bukan berkas: $berkas" }
        berkas.inputStream().buffered(BUFFER).use { aliran ->
            return hitungAliran(aliran, masihJalan)
        }
    }

    /**
     * Hitung SHA-256 dari aliran data mentah.
     *
     * [buffer] dapat dikecilkan agar jalur pembatalan bisa diuji tanpa
     * menyiapkan input sebesar buffer produksi.
     *
     * @return hash hex, atau null bila [masihJalan] bernilai false.
     */
    @JvmOverloads
    fun hitungAliran(
        aliran: InputStream,
        masihJalan: () -> Boolean = { true },
        buffer: Int = BUFFER,
    ): String? {
        require(buffer > 0) { "buffer harus positif" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(buffer)
        while (true) {
            if (!masihJalan()) return null
            val dibaca = aliran.read(buf)
            if (dibaca < 0) break
            if (dibaca > 0) digest.update(buf, 0, dibaca)
        }
        return keHex(digest.digest())
    }

    /**
     * Hitung SHA-256 dari isi string — untuk pengujian cepat dan berkas kecil.
     */
    fun hitungTeks(teks: String): String =
        keHex(MessageDigest.getInstance("SHA-256").digest(teks.toByteArray(Charsets.UTF_8)))

    /** Ubah byte[] menjadi hex huruf kecil. */
    fun keHex(bita: ByteArray): String {
        val sb = StringBuilder(bita.size * 2)
        for (b in bita) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /** Apakah string ini berbentuk SHA-256 hex yang sah? */
    fun adalahHashSah(bak: String?): Boolean {
        if (bak == null || bak.length != HeaderArsip.PANJANG_HASH) return false
        return bak.all { it in '0'..'9' || it in 'a'..'f' }
    }

    private val HEX = "0123456789abcdef".toCharArray()
}