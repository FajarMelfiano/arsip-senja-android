package com.arsipsenja.data.repo

import androidx.work.WorkInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tes unit untuk fungsi murni [ArsipRepository.perluDaftarkanUlang].
 *
 * Fungsi mengembalikan true kecuali ada state SUCCEEDED — artinya:
 * hanya bila berkas sudah terkirim penuh, enqueue ulang dilewati.
 */
class PerluDaftarkanUlangTest {

    @Test
    fun `daftar kosong — perlu daftarkan ulang`() {
        // Work belum pernah dibuat atau sudah terpangkas dari WorkManager
        assertThat(ArsipRepository.perluDaftarkanUlang(emptyList())).isTrue()
    }

    @Test
    fun `state SUCCEEDED — tidak perlu daftarkan ulang`() {
        // Berkas sudah terkirim penuh; jangan kirim ulang
        assertThat(
            ArsipRepository.perluDaftarkanUlang(listOf(WorkInfo.State.SUCCEEDED))
        ).isFalse()
    }

    @Test
    fun `state ENQUEUED — perlu daftarkan ulang`() {
        // WorkManager belum menjalankan; daftarkan REPLACE agar seketika
        assertThat(
            ArsipRepository.perluDaftarkanUlang(listOf(WorkInfo.State.ENQUEUED))
        ).isTrue()
    }

    @Test
    fun `state RUNNING — perlu daftarkan ulang`() {
        // Sedang berjalan (race condition); REPLACE akan menggantikannya
        assertThat(
            ArsipRepository.perluDaftarkanUlang(listOf(WorkInfo.State.RUNNING))
        ).isTrue()
    }

    @Test
    fun `state FAILED — perlu daftarkan ulang`() {
        // Gagal sebelumnya; lanjutkan berarti coba lagi seketika
        assertThat(
            ArsipRepository.perluDaftarkanUlang(listOf(WorkInfo.State.FAILED))
        ).isTrue()
    }

    @Test
    fun `state CANCELLED — perlu daftarkan ulang`() {
        // Dibatalkan saat jeda; lanjutkan = enqueue baru
        assertThat(
            ArsipRepository.perluDaftarkanUlang(listOf(WorkInfo.State.CANCELLED))
        ).isTrue()
    }

    @Test
    fun `state BLOCKED — perlu daftarkan ulang`() {
        // Diblokir constraint (mis. jaringan); daftarkan agar mulai saat constraint terpenuhi
        assertThat(
            ArsipRepository.perluDaftarkanUlang(listOf(WorkInfo.State.BLOCKED))
        ).isTrue()
    }

    @Test
    fun `campuran CANCELLED dan SUCCEEDED — tidak perlu daftarkan ulang`() {
        // Ada state SUCCEEDED berarti berkas sudah terkirim; jangan kirim ulang
        assertThat(
            ArsipRepository.perluDaftarkanUlang(
                listOf(WorkInfo.State.CANCELLED, WorkInfo.State.SUCCEEDED)
            )
        ).isFalse()
    }
}
