package com.arsipsenja.util

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Uji perhitungan SHA-256.
 *
 * Vektor acuan yang dipakai adalah nilai SHA-256 standar, sehingga bila
 * implementasi salah (mis. urutan byte terbalik atau hex salah kapitalisasi)
 * uji ini langsung gagal.
 */
class HashBerkasTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `sha256 teks abc cocok dengan vektor standar`() {
        assertThat(HashBerkas.hitungTeks("abc")).isEqualTo(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        )
    }

    @Test
    fun `sha256 teks kosong cocok dengan vektor standar`() {
        assertThat(HashBerkas.hitungTeks("")).isEqualTo(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
    }

    @Test
    fun `hash berkas sama dengan hash aliran isi yang sama`() {
        val isi = ByteArray(5_000) { (it * 31 % 251).toByte() }
        val berkas = folder.newFile("data.bin").apply { writeBytes(isi) }

        val dariBerkas = HashBerkas.hitungBerkas(berkas)
        val dariAliran = HashBerkas.hitungAliran(ByteArrayInputStream(isi))

        assertThat(dariBerkas).isNotNull()
        assertThat(dariBerkas).isEqualTo(dariAliran)
    }

    @Test
    fun `berkas kosong menghasilkan hash yang sah`() {
        val kosong = folder.newFile("kosong.bin")
        val hasil = HashBerkas.hitungBerkas(kosong)
        assertThat(hasil).isEqualTo(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
    }

    @Test
    fun `perubahan satu byte mengubah hash`() {
        val a = folder.newFile("a.bin").apply { writeBytes(ByteArray(100) { 1 }) }
        val b = folder.newFile("b.bin").apply { writeBytes(ByteArray(100) { 1 }.also { it[50] = 2 }) }
        assertThat(HashBerkas.hitungBerkas(a)).isNotEqualTo(HashBerkas.hitungBerkas(b))
    }

    @Test
    fun `pembatalan mengembalikan null alih-alih hash sebagian`() {
        // Buffer sengaja dikecilkan agar satu berkas besar butuh banyak
        // putaran; dengan buffer produksi 1 MiB, input 100 KB hanya terbaca
        // sekali sehingga jalur pembatalan tidak akan pernah tersentuh.
        val isi = ByteArray(1_000) { 7 }
        var panggilan = 0
        val hasil = HashBerkas.hitungAliran(
            aliran = ByteArrayInputStream(isi),
            masihJalan = { panggilan += 1; panggilan < 3 },
            buffer = 128,
        )
        assertThat(hasil).isNull()
        assertThat(panggilan).isAtLeast(3)
    }

@Test
    fun `perhitungan penuh tetap sama walau buffer dipecah kecil`() {
        val isi = ByteArray(1_000) { (it % 97).toByte() }
        val penuh = HashBerkas.hitungAliran(ByteArrayInputStream(isi), buffer = 1 shl 20)
        val kecil = HashBerkas.hitungAliran(ByteArrayInputStream(isi), buffer = 64)
        assertThat(kecil).isEqualTo(penuh)
    }

    @Test
    fun `hex yang dihasilkan huruf kecil dan sepanjang dua kali jumlah byte`() {
        val hex = HashBerkas.keHex(byteArrayOf(0x00, 0x0F, 0xFF.toByte(), 0x10))
        assertThat(hex).isEqualTo("000fff10")
        assertThat(hex).hasLength(8)
    }

    @Test
    fun `validasi hash menolak nilai yang bukan sha256`() {
        assertThat(HashBerkas.adalahHashSah("a".repeat(64))).isTrue()
        assertThat(HashBerkas.adalahHashSah("A".repeat(64))).isFalse()
        assertThat(HashBerkas.adalahHashSah("a".repeat(63))).isFalse()
        assertThat(HashBerkas.adalahHashSah("g".repeat(64))).isFalse()
        assertThat(HashBerkas.adalahHashSah(null)).isFalse()
        assertThat(HashBerkas.adalahHashSah("")).isFalse()
    }
}