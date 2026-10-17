package com.arsipsenja.data.repo

/**
 * Aturan murni resolusi konflik sinkron dua arah (Paket B2).
 *
 * Konflik terjadi bila ada operasi lokal yang masih tertunda di
 * [OperasiTertundaEntity] DAN pembaruan realtime dari Telegram menyentuh
 * target yang sama. Kebijakan: last-write-wins berdasarkan timestamp
 * (milis epoch). Timestamp lokal = [dibuatPada] operasi; timestamp
 * remote = waktu pesan Telegram tiba (epoch dari TDLib bila ada,
 * jika tidak waktu terima lokal).
 *
 * Seri (timestamp sama persis) dimenangkan pihak remote: server Telegram
 * adalah sumber kebenaran untuk konten yang sudah terkirim, dan operasi
 * lokal yang kalah dicatat ke log konflik agar pengguna bisa meninjaunya
 * di Pengaturan, bukan hilang diam-diam.
 */
object AturanKonflik {

    /** Pemenang resolusi konflik. */
    enum class Pemenang {
        /** Operasi lokal dipertahankan; pembaruan remote diabaikan. */
        LOKAL,

        /** Pembaruan remote diterapkan; operasi lokal dibatalkan. */
        REMOTE,
    }

    /** Hasil resolusi satu konflik. */
    data class KeputusanKonflik(
        val pemenang: Pemenang,
        /** Penjelasan Bahasa Indonesia untuk log konflik. */
        val alasan: String,
    )

    /**
     * Selesaikan konflik antara operasi lokal tertunda dan pembaruan remote.
     *
     * @param waktuLokalMilis timestamp operasi lokal (dibuatPada).
     * @param waktuRemoteMilis timestamp pembaruan remote.
     */
    fun selesaikan(waktuLokalMilis: Long, waktuRemoteMilis: Long): KeputusanKonflik =
        if (waktuLokalMilis > waktuRemoteMilis) {
            KeputusanKonflik(
                Pemenang.LOKAL,
                "Perubahan di perangkat ini lebih baru " +
                    "(${formatSelisih(waktuLokalMilis - waktuRemoteMilis)}), " +
                    "jadi perubahan dari Telegram diabaikan.",
            )
        } else {
            KeputusanKonflik(
                Pemenang.REMOTE,
                "Perubahan dari Telegram lebih baru " +
                    "(${formatSelisih(waktuRemoteMilis - waktuLokalMilis)}), " +
                    "jadi operasi lokal yang tertunda dibatalkan.",
            )
        }

    private fun formatSelisih(selisihMilis: Long): String {
        val detik = selisihMilis / 1000
        return when {
            detik < 60 -> "$detik dtk"
            detik < 3600 -> "${detik / 60} mnt"
            else -> "${detik / 3600} jam"
        }
    }
}
