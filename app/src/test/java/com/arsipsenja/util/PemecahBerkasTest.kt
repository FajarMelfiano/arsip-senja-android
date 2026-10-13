package com.arsipsenja.util

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PemecahBerkasTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `nama potongan menyisipkan part sebelum ekstensi`() {
        assertThat(namaPotongan("lapor.pdf", 1)).isEqualTo("lapor.part1.pdf")
        assertThat(namaPotongan("lapor.pdf", 12)).isEqualTo("lapor.part12.pdf")
        assertThat(namaPotongan("arsip.tar.gz", 2)).isEqualTo("arsip.tar.part2.gz")
        assertThat(namaPotongan("tanpaekstensi", 3)).isEqualTo("tanpaekstensi.part3")
        assertThat(namaPotongan(".bashrc", 1)).isEqualTo(".bashrc.part1")
    }

    @Test
    fun `perluPecah hanya di atas batas`() {
        assertThat(perluPecah(BATAS_POTONGAN)).isFalse()
        assertThat(perluPecah(BATAS_POTONGAN + 1)).isTrue()
        assertThat(perluPecah(0)).isFalse()
    }

    @Test
    fun `berkas kecil tidak dipecah`() {
        val kecil = folder.newFile("kecil.bin").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        val hasil = pecahBerkas(kecil, folder.root, batas = 1024)
        assertThat(hasil).hasSize(1)
        assertThat(hasil[0]).isEqualTo(kecil)
    }

    @Test
    fun `berkas besar terpecah lalu tersusun kembali utuh`() {
        val ukuran = 10_000
        val isi = Random(42).nextBytes(ukuran)
        val sumber = folder.newFile("besar.bin").apply { writeBytes(isi) }

        val hasil = pecahBerkas(sumber, folder.root, batas = 3_000, buffer = 512)

        assertThat(hasil).hasSize(4)
        hasil.forEachIndexed { i, potongan ->
            assertThat(potongan.name).contains("besar.part${i + 1}")
            assertThat(potongan.length()).isAtMost(3_000L)
        }

        val gabung = hasil.fold(ByteArray(0)) { akhir, f -> akhir + f.readBytes() }
        assertThat(gabung).isEqualTo(isi)
        assertThat(sumber.readBytes()).isEqualTo(isi) // sumber tidak disentuh
    }

    @Test
    fun `potongan terakhir menyimpan sisa persis`() {
        val isi = Random(7).nextBytes(1_000)
        val sumber = folder.newFile("sisa.bin").apply { writeBytes(isi) }
        val hasil = pecahBerkas(sumber, folder.root, batas = 400, buffer = 100)
        assertThat(hasil).hasSize(3)
        assertThat(hasil[0].length()).isEqualTo(400L)
        assertThat(hasil[1].length()).isEqualTo(400L)
        assertThat(hasil[2].length()).isEqualTo(200L)
        assertThat(hasil.joinToString("") { File(it.path).name }.isNotEmpty()).isTrue()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `batas nol ditolak`() {
        pecahBerkas(folder.newFile("x.bin").apply { writeBytes(ByteArray(10)) }, folder.root, batas = 0)
    }
}
