package com.arsipsenja.util

import java.io.File
import java.io.RandomAccessFile

/**
 * Pemecah berkas besar untuk unggah Telegram.
 *
 * Telegram membatasi ukuran satu berkas per pesan di sekitar 2 GiB, maka
 * berkas lebih besar dipecah menjadi potongan `nama.partN.ekstensi` yang
 * dikirim sebagai pesan terpisah. Logika murni (tanpa Android) agar bisa
 * diuji sebagai unit test JVM.
 */

/** Batas aman satu potongan: 1,9 miliar byte (di bawah ~2 GiB). */
const val BATAS_POTONGAN = 1_900_000_000L

/** Ukuran baca/tulis internal pemecahan. */
const val BUFFER_PEMECAH = 1 shl 20

/** Apakah ukuran ini perlu dipecah? */
fun perluPecah(ukuran: Long): Boolean = ukuran > BATAS_POTONGAN

/**
 * Nama potongan ke-[nomor]:
 * `lapor an.txt` → `lapor an.part1.txt`, `foto` → `foto.part1`.
 */
fun namaPotongan(nama: String, nomor: Int): String {
    val titik = nama.lastIndexOf('.')
    return if (titik <= 0) {
        "$nama.part$nomor"
    } else {
        "${nama.substring(0, titik)}.part$nomor${nama.substring(titik)}"
    }
}

/**
 * Pecah [sumber] menjadi potongan berukuran maksimal [batas] di dalam
 * [direktori]. Berkas kecil (< [batas]) dikembalikan apa adanya ( satu
 * elemen: berkas sumber ). Sumber tidak dihapus oleh fungsi ini.
 *
 * @throws java.io.IOException bila direktori tak bisa ditulis.
 */
fun pecahBerkas(
    sumber: File,
    direktori: File,
    batas: Long = BATAS_POTONGAN,
    buffer: Int = BUFFER_PEMECAH,
): List<File> {
    require(batas > 0) { "batas harus positif" }
    require(buffer > 0) { "buffer harus positif" }
    if (sumber.length() <= batas) return listOf(sumber)
    require(direktori.isDirectory || direktori.mkdirs()) {
        "Direktori tujuan tidak bisa dibuat: $direktori"
    }

    val hasil = mutableListOf<File>()
    RandomAccessFile(sumber, "r").use { src ->
        var potonganKe = 1
        while (src.filePointer < src.length()) {
            val ukuranPotongan = minOf(batas, src.length() - src.filePointer)
            val target = File(
                direktori,
                "${System.currentTimeMillis()}_${namaPotongan(sumber.name, potonganKe)}",
            )
            RandomAccessFile(target, "rw").use { dst ->
                dst.setLength(ukuranPotongan)
                val buf = ByteArray(buffer)
                var sisa = ukuranPotongan
                while (sisa > 0) {
                    val baca = minOf(buf.size.toLong(), sisa).toInt()
                    src.readFully(buf, 0, baca)
                    dst.write(buf, 0, baca)
                    sisa -= baca
                }
            }
            hasil += target
            potonganKe += 1
        }
    }
    return hasil
}
