package com.arsipsenja.data.repo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Aturan resolusi konflik sinkron dua arah (Paket B2): last-write-wins
 * berdasarkan timestamp; seri dimenangkan pihak remote.
 */
class AturanKonflikTest {

    @Test
    fun `operasi lokal lebih baru dimenangkan lokal`() {
        val keputusan = AturanKonflik.selesaikan(
            waktuLokalMilis = 2_000L,
            waktuRemoteMilis = 1_000L,
        )
        assertThat(keputusan.pemenang).isEqualTo(AturanKonflik.Pemenang.LOKAL)
        assertThat(keputusan.alasan).contains("perangkat ini")
    }

    @Test
    fun `pembaruan remote lebih baru dimenangkan remote`() {
        val keputusan = AturanKonflik.selesaikan(
            waktuLokalMilis = 1_000L,
            waktuRemoteMilis = 2_000L,
        )
        assertThat(keputusan.pemenang).isEqualTo(AturanKonflik.Pemenang.REMOTE)
        assertThat(keputusan.alasan).contains("Telegram")
    }

    @Test
    fun `timestamp seri dimenangkan remote`() {
        val keputusan = AturanKonflik.selesaikan(
            waktuLokalMilis = 5_000L,
            waktuRemoteMilis = 5_000L,
        )
        assertThat(keputusan.pemenang).isEqualTo(AturanKonflik.Pemenang.REMOTE)
    }

    @Test
    fun `alasan memakai bahasa indonesia`() {
        val keputusan = AturanKonflik.selesaikan(3_000L, 1_000L)
        assertThat(keputusan.alasan).isNotEmpty()
        // Tidak boleh ada bahasa Inggris di pesan pengguna.
        assertThat(keputusan.alasan).doesNotContain("remote")
    }
}
