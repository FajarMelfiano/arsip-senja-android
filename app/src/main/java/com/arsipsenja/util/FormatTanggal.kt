package com.arsipsenja.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LOCALE_ID = Locale("id", "ID")

/** Contoh: "14 Okt 2026". */
fun formatTanggal(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    val format = SimpleDateFormat("d MMM yyyy", LOCALE_ID)
    return format.format(Date(epochMillis))
}

/** Contoh: "14 Okt 2026 · 19.32". */
fun formatTanggalWaktu(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    val format = SimpleDateFormat("d MMM yyyy · HH.mm", LOCALE_ID)
    return format.format(Date(epochMillis))
}

/** Contoh: "19.32" — untuk progres transfer terkini. */
fun formatWaktu(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    return SimpleDateFormat("HH.mm", LOCALE_ID).format(Date(epochMillis))
}

/** Nomor indeks katalog: 24 → "No. 024". */
fun nomorKatalog(nomor: Int): String = "No. " + String.format(Locale.US, "%03d", nomor)

/**
 * Waktu relatif Bahasa Indonesia untuk cap kesegaran, mis. hasil cek
 * kesehatan vault: "baru saja", "12 menit lalu", "3 jam lalu", "2 hari lalu".
 * Nilai <= 0 berarti belum pernah dicek → "belum pernah dicek".
 */
fun formatRelatif(epochMillis: Long, sekarang: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0L) return "belum pernah dicek"
    val selisih = (sekarang - epochMillis).coerceAtLeast(0L)
    val menit = selisih / 60_000L
    return when {
        menit < 1 -> "baru saja"
        menit < 60 -> "$menit menit lalu"
        menit < 60 * 24 -> "${menit / 60} jam lalu"
        else -> "${menit / (60 * 24)} hari lalu"
    }
}
