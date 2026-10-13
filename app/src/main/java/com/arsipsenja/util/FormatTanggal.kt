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
