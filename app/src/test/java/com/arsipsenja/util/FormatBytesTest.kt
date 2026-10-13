package com.arsipsenja.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatBytesTest {

    @Test
    fun `ukuran kecil tetap byte`() {
        assertThat(formatUkuran(0)).isEqualTo("0 B")
        assertThat(formatUkuran(1)).isEqualTo("1 B")
        assertThat(formatUkuran(1023)).isEqualTo("1023 B")
    }

    @Test
    fun `ukuran sedang pakai satuan kecil`() {
        assertThat(formatUkuran(1024)).isEqualTo("1,0 kB")
        assertThat(formatUkuran(1536)).isEqualTo("1,5 kB")
    }

    @Test
    fun `ukuran besar memakai koma desimal indonesia`() {
        assertThat(formatUkuran(1_500_000L)).isEqualTo("1,4 MB")
        assertThat(formatUkuran(2_147_483_648L)).isEqualTo("2,0 GB")
        assertThat(formatUkuran(1024L * 1024 * 1024 * 1024)).isEqualTo("1,0 TB")
    }

    @Test
    fun `nilai negatif dianggap tak diketahui`() {
        assertThat(formatUkuran(-1)).isEqualTo("—")
        assertThat(formatUkuran(Long.MIN_VALUE)).isEqualTo("—")
    }

    @Test
    fun `total tanpa berkas bernol`() {
        assertThat(formatTotalUkuran(0, 0)).isEqualTo("0 B")
        assertThat(formatTotalUkuran(2048, 3)).isEqualTo("2,0 kB")
    }
}
