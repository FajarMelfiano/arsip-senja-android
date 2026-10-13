package com.arsipsenja.data.telegram

/**
 * Status otorisasi yang dipahami lapisan UI.
 */
sealed interface StatusOtorisasi {
    /** Klien TDLib sedang disiapkan. */
    data object Menyiapkan : StatusOtorisasi

    /** api_id/api_hash belum diisi pengguna. */
    data object MenungguKredensial : StatusOtorisasi

    /** Menunggu input nomor telepon. */
    data object MenungguNomor : StatusOtorisasi

    /** Menunggu kode OTP; [petunjuk] menjelaskan kanal pengiriman. */
    data class MenungguKode(val petunjuk: String, val hitungMundur: Int) : StatusOtorisasi

    /** Menunggu kata sandi 2FA. */
    data class MenungguSandi(val petunjuk: String?) : StatusOtorisasi

    /** Nomor baru, perlu pendaftaran akun (nama depan/belakang). */
    data class MenungguRegistrasi(val tos: String?) : StatusOtorisasi

    /** Login dari perangkat lain (konfirmasi QR/Telegram). */
    data object MenungguPerangkatLain : StatusOtorisasi

    /** Sudah masuk; arsip siap dipakai. */
    data object Siap : StatusOtorisasi

    /** Sedang keluar atau sesi ditutup. */
    data object Keluar : StatusOtorisasi

    /** Terjadi galat yang menghentikan alur. */
    data class Galat(val pesan: String) : StatusOtorisasi
}

/**
 * Progres transfer berkas yang dilaporkan TDLib lewat UpdateFile.
 */
data class ProgresBerkas(
    val fileId: Int,
    val path: String,
    val ukuran: Long,
    val terkirim: Long,
    val terunduh: Long,
    val selesaiUnggah: Boolean,
    val selesaiUnduh: Boolean,
) {
    /** 0..100, atau -1 bila proporsi belum diketahui. */
    val persenUnggah: Int
        get() = persen(terkirim, ukuran)

    val persenUnduh: Int
        get() = persen(terunduh, ukuran)

    private fun persen(sebagian: Long, total: Long): Int = when {
        total <= 0L -> -1
        sebagian >= total -> 100
        else -> ((sebagian * 100) / total).toInt().coerceIn(0, 99)
    }
}

/** Pasangan pesan terkirim + id lamanya, untuk memperbaiki indeks lokal. */
data class PesanTerkirim(
    val pesan: org.drinkless.tdlib.TdApi.Message,
    val idLama: Long,
)
