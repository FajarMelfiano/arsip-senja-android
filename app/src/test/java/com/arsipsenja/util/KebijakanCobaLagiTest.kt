package com.arsipsenja.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Uji kebijakan percobaan ulang.
 *
 * Uji paling penting di sini adalah kasus yang dulu tidak tertangani:
 * berkas yang pesan Telegram-nya sudah dihapus (galat permanen) **tidak boleh**
 * diulang selamanya.
 */
class KebijakanCobaLagiTest {

    @Test
    fun `kode 400 401 403 dihentikan permanen`() {
        for (kode in listOf(400, 401, 403)) {
            val keputusan = KebijakanCobaLagi.putuskan(kode)
            assertThat(keputusan).isEqualTo(KeputusanCobaLagi.Berhenti)
            assertThat(keputusan.bolehDiulang).isFalse()
        }
    }

    @Test
    fun `flood wait menunggu sesuai retry after dari server`() {
        val keputusan = KebijakanCobaLagi.putuskan(420, retryAfterDetik = 30)
        assertThat(keputusan).isInstanceOf(KeputusanCobaLagi.Tunda::class.java)
        assertThat((keputusan as KeputusanCobaLagi.Tunda).jedaMilidetik).isEqualTo(30_000L)
    }

    @Test
    fun `flood wait tanpa retry after memakai jeda bawaan`() {
        val keputusan = KebijakanCobaLagi.putuskan(420) as KeputusanCobaLagi.Tunda
        assertThat(keputusan.jedaMilidetik).isEqualTo(60_000L)
    }

    @Test
    fun `retry after yang sangat kecil tetap dihormati jeda minimum`() {
        val keputusan = KebijakanCobaLagi.putuskan(420, retryAfterDetik = 0) as KeputusanCobaLagi.Tunda
        assertThat(keputusan.jedaMilidetik).isAtLeast(5_000L)
    }

    @Test
    fun `kode 429 menunggu tiga puluh detik`() {
        val keputusan = KebijakanCobaLagi.putuskan(429) as KeputusanCobaLagi.Tunda
        assertThat(keputusan.jedaMilidetik).isEqualTo(30_000L)
    }

    @Test
    fun `galat sisi server memakai backoff`() {
        for (kode in listOf(500, 502, 503)) {
            assertThat(KebijakanCobaLagi.putuskan(kode, percobaan = 3))
                .isEqualTo(KeputusanCobaLagi.Backoff(3))
        }
    }

    @Test
    fun `kode tak dikenal diasumsikan sementara`() {
        assertThat(KebijakanCobaLagi.putuskan(9999, percobaan = 1))
            .isEqualTo(KeputusanCobaLagi.Backoff(1))
    }

    @Test
    fun `jeda backoff tumbuh eksponensial dan dibatasi`() {
        val kecil = KebijakanCobaLagi.jedaUntuk(KeputusanCobaLagi.Backoff(0))
        val sedang = KebijakanCobaLagi.jedaUntuk(KeputusanCobaLagi.Backoff(3))
        val besar = KebijakanCobaLagi.jedaUntuk(KeputusanCobaLagi.Backoff(30))

        assertThat(kecil).isEqualTo(5_000L)
        assertThat(sedang).isGreaterThan(kecil)
        assertThat(besar).isAtMost(30 * 60 * 1000L)
    }

    @Test
    fun `galat berkas lokal yang tidak ditemukan dihentikan`() {
        assertThat(KebijakanCobaLagi.dariGalatLokal("java.io.FileNotFoundException"))
            .isEqualTo(KeputusanCobaLagi.Berhenti)
        assertThat(KebijakanCobaLagi.dariGalatLokal("java.io.IOException"))
            .isInstanceOf(KeputusanCobaLagi.Backoff::class.java)
    }

    @Test
    fun `keputusan berhenti memiliki jeda nol`() {
        assertThat(KebijakanCobaLagi.jedaUntuk(KeputusanCobaLagi.Berhenti)).isEqualTo(0L)
    }
}