package com.arsipsenja.data.repo

/**
 * Aturan murni pengenalan idempoten unggah:
 * - Jendela toleransi 7 hari (bukan hanya 90 menit),
 * - Fallback ukuran tak diketahui (ukuran 0 B dicocokkan berdasarkan nama + chatId),
 * - Pencocokan presisi nama, chatId, dan ukuran (bila keduanya > 0).
 */
object AturanIdempoten {

    /** Jendela default idempoten: 7 hari dalam milidetik. */
    const val JENDELA_DEFAULT_MS: Long = 7L * 24L * 60L * 60L * 1000L

    /**
     * Memeriksa apakah berkas kandidat cocok dengan target yang sudah terindeks.
     *
     * @param namaKandidat Nama berkas yang akan/sedang diunggah.
     * @param ukuranKandidat Ukuran berkas lokal yang akan diunggah.
     * @param chatIdKandidat ID chat/koleksi tujuan.
     * @param namaTarget Nama berkas yang ada di indeks.
     * @param ukuranTarget Ukuran berkas yang ada di indeks (bisa 0 jika metadata belum lengkap).
     * @param chatIdTarget ID chat/koleksi di indeks.
     * @param waktuTarget Cap waktu baris di indeks (dibuatPada atau tanggal).
     * @param sekarang Waktu saat pemeriksaan dilakukan.
     * @param jendelaMs Ambang batas jendela waktu (default 7 hari).
     */
    fun cocok(
        namaKandidat: String,
        ukuranKandidat: Long,
        chatIdKandidat: Long,
        namaTarget: String,
        ukuranTarget: Long,
        chatIdTarget: Long,
        waktuTarget: Long,
        sekarang: Long,
        jendelaMs: Long = JENDELA_DEFAULT_MS,
    ): Boolean {
        if (namaKandidat != namaTarget) return false
        if (chatIdKandidat != chatIdTarget) return false
        val selisih = sekarang - waktuTarget
        if (selisih < 0 || selisih > jendelaMs) return false
        // Fallback ukuran tak diketahui: bila ukuran baris terindeks = 0 ATAU ukuran lokal = 0,
        // cocokkan HANYA nama + chatId dalam jendela waktu.
        if (ukuranKandidat <= 0L || ukuranTarget <= 0L) return true
        return ukuranKandidat == ukuranTarget
    }
}
