package com.arsipsenja.util

import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Penyatuan potongan berkas besar menjadi satu berkas utuh.
 *
 * ## Kenapa modul ini perlu
 *
 * Unggahan berkas di atas [BATAS_POTONGAN] dipecah menjadi beberapa pesan
 * Telegram. Aplikasi sebelumnya tidak pernah dirancang untuk menyatukannya
 * kembali, sehingga pengguna menerima beberapa berkas terpisah
 * untuk satu berkas asli. Modul ini menutup celah tersebut.
 *
 * Potongan diurutkan **secara numerik**, bukan leksikografis, agar `part10`
 * tidak diletakkan sebelum `part2`.
 */
object GabungPotongan {

    /** Melempar galat ketika ada nomor potongan yang hilang di tengah rangkaian. */
    class PotonganHilangException(val indeksHilang: List<Int>) :
        IOException("Potongan hilang: $indeksHilang")

    /** Melempar galat ketika ukuran hasil tidak sesuai perkiraan. */
    class UkuranTidakCocokException(val diharapkan: Long, val sebenarnya: Long) :
        IOException("Ukuran hasil gabung $sebenarnya, seharusnya $diharapkan")

    /**
     * Gabung seluruh [sumber] (nama potongan ke berkas) ke satu [tujuan].
     *
     * @param sumber peta nama berkas potongan ke berkas nyatanya.
     * @param tujuan berkas hasil; ditimpa bila sudah ada.
     * @param ukuranHarapan bila diisi, jumlah byte hasil harus cocok. Inilah
     *   verifikasi bahwa tidak ada potongan yang terpotong diam-diam.
     * @return jumlah byte yang ditulis.
     */
    @JvmStatic
    fun gabungKeBerkas(
        sumber: Map<String, File>,
        tujuan: File,
        ukuranHarapan: Long? = null,
    ): Long {
        tujuan.parentFile?.let { induk ->
            if (!induk.isDirectory) induk.mkdirs()
        }
        return tujuan.outputStream().buffered(BUFFER_PEMECAH).use { keluaran ->
            val tertulis = gabungKeAliran(sumber, keluaran, ukuranHarapan)
            keluaran.flush()
            tertulis
        }
    }

    /**
     * Gabung seluruh [sumber] langsung ke [keluaran].
     *
     * Versi tanpa berkas antara — dipakai saat menulis ke MediaStore agar berkas
     * besar tidak perlu diletakkan dua kali di penyimpanan.
     *
     * @return jumlah byte yang ditulis.
     * @throws PotonganHilangException bila ada nomor potongan yang hilang.
     * @throws UkuranTidakCocokException bila total tidak sesuai [ukuranHarapan].
     */
    @JvmStatic
    fun gabungKeAliran(
        sumber: Map<String, File>,
        keluaran: OutputStream,
        ukuranHarapan: Long? = null,
    ): Long {
        val terurut = urutkanNamaPotongan(sumber.keys)
        periksaKelengkapan(terurut)

        var total = 0L
        val buffer = ByteArray(BUFFER_PEMECAH)
        for (nama in terurut) {
            val berkas = sumber.getValue(nama)
            if (!berkas.isFile) throw IOException("Potongan tidak ada: $nama")
            berkas.inputStream().use { masuk ->
                while (true) {
                    val dibaca = masuk.read(buffer)
                    if (dibaca < 0) break
                    keluaran.write(buffer, 0, dibaca)
                    total += dibaca
                }
            }
        }
        if (ukuranHarapan != null && total != ukuranHarapan) {
            throw UkuranTidakCocokException(ukuranHarapan, total)
        }
        return total
    }

    /**
     * Pastikan nomor potongan berjalan tanpa celah dari 1 sampai jumlah terakhir.
     *
     * Potongan yang hilang berarti satu pesan gagal terunggah. Kondisi ini
     * **tidak boleh** diam-diam menghasilkan berkas korup.
     */
    @JvmStatic
    fun periksaKelengkapan(namaPotongan: List<String>) {
        val indeksTersedia = namaPotongan.mapNotNull { bacaInfoPotongan(it)?.indeks }.toSet()
        if (indeksTersedia.isEmpty()) return
        val jumlahTertinggi = indeksTersedia.max()
        val hilang = (1..jumlahTertinggi).filterNot { indeksTersedia.contains(it) }
        if (hilang.isNotEmpty()) throw PotonganHilangException(hilang)
    }
}