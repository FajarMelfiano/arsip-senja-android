package com.arsipsenja.util

import com.google.common.truth.Truth.assertThat
import com.arsipsenja.data.repo.PenjagaVault
import org.junit.Test

/**
 * Regression test untuk dua kerusakan yang membuat aplikasi tidak bisa dipakai.
 *
 * ## 1. Kelompok multi-potongan hilang diam-diam
 *
 * `grupId` pernah dibentuk sebagai string pendek seperti `g00000000ab12cd34`.
 * Pola UUID di [HeaderArsip.susun] menuntut format 8-4-4-4-12, sehingga nilai
 * itu tidak cocok dan field `g=` **dibuang tanpa pesan apa pun**. Akibatnya berkas besar
 * yang sudah terpecah tidak lagi dapat disatukan setelah perangkat di-reset.
 *
 * Uji ini memutar-balik nilai grup melalui `susun` lalu `uraiHeaderArsip`,
 * persis seperti yang terjadi di dunia nyata — bukan sekadar memeriksa
 * panjangnya string.
 *
 * ## 2. Vault lama tidak terdeteksi setelah dipasang ulang
 *
 * Deteksi vault memakai aturan yang sama dengan pembuatan vault. Padahal
 * aplikasi ini mendukung channel sebagai koleksi (`topicId = 0`). Vault
 * channel milik sendiri karena itu tidak pernah ditemukan, lalu aplikasi
 * membuat vault baru secara diam-diam.
 */
class RegresPerbaikanTest {

    private val hashContoh = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    // ---- 1. UUID kelompok harus benar-benar bertahan pada header -----------

    @Test
    fun `uuid dari hash terbaca kembali utuh dari caption`() {
        val grup = HeaderArsip.uuidDariHash(hashContoh)

        val header = HeaderArsip.potongan(
            grupId = grup,
            indeks = 2,
            jumlah = 5,
            namaAsli = "video.mkv",
            ukuranTotal = 9_000_000,
        )
        val hasil = uraiHeaderArsip(header.susun())

        // Inilah assertion yang menangkap bug aslinya: groupId harus utuh.
        assertThat(hasil).isNotNull()
        assertThat(hasil!!.grupId).isEqualTo(grup)
        assertThat(hasil.jumlahPotongan).isEqualTo(5)
        assertThat(hasil.indeksPotongan).isEqualTo(2)
    }

    @Test
    fun `uuid dari hash berbentuk UUID yang sah`() {
        val grup = HeaderArsip.uuidDariHash(hashContoh)
        assertThat(grup).matches(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
        )
    }

    @Test
    fun `isi sama menghasilkan kelompok sama di koleksi berbeda`() {
        assertThat(HeaderArsip.uuidDariHash(hashContoh))
            .isEqualTo(HeaderArsip.uuidDariHash(hashContoh))
    }

    @Test
    fun `isi berbeda menghasilkan kelompok berbeda`() {
        val lain = HashBerkas.hitungTeks("berbeda")
        assertThat(HeaderArsip.uuidDariHash(lain))
            .isNotEqualTo(HeaderArsip.uuidDariHash(hashContoh))
    }

    @Test
    fun `seluruh potongan satu berkas memakai kelompok yang sama`() {
        val grup = HeaderArsip.uuidDariHash(hashContoh)
        val captionSatu = HeaderArsip.potongan(grup, 1, 3, "a.bin", 300).susun()
        val captionDua = HeaderArsip.potongan(grup, 2, 3, "a.bin", 300).susun()
        val captionTiga = HeaderArsip.potongan(grup, 3, 3, "a.bin", 300).susun()

        val grup1 = uraiHeaderArsip(captionSatu)?.grupId
        assertThat(grup1).isEqualTo(grup)
        assertThat(uraiHeaderArsip(captionDua)?.grupId).isEqualTo(grup)
        assertThat(uraiHeaderArsip(captionTiga)?.grupId).isEqualTo(grup)

        // Urutannya harus berbeda agar file bisa disusun kembali dengan benar.
        val indeks = listOf(captionSatu, captionDua, captionTiga)
            .mapNotNull { uraiHeaderArsip(it)?.indeksPotongan }
        assertThat(indeks).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `caption tanpa header yang sah ditolak lebih awal`() {
        try {
            HeaderArsip.uuidDariHash("bukan-hash")
            throw AssertionError("hash bukan heksadesimal seharusnya ditolak")
        } catch (e: IllegalArgumentException) {
            // Perilaku yang diharapkan.
        }
    }

    // ---- 2. Deteksi vault harus menerima channel milik sendiri -----------

    @Test
    fun `channel milik sendiri sah untuk deteksi vault`() {
        // Channel + owned. Ini kondisi vault lama milik pengguna.
        assertThat(PenjagaVault.layakDeteksi(isSupergroup = true, isChannel = true)).isTrue()
    }

    @Test
    fun `grup forum milik sendiri sah untuk deteksi vault`() {
        assertThat(PenjagaVault.layakDeteksi(isSupergroup = true, isChannel = false)).isTrue()
    }

    @Test
    fun `chat privat bukan supergroup tidak masuk kandidat vault`() {
        assertThat(PenjagaVault.layakDeteksi(isSupergroup = false, isChannel = false)).isFalse()
    }

    @Test
    fun `deteksi dan pembuatan vault memakai aturan yang berbeda`() {
        // Membuat vault tetap mewajibkan supergroup forum: channel tidak punya
        // topik sehingga tidak bisa dipakai sebagai vault baru.
        assertThat(PenjagaVault.grupBaruSah(isSupergroup = true, isChannel = true)).isFalse()
        // Deteksi harus menemukan channel milik sendiri sebagai arsip lama.
        assertThat(PenjagaVault.layakDeteksi(isSupergroup = true, isChannel = true)).isTrue()
    }
}