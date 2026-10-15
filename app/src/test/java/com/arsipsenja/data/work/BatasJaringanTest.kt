package com.arsipsenja.data.work

import androidx.work.NetworkType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit test untuk pemetaan batas jaringan unggahan
 * ([UploadWorker.batasJaringan]).
 */
class BatasJaringanTest {

    @Test
    fun `mode normal mengizinkan semua koneksi`() {
        val batas = UploadWorker.batasJaringan(hanyaWifi = false)
        assertThat(batas.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun `hanya wifi menuntut jaringan tak bermeter`() {
        val batas = UploadWorker.batasJaringan(hanyaWifi = true)
        assertThat(batas.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)
    }
}
