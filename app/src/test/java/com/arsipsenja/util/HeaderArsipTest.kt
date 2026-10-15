package com.arsipsenja.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Uji header arsip pada caption pesan.
 *
 * Fokus uji ini adalah **kompatibilitas dua arah**: header yang disusun harus
 * dapat dibaca kembali, dan caption lama tanpa header harus tetap terbaca
 * sebagai "bukan header" tanpa melempar galat.
 */
class HeaderArsipTest {

    private val hashContoh = "a".repeat(64)
    private val uuidContoh = "550e8400-e29b-41d4-a716-446655440000"

    // ---- penyusunan & pembulatan utuh -------------------------------------

    @Test
    fun `header berkas utuh tetap utuh setelah disusun lalu diurai`() {
        val header = HeaderArsip.utuh(hashContoh, "laporan.pdf", 1_234_567)
        val hasil = uraiHeaderArsip(header.susun())
        assertThat(hasil).isNotNull()
        assertThat(hasil!!.hashSha256).isEqualTo(hashContoh)
        assertThat(hasil.namaAsli).isEqualTo("laporan.pdf")
        assertThat(hasil.ukuranTotal).isEqualTo(1_234_567L)
        assertThat(hasil.terpecah).isFalse()
    }

    @Test
    fun `header berkas terpecah tetap lengkap setelah diurai`() {
        val header = HeaderArsip.potongan(
            grupId = uuidContoh,
            indeks = 2,
            jumlah = 3,
            namaAsli = "video-4k.mkv",
            ukuranTotal = 6_000_000_000,
            hashPotongan = hashContoh,
        )
        val hasil = uraiHeaderArsip(header.susun())!!
        assertThat(hasil.indeksPotongan).isEqualTo(2)
        assertThat(hasil.jumlahPotongan).isEqualTo(3)
        assertThat(hasil.grupId).isEqualTo(uuidContoh)
        assertThat(hasil.namaAsli).isEqualTo("video-4k.mkv")
        assertThat(hasil.ukuranTotal).isEqualTo(6_000_000_000L)
        assertThat(hasil.terpecah).isTrue()
    }

    @Test
    fun `grup id dibuat otomatis bila belum ada`() {
        val a = HeaderArsip.potongan(null, 1, 2, "x.bin", 100)
        val b = HeaderArsip.potongan(null, 1, 2, "x.bin", 100)
        assertThat(a.grupId).isNotNull()
        assertThat(a.grupId).isNotEqualTo(b.grupId)
    }

    @Test
    fun `nama berkas dengan karakter khusus tetap utuh setelah diurai`() {
        val nama = "Rapor & Nilai (2026) #1/2 — über café.pdf"
        val header = HeaderArsip.utuh(null, nama, 10)
        assertThat(uraiHeaderArsip(header.susun())!!.namaAsli).isEqualTo(nama)
    }

    // ---- caption pengguna -------------------------------------------------

    @Test
    fun `caption pengguna menyusul header pada baris kedua`() {
        val header = HeaderArsip.utuh(hashContoh, "a.pdf", 1)
        val caption = header.susun("catatan arsip 2026")
        assertThat(caption).contains("\n\n")
        assertThat(captionTanpaHeader(caption)).isEqualTo("catatan arsip 2026")
    }

    @Test
    fun `header tanpa caption tidak menghasilkan baris kosong menggantung`() {
        val caption = HeaderArsip.utuh(hashContoh, "a.pdf", 1).susun()
        assertThat(caption).doesNotContain("\n")
        assertThat(captionTanpaHeader(caption)).isNull()
    }

    // ---- kompatibilitas ke belakang ---------------------------------------

    @Test
    fun `caption lama tanpa header dibaca sebagai bukan header`() {
        assertThat(uraiHeaderArsip(null)).isNull()
        assertThat(uraiHeaderArsip("")).isNull()
        assertThat(uraiHeaderArsip("   ")).isNull()
        assertThat(uraiHeaderArsip("Unggah manual")).isNull()
        assertThat(uraiHeaderArsip("AS2 h=abc")).isNull()
    }

    @Test
    fun `caption pengguna tanpa header utuh dikembalikan apa adanya`() {
        assertThat(captionTanpaHeader("catatan biasa")).isEqualTo("catatan biasa")
        assertThat(captionTanpaHeader(null)).isNull()
    }

    // ---- ketahanan parser (lenient) ---------------------------------------

    @Test
    fun `hash dengan panjang salah diabaikan tanpa menggagalkan field lain`() {
        val teks = "AS1 h=pendek n=a.pdf s=5"
        val hasil = uraiHeaderArsip(teks)!!
        assertThat(hasil.hashSha256).isNull()
        assertThat(hasil.namaAsli).isEqualTo("a.pdf")
        assertThat(hasil.ukuranTotal).isEqualTo(5L)
    }

    @Test
    fun `kunci asing tetap diabaikan untuk kompatibilitas ke depan`() {
        val hasil = uraiHeaderArsip("AS1 h=$hashContoh n=a.pdf x=99 z=apa")!!
        assertThat(hasil.hashSha256).isEqualTo(hashContoh)
        assertThat(hasil.namaAsli).isEqualTo("a.pdf")
    }

    @Test
    fun `indeks lebih besar dari jumlah ditolak`() {
        assertThat(uraiHeaderArsip("AS1 i=5/3 n=a.pdf")!!.indeksPotongan).isNull()
    }

    @Test
    fun `indeks nol ditolak`() {
        assertThat(uraiHeaderArsip("AS1 i=0/3 n=a.pdf")!!.jumlahPotongan).isNull()
    }

    @Test
    fun `ukuran negatif ditolak`() {
        assertThat(uraiHeaderArsip("AS1 n=a.pdf s=-5")!!.ukuranTotal).isNull()
    }

    @Test
    fun `ukuran bukan angka ditolak`() {
        assertThat(uraiHeaderArsip("AS1 s=banyak")!!.ukuranTotal).isNull()
    }

    @Test
    fun `potongan tunggal dinormalkan menjadi berkas utuh`() {
        val header = HeaderArsip.potongan(uuidContoh, 1, 1, "a.pdf", 10)
        assertThat(uraiHeaderArsip(header.susun())!!.terpecah).isFalse()
    }

    @Test
    fun `header kosong tetap valid dan semua field kosong`() {
        val hasil = uraiHeaderArsip(HeaderArsip.SENTINEL)!!
        assertThat(hasil.hashSha256).isNull()
        assertThat(hasil.namaAsli).isNull()
        assertThat(hasil.punyaHash).isFalse()
    }

    @Test
    fun `nama kosong ditolak karena menunjukkan masukan yang rusak`() {
        // Fail-fast di batas unggahan jauh lebih murah daripada mengunggah
        // pesan tanpa nama yang tidak bisa dikenali lagi di kemudian hari.
        try {
            HeaderArsip.utuh(null, "", 1).susun()
            throw AssertionError("header dengan nama kosong seharusnya ditolak")
        } catch (e: IllegalArgumentException) {
            assertThat(e).hasMessageThat().contains("namaAsli")
        }
    }

    @Test
    fun `nama yang tidak diketahui tetap menghasilkan header yang sah`() {
        val caption = HeaderArsip(hashSha256 = hashContoh).susun()
        assertThat(caption).doesNotContain("n=")
        assertThat(uraiHeaderArsip(caption)).isNotNull()
    }
}