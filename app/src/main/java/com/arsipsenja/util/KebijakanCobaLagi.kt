package com.arsipsenja.util

/**
 * Kebijakan percobaan ulang berdasarkan kode galat TDLib.
 *
 * ## Kenapa ini ada
 *
 * Versi lama memakai `Result.retry()` tanpa syarat untuk semua galat. Akibatnya
 * berkas yang pesan Telegram-nya sudah dihapus akan dicoba ulang **selamanya**
 * dengan backoff WorkManager — menghabiskan baterai dan kuota tanpa pernah
 * memberi tahu pengguna. Sebaliknya, `FLOOD_WAIT` yang hanya perlu menunggu
 * beberapa detik justru ikut gagal permanen.
 *
 * Modul ini murni (tanpa Android/TDLib) sehingga setiap aturan bisa diuji.
 *
 * Rujukan kode: https://core.telegram.org/api/errors
 */
sealed interface KeputusanCobaLagi {

    /** Galat permanen — jangan coba lagi, beri tahu pengguna. */
    data object Berhenti : KeputusanCobaLagi

    /** Galat sementara — coba lagi setelah [jedaMilidetik]. */
    data class Tunda(val jedaMilidetik: Long) : KeputusanCobaLagi

    /**
     * Galat sementara dengan backoff eksponensial berbasis percobaan.
     * @param percobaan nomor percobaan mulai 0.
     */
    data class Backoff(val percobaan: Int) : KeputusanCobaLagi

    /** Apakah galat ini layak dicoba lagi? */
    val bolehDiulang: Boolean get() = this !is Berhenti
}

/**
 * Peta kode galat TDLib → keputusan.
 *
 * Fungsi murni: [retryAfterDetik] diambil dari
 * `TdApi.Error.parameters["retry_after"]` oleh pemanggil, sehingga logika ini
 * dapat diuji tanpa TDLib.
 */
object KebijakanCobaLagi {

    /** Jeda minimum saat server memberi tahu `retry_after`. */
    private const val JEDA_MINIMUM = 5_000L

    /** Jeda default bila `retry_after` tidak ada. */
    private const val JEDA_FLOOD_BAWAHAN = 60_000L

    /** Backoff dasar: 5 s, 10 s, 20 s … maksimum 30 menit. */
    private const val BACKOFF_DASAR = 5_000L
    private const val BACKOFF_MAKS = 30 * 60 * 1_000L

    /** Galat belum ditentukan (mis. exception non-TDLib) → coba lagi. */
    const val KODE_TIDAK_DIKETAHUI = 0

    /**
     * Putuskan apa yang harus dilakukan terhadap sebuah galat TDLib.
     *
     * @param kode kode galat TDLib ([KODE_TIDAK_DIKETAHUI] bila bukan TDLib).
     * @param retryAfterDetik nilai `retry_after` dari parameter galat, bila ada.
     * @param percobaan nomor percobaan mulai 0, untuk perhitungan backoff.
     */
    fun putuskan(
        kode: Int,
        retryAfterDetik: Int? = null,
        percobaan: Int = 0,
    ): KeputusanCobaLagi = when (kode) {
        // 401 Unauthorized — sesi dicabut / log out dari perangkat lain.
        // 403 CHAT_FORBIDDEN — tidak punya akses ke vault lagi.
        // 400 — sebagian besar galat 400 bersifat permanen (nomor/chat salah,
        //       PASSWORD_HASH_INVALID, CHAT_ADMIN_REQUIRED, dst).
        400, 401, 403 -> KeputusanCobaLagi.Berhenti

        // 420 FLOOD_WAIT — wajib menunggu sesuai permintaan server.
        420 -> KeputusanCobaLagi.Tunda(
            ((retryAfterDetik ?: (JEDA_FLOOD_BAWAHAN / 1000).toInt()).toLong() * 1000L)
                .coerceAtLeast(JEDA_MINIMUM)
        )

        // 429 Too Many Requests — antrean penuh, tunggu sebentar.
        429 -> KeputusanCobaLagi.Tunda(
            (retryAfterDetik?.toLong()?.times(1000L) ?: 30_000L).coerceAtLeast(JEDA_MINIMUM)
        )

        // 500/502/503 — sisi server, coba lagi dengan backoff.
        500, 502, 503 -> backoff(percobaan)

        // Kode tak dikenal & galat non-TDLib: asumsikan sementara.
        else -> backoff(percobaan)
    }

    /**
     * Keputusan berdasarkan nama exception Android, untuk galat I/O lokal yang
     * tidak pernah sampai ke TDLib.
     */
    fun dariGalatLokal(namaPenyebab: String): KeputusanCobaLagi =
        if (namaPenyebab.startsWith("java.io.FileNotFoundException") ||
            namaPenyebab.startsWith("android.database.sqlite.SQLiteException")
        ) {
            KeputusanCobaLagi.Berhenti
        } else {
            KeputusanCobaLagi.Backoff(0)
        }

    /** Hitung jeda backoff eksponensial dengan pembatas atas. */
    private fun backoff(percobaan: Int): KeputusanCobaLagi =
        KeputusanCobaLagi.Backoff(percobaan.coerceAtLeast(0))

    /** Jeda aktual (ms) untuk keputusan Backoff — dipakai worker. */
    fun jedaUntuk(keputusan: KeputusanCobaLagi): Long = when (keputusan) {
        is KeputusanCobaLagi.Berhenti -> 0L
        is KeputusanCobaLagi.Tunda -> keputusan.jedaMilidetik
        is KeputusanCobaLagi.Backoff ->
            (BACKOFF_DASAR shl keputusan.percobaan.coerceIn(0, 10)).coerceAtMost(BACKOFF_MAKS)
    }
}