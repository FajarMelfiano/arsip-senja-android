package com.arsipsenja.data.telegram

import com.arsipsenja.util.KebijakanCobaLagi
import com.arsipsenja.util.KeputusanCobaLagi
import org.drinkless.tdlib.TdApi

/**
 * Galat yang berasal dari TDLib atau dari lapisan pembungkusnya.
 *
 * ## Kenapa ada `retryAfterDetik`
 *
 * Sebelumnya kode galat dibawa lalu dibuang, sehingga `FLOOD_WAIT` (420)
 * diperlakukan sama dengan galat mati: mencoba ulang seketika, yang justru
 * memperburuk throttle. Nilai ini kini diteruskan ke [KeputusanCobaLagi] agar
 * worker tahu kapan sebaiknya mencoba lagi.
 *
 * ## Catatan tentang binding ini
 *
 * `org.drinkless.tdlib` (**pinned ke commit `11850ef`**) mengekspos `TdApi.Error`
 * **hanya** berisi `code` dan `message` — tidak ada `parameters` seperti di
 * dokumen TDLib. Karena itu durasi tunggu dibaca dari teks pesan
 * ([ambilDurasiTunggu]) dan nilainya=null bila tidak terbaca. Bila binding
 * diperbarui ke versi yang mengekspos `parameters`, cukup ganti isi fungsi ini.
 *
 * Urutan parameter menempatkan `cause` di posisi ketiga agar pemanggil lama
 * yang menulis `TelegramException(pesan, 0, sebab)` tetap compiles.
 */
open class TelegramException(
    val pesan: String,
    val kode: Int = 0,
    cause: Throwable? = null,
    /** Durasi tunggu yang diminta server, dalam detik; null bila tidak ada. */
    val retryAfterDetik: Int? = null,
) : Exception(pesan, cause) {

    constructor(error: TdApi.Error) : this(
        pesan = error.message ?: "Galat TDLib",
        kode = error.code,
        retryAfterDetik = ambilDurasiTunggu(error.message),
    )

    /**
     * Apakah galat ini perlu ditampilkan ke pengguna, atau cukup dicatat?
     *
     * Galat yang hanya terjadi di dalam trial cepat (kirim ulang otomatis,
     *ircuit breaker) tidak boleh membanjiri layar dengan dialog.
     */
    fun keputusan(percobaan: Int = 0): KeputusanCobaLagi =
        KebijakanCobaLagi.putuskan(kode, retryAfterDetik, percobaan)

    /** Jeda dalam milidetik sebelum mencoba lagi. */
    fun jedaMilidetik(percobaan: Int = 0): Long =
        KebijakanCobaLagi.jedaUntuk(keputusan(percobaan))

    /** Galat ini permanen sehingga coba lagi tidak akan menolong. */
    val permanen: Boolean get() = !keputusan().bolehDiulang

    companion object {
        /** Kunci parameter galat TDLib yang memuat durasi tunggu. */
        const val KUNCI_RETRY_AFTER = "retry_after"

        /**
         * Tebakan terbaik durasi tunggu dari teks pesan galat.
         *
         * TDLib menulis pesan seperti `FLOOD_WAIT_42` atau
         * `Too Many Requests: retry after 42`. Karena binding yang dipakai tidak
         * mengekspos `parameters`, angka terbesar dalam pesan diambil sebagai
         * durasi dalam detik.
         *
         * @return detik, atau null bila tidak ada angka yang relevan.
         */
        fun ambilDurasiTunggu(pesan: String?): Int? {
            if (pesan.isNullOrBlank()) return null
            val hanyaAngka = Regex("\\d+").findAll(pesan).mapNotNull { it.value.toIntOrNull() }
            return hanyaAngka.maxOrNull()?.takeIf { it > 0 && it <= SEKITAR_JAM_DETIK }
        }

        /** Batas atas durasi yang dianggap masuk akal, agar tidak salah baca sebuah id. */
        private const val SEKITAR_JAM_DETIK = 24 * 60 * 60
    }
}