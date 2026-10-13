package com.arsipsenja.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTanggalTest {

    @Test
    fun `format tanggal memakai bahasa indonesia`() {
        // 14 Oktober 2026 00:00 UTC+7
        val epoch = 1791949200000L
        val teks = formatTanggal(epoch)
        assertThat(teks).contains("2026")
        assertThat(teks).contains("Okt")
    }

    @Test
    fun `nilai nol menjadi hubung`() {
        assertThat(formatTanggal(0)).isEqualTo("—")
        assertThat(formatTanggalWaktu(0)).isEqualTo("—")
        assertThat(formatWaktu(0)).isEqualTo("—")
    }

    @Test
    fun `format waktu dua digit jam`() {
        val teks = formatWaktu(1791949200000L)
        assertThat(teks).matches("""\d{2}\.\d{2}""")
    }

    @Test
    fun `nomor katalog selalu tiga digit`() {
        assertThat(nomorKatalog(0)).isEqualTo("No. 000")
        assertThat(nomorKatalog(24)).isEqualTo("No. 024")
        assertThat(nomorKatalog(1234)).isEqualTo("No. 1234")
    }
}
