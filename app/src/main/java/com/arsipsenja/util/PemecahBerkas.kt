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

/**
 * Berapa banyak potongan yang dihasilkan untuk berkas sepanjang [ukuran].
 *
 * Dipakai sebelum memulai unggahan agar jumlah potongan bisa ditulis ke header
 * caption (field `i=indeks/jumlah`) tanpa perlu membuat berkas apa pun.
 */
fun jumlahPotongan(ukuran: Long, batas: Long = BATAS_POTONGAN): Int {
    require(batas > 0) { "batas harus positif" }
    require(ukuran >= 0) { "ukuran tidak boleh negatif" }
    if (ukuran <= batas) return 1
    // Pembulatan ke atas agar berkas tepat di batas tidak terpecah.
    return ((ukuran + batas - 1) / batas).toInt()
}

/**
 * Pecah [sumber] **secara berurutan**: satu potongan dibuat, diserahkan ke
 * [onPotongan], lalu dihapus sebelum potongan berikutnya dibuat.
 *
 * Ini melengkapi [pecahBerkas] untuk jalur unggah, karena [pecahBerkas] menulis
 * seluruh potongan lebih dulu — untuk berkas 6 GB berarti ~12 GB peak di
 * `cacheDir` (asli + semua potongan) sebelum sumber dihapus. Versi ini
 * menyimpan hanya satu potongan pada satu waktu.
 *
 * Nomor urut dimulai dari 1. [onPotongan] dipanggil dengan
 * `(berkas, indeks, jumlah)` dan boleh `suspend` — pemanggil biasanya
 * mengunggah setiap-potongan sebelum berikutnya dibuat. Bila [onPotongan]
 * melempar galat, proses berhenti dan potongan yang sedang ada dihapus.
 *
 * @param hapusSetelah bila true, potongan dihapus setelah [onPotongan] selesai.
 * @throws java.io.IOException bila direktori tak bisa ditulis.
 */
@JvmOverloads
suspend fun pecahBerturutan(
    sumber: File,
    direktori: File,
    batas: Long = BATAS_POTONGAN,
    buffer: Int = BUFFER_PEMECAH,
    hapusSetelah: Boolean = false,
    onPotongan: suspend (File, Int, Int) -> Unit,
) {
    require(batas > 0) { "batas harus positif" }
    require(buffer > 0) { "buffer harus positif" }
    if (!sumber.isFile) throw java.io.IOException("Berkas sumber tidak ada: $sumber")

    val panjang = sumber.length()
    val total = jumlahPotongan(panjang, batas)
    if (total <= 1) {
        onPotongan(sumber, 1, 1)
        return
    }
    require(direktori.isDirectory || direktori.mkdirs()) {
        "Direktori tujuan tidak bisa dibuat: $direktori"
    }

    RandomAccessFile(sumber, "r").use { src ->
        val buf = ByteArray(buffer)
        var potonganKe = 1
        while (src.filePointer < panjang) {
            val ukuranPotongan = minOf(batas, panjang - src.filePointer)
            val target = File(direktori, namaPotongan(sumber.name, potonganKe))
            try {
                RandomAccessFile(target, "rw").use { dst ->
                    dst.setLength(ukuranPotongan)
                    var sisa = ukuranPotongan
                    while (sisa > 0) {
                        val baca = minOf(buf.size.toLong(), sisa).toInt()
                        src.readFully(buf, 0, baca)
                        dst.write(buf, 0, baca)
                        sisa -= baca
                    }
                }
                onPotongan(target, potonganKe, total)
            } catch (e: Throwable) {
                target.delete()
                throw e
            }
            if (hapusSetelah) target.delete()
            potonganKe += 1
        }
    }
}

/** Nomor potongan yang tersimpan dalam nama berkas, atau null bila bukan potongan. */
data class InfoPotongan(val indeks: Int, val jumlah: Int?)

/**
 * Pola `nama.part<angka><_opsional jumlah>.ekstensi`, maksimal 9 digit per angka.
 *
 * Kelompok tangkapan: 1 = nama dasar, 2 = indeks, 3 = jumlah opsional,
 * 4 = ekstensi termasuk titik (opsional).
 */
private val POLA_POTONGAN = Regex("""^(.+)\.part(\d{1,9})(\d{0,9})(\.[^.\s]*)?$""")

/**
 * Baca nomor potongan dari nama berkas seperti `lapor.part3.pdf`.
 *
 * Hanya dipakai untuk mengarsipkan ulang berkas lama yang belum memakai header
 * caption. Berkas baru **tidak** bergantung pada nama — grup dan urutan diambil
 * dari header, sehingga nama bebas diubah.
 *
 * Angka dibaca dengan batas 9 digit agar `part10` tidak tertukar dengan
 * `part1` diikuti karakter lain.
 */
fun bacaInfoPotongan(namaBerkas: String): InfoPotongan? {
    val cocok = POLA_POTONGAN.matchEntire(namaBerkas) ?: return null
    val indeks = cocok.groupValues[2].toAngkaBersih() ?: return null
    if (indeks < 1) return null
    val jumlah = cocok.groupValues[3].takeIf { it.isNotEmpty() }?.toAngkaBersih()
    return InfoPotongan(indeks, jumlah)
}

/**
 * Kembalikan nama berkas **asli** dari nama potongan.
 *
 * `lapor.part2.pdf` → `lapor.pdf`. Berguna untuk mengarsipkan ulang berkas lama
 * yang belum punya header caption, sehingga semua potongannya bisa ditampilkan
 * dengan nama yang sama seperti aslinya.
 *
 * @return nama asli, atau null bila [namaBerkas] bukan nama potongan.
 */
fun namaAsliDariPotongan(namaBerkas: String): String? {
    val cocok = POLA_POTONGAN.matchEntire(namaBerkas) ?: return null
    val indeks = cocok.groupValues[2].toAngkaBersih() ?: return null
    if (indeks < 1) return null
    return cocok.groupValues[1] + cocok.groupValues[4]
}

/** Uraikan angka tanpa kepekaan nol di depan; string kosong menjadi null. */
private fun String.toAngkaBersih(): Int? {
    if (isEmpty()) return null
    val bersih = trimStart('0').ifEmpty { "0" }
    return bersih.toIntOrNull()
}


/**
 * Urutkan nama berkas potongan secara **numerik** menurut nomor bagian.
 *
 * Pengurutan leksikografis keliru: `part10` mendahului `part2`. Nama yang bukan
 * potongan diletakkan di akhir tanpa diubah urutannya.
 */
fun urutkanNamaPotongan(nama: Iterable<String>): List<String> {
    val potongan = mutableListOf<Pair<String, Int>>()
    val lainnya = mutableListOf<String>()
    for (n in nama) {
        val info = bacaInfoPotongan(n)
        if (info == null) lainnya += n else potongan += n to info.indeks
    }
    return potongan.sortedBy { it.second }.map { it.first } + lainnya
}
