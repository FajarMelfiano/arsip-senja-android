package com.arsipsenja.util

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Uji pemecahan berurutan dan penyatuan potongan.
 *
 * Fokusnya adalah **puncak penggunaan ruang**: pemecahan lama menulis seluruh
 * potongan lebih dulu, sehingga berkas 6 GB butuh sekitar 12 GB di cache.
 */
class PemecahLanjutanTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `jumlah potongan dihitung tanpa membuat berkas`() {
        assertThat(jumlahPotongan(0, 1000)).isEqualTo(1)
        assertThat(jumlahPotongan(1000, 1000)).isEqualTo(1)
        assertThat(jumlahPotongan(1001, 1000)).isEqualTo(2)
        assertThat(jumlahPotongan(10_000, 1000)).isEqualTo(10)
    }

    @Test
    fun `pemecahan berurutan memberi indeks dan jumlah yang benar`() = runBlocking {
        val isi = Random(11).nextBytes(2_500)
        val sumber = folder.newFile("besar.bin").apply { writeBytes(isi) }

        val tercatat = mutableListOf<Triple<String, Int, Int>>()
        val dir = File(folder.root, "potongan")
        pecahBerturutan(sumber, dir, batas = 1_000, buffer = 256, hapusSetelah = false) { f, i, t ->
            tercatat += Triple(f.name, i, t)
        }

        assertThat(tercatat).hasSize(3)
        tercatat.forEach { (nama, indeks, total) ->
            assertThat(total).isEqualTo(3)
            assertThat(indeks).isAtLeast(1)
            assertThat(nama).contains(".part$indeks")
        }
    }

    @Test
    fun `hapus setelah memangkas puncak ruang di cache`() = runBlocking {
        val isi = Random(12).nextBytes(3_000)
        val sumber = folder.newFile("besar2.bin").apply { writeBytes(isi) }
        val dir = File(folder.root, "potongan2")

        var maksimumBertahan = 0
        var sekarangBertahan = 0
        pecahBerturutan(sumber, dir, batas = 1_000, buffer = 256, hapusSetelah = true) { f, _, _ ->
            // Hitung file potongan yang ada pada saat callback berjalan.
            sekarangBertahan = dir.listFiles()?.size ?: 0
            if (sekarangBertahan > maksimumBertahan) maksimumBertahan = sekarangBertahan
        }

        // Kuncinya: saat satu potongan sedang dikirim, hanya satu yang perlu ada.
        // Versi lama akan menyimpan seluruh 3 berkas sekaligus.
        assertThat(maksimumBertahan).isAtMost(1)
    }

    @Test
    fun `berkas kecil diteruskan apa adanya`() = runBlocking {
        val kecil = folder.newFile("kecil.bin").apply { writeBytes(ByteArray(10)) }
        var dipanggil = 0
        pecahBerturutan(kecil, folder.root, batas = 1000) { f, i, t ->
            dipanggil += 1
            assertThat(f).isEqualTo(kecil)
            assertThat(i).isEqualTo(1)
            assertThat(t).isEqualTo(1)
        }
        assertThat(dipanggil).isEqualTo(1)
    }

    @Test
    fun `galat pada satu potongan menghentikan proses dan menghapus sisa`() = runBlocking {
        val isi = Random(13).nextBytes(3_000)
        val sumber = folder.newFile("besar3.bin").apply { writeBytes(isi) }
        val dir = File(folder.root, "potongan3")
        var dipanggil = 0

        try {
            pecahBerturutan(sumber, dir, batas = 1_000, buffer = 256, hapusSetelah = false) { _, i, _ ->
                dipanggil += 1
                if (i == 2) throw IllegalStateException("gagal sengaja")
            }
            throw AssertionError("harus melempar galat")
        } catch (e: IllegalStateException) {
            assertThat(e).hasMessageThat().contains("gagal sengaja")
        }

        assertThat(dipanggil).isEqualTo(2)
        // Potongan ketiga tidak boleh dibuat setelah gagal.
        assertThat(dir.listFiles()?.size ?: 0).isAtMost(2)
    }

    @Test
    fun `gabungan memulihkan isi berkas asli persis`() {
        val isi = Random(14).nextBytes(5_000)
        val sumber = folder.newFile("gabung.bin").apply { writeBytes(isi) }
        val dir = File(folder.root, "pot")
        val potongan = pecahBerkas(sumber, dir, batas = 1_200, buffer = 300)
        assertThat(potongan).hasSize(5)

        val peta = potongan.associateBy { it.name }
        val hasil = File(folder.root, "hasil.bin")
        val tertulis = GabungPotongan.gabungKeBerkas(peta, hasil, ukuranHarapan = isi.size.toLong())

        assertThat(tertulis).isEqualTo(isi.size.toLong())
        assertThat(hasil.readBytes()).isEqualTo(isi)
    }

    @Test
    fun `gabungan menolak ukuran yang tidak sesuai`() {
        val isi = Random(15).nextBytes(3_000)
        val sumber = folder.newFile("gabung2.bin").apply { writeBytes(isi) }
        val potongan = pecahBerkas(sumber, File(folder.root, "pot2"), batas = 1_000, buffer = 200)
        val peta = potongan.associateBy { it.name }

        try {
            GabungPotongan.gabungKeBerkas(peta, File(folder.root, "hasil2.bin"), ukuranHarapan = 99_999L)
            throw AssertionError("harus melempar")
        } catch (e: GabungPotongan.UkuranTidakCocokException) {
            assertThat(e.diharapkan).isEqualTo(99_999L)
            assertThat(e.sebenarnya).isEqualTo(isi.size.toLong())
        }
    }

    @Test
    fun `potongan yang hilang terdeteksi sebelum digabung`() {
        val isi = Random(16).nextBytes(3_000)
        val sumber = folder.newFile("gabung3.bin").apply { writeBytes(isi) }
        val semua = pecahBerkas(sumber, File(folder.root, "pot3"), batas = 1_000, buffer = 200)
        assertThat(semua).hasSize(3)

        // Buang potongan tengah (bagian 2), sisanya utuh.
        val peta = semua.filterNot { bacaInfoPotongan(it.name)?.indeks == 2 }
            .associateBy { it.name }
        assertThat(peta).hasSize(2)
        try {
            GabungPotongan.periksaKelengkapan(peta.keys.toList())
            throw AssertionError("harus melempar")
        } catch (e: GabungPotongan.PotonganHilangException) {
            assertThat(e.indeksHilang).containsExactly(2)
        }
    }

    @Test
    fun `nama asli dapat dikembalikan dari nama potongan`() {
        assertThat(namaAsliDariPotongan("lapor.part2.pdf")).isEqualTo("lapor.pdf")
        assertThat(namaAsliDariPotongan("lapor.part10.pdf")).isEqualTo("lapor.pdf")
        assertThat(namaAsliDariPotongan("tanpaekstensi.part3")).isEqualTo("tanpaekstensi")
        assertThat(namaAsliDariPotongan("biasa.pdf")).isNull()
    }

    @Test
    fun `potongan diurutkan secara numerik bukan leksikografis`() {
        val nama = listOf("a.part10.pdf", "a.part2.pdf", "a.part1.pdf", "lain.pdf")
        assertThat(urutkanNamaPotongan(nama))
            .containsExactly("a.part1.pdf", "a.part2.pdf", "a.part10.pdf", "lain.pdf")
            .inOrder()
    }
}