package com.arsipsenja.util

import java.util.Locale

private val LOCALE_ID = Locale("id", "ID")

private val SATUAN = arrayOf("B", "kB", "MB", "GB", "TB", "PB")

/**
 * Format ukuran berkas gaya Indonesia: 812 B, 1,4 MB, 2,1 GB.
 * Nilai negatif dimaknai "tidak diketahui" dan dirender sebagai tanda hubung.
 */
fun formatUkuran(byte: Long): String {
    if (byte < 0) return "—"
    if (byte < 1024) return "$byte B"

    var nilai = byte.toDouble()
    var indeks = 0
    while (nilai >= 1024 && indeks < SATUAN.lastIndex) {
        nilai /= 1024
        indeks++
    }
    val desimal = if (nilai < 10) 1 else 0
    val teks = String.format(LOCALE_ID, "%,.${desimal}f", nilai)
    return "$teks ${SATUAN[indeks]}"
}

/**
 * Gabungan ukuran untuk statistik; "—" bila semua nilai tidak diketahui.
 */
fun formatTotalUkuran(total: Long, jumlah: Int): String =
    if (jumlah <= 0) "0 B" else formatUkuran(total)
