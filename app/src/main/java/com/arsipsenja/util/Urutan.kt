package com.arsipsenja.util

/** Urutan daftar berkas di dasbor. */
enum class UrutBerkas(val label: String) {
    TANGGAL("Terbaru"),
    NAMA("Nama"),
    UKURAN("Ukuran"),
}

/** Arah pengurutan. */
enum class ArahUrut(val label: String) {
    TURUN("Menurun"),
    NAIK("Menaik"),
}

/** Tampilan daftar dasbor. */
enum class TampilanDasbor {
    KISI,
    DAFTAR,
}

/**
 * Penanda status berkas terhadap penyimpanan perangkat.
 */
enum class StatusLokal {
    /** Baru terindeks, belum pernah diunduh. */
    BELUM,

    /** Sedang diunduh (persen diketahui lewat progres). */
    PROSES,

    /** Sudah ada di penyimpanan perangkat. */
    ADA,
}
