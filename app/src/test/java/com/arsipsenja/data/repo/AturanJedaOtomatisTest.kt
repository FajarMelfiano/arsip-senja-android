package com.arsipsenja.data.repo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Aturan jeda otomatis antrean unggah (Paket B3): baterai < 15% atau data
 * seluler — masing-masing bisa dimatikan lewat toggle Pengaturan.
 */
class AturanJedaOtomatisTest {

    @Test
    fun `baterai di bawah ambang dan toggle aktif dijeda`() {
        assertThat(
            AturanJedaOtomatis.harusJeda(
                bateraiPersen = 14,
                diDataSeluler = false,
                jedaBateraiAktif = true,
                jedaSelulerAktif = false,
            ),
        ).isEqualTo(AturanJedaOtomatis.Alasan.BATERAI_RENDAH)
    }

    @Test
    fun `baterai tepat di ambang tidak dijeda`() {
        assertThat(
            AturanJedaOtomatis.harusJeda(
                bateraiPersen = 15,
                diDataSeluler = false,
                jedaBateraiAktif = true,
                jedaSelulerAktif = false,
            ),
        ).isNull()
    }

    @Test
    fun `baterai rendah tapi toggle mati tidak dijeda`() {
        assertThat(
            AturanJedaOtomatis.harusJeda(
                bateraiPersen = 5,
                diDataSeluler = false,
                jedaBateraiAktif = false,
                jedaSelulerAktif = false,
            ),
        ).isNull()
    }

    @Test
    fun `data seluler dan toggle aktif dijeda`() {
        assertThat(
            AturanJedaOtomatis.harusJeda(
                bateraiPersen = 80,
                diDataSeluler = true,
                jedaBateraiAktif = true,
                jedaSelulerAktif = true,
            ),
        ).isEqualTo(AturanJedaOtomatis.Alasan.DATA_SELULER)
    }

    @Test
    fun `data seluler tapi toggle mati tidak dijeda`() {
        assertThat(
            AturanJedaOtomatis.harusJeda(
                bateraiPersen = 80,
                diDataSeluler = true,
                jedaBateraiAktif = true,
                jedaSelulerAktif = false,
            ),
        ).isNull()
    }

    @Test
    fun `baterai tak terbaca tidak memicu jeda baterai`() {
        assertThat(
            AturanJedaOtomatis.harusJeda(
                bateraiPersen = null,
                diDataSeluler = false,
                jedaBateraiAktif = true,
                jedaSelulerAktif = false,
            ),
        ).isNull()
    }

    @Test
    fun `baterai rendah diprioritaskan atas data seluler`() {
        assertThat(
            AturanJedaOtomatis.harusJeda(
                bateraiPersen = 10,
                diDataSeluler = true,
                jedaBateraiAktif = true,
                jedaSelulerAktif = true,
            ),
        ).isEqualTo(AturanJedaOtomatis.Alasan.BATERAI_RENDAH)
    }

    @Test
    fun `pesan alasan memakai bahasa indonesia`() {
        val pesan = AturanJedaOtomatis.pesanAlasan(AturanJedaOtomatis.Alasan.BATERAI_RENDAH)
        assertThat(pesan).contains("15%")
        assertThat(pesan).doesNotContain("battery")
    }
}
