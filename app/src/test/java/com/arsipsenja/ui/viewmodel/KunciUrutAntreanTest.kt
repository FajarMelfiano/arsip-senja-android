package com.arsipsenja.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test untuk aturan pengurutan antrean unggah
 * ([kunciUrutAntrean]): prioritas dulu, lalu berjalan, lalu sisanya.
 */
class KunciUrutAntreanTest {

    @Test
    fun `prioritas selalu paling atas`() {
        assertThat(kunciUrutAntrean(prioritas = true, sedangBerjalan = false))
            .isLessThan(kunciUrutAntrean(prioritas = false, sedangBerjalan = true))
        assertThat(kunciUrutAntrean(prioritas = true, sedangBerjalan = true))
            .isLessThan(kunciUrutAntrean(prioritas = false, sedangBerjalan = false))
    }

    @Test
    fun `berjalan di atas antrean biasa`() {
        assertThat(kunciUrutAntrean(prioritas = false, sedangBerjalan = true))
            .isLessThan(kunciUrutAntrean(prioritas = false, sedangBerjalan = false))
    }

    @Test
    fun `kunci konsisten untuk keadaan sama`() {
        assertThat(kunciUrutAntrean(true, true)).isEqualTo(kunciUrutAntrean(true, false))
    }
}
