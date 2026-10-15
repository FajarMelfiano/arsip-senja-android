package com.arsipsenja.util

import com.arsipsenja.data.local.BerkasArsipEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit test untuk [AturanSenjaMemories] (aturan murni, tanpa Android framework).
 */
class AturanSenjaMemoriesTest {

    private val zona: TimeZone = TimeZone.getDefault()

    private fun tanggal(tahun: Int, bulan: Int, hari: Int): Long {
        val c = Calendar.getInstance(zona)
        c.set(tahun, bulan, hari, 12, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun berkas(id: String, tanggal: Long) = BerkasArsipEntity(
        id = id,
        messageId = 1,
        chatId = 1,
        koleksiId = null,
        nama = "foto.jpg",
        ukuran = 10,
        tanggal = tanggal,
        mime = "image/jpeg",
        tipe = "GAMBAR",
        tdFileId = 1,
        tdLocalPath = null,
        deskripsi = null,
        dibuatPada = tanggal,
    )

    private fun hariIni(): Triple<Int, Int, Int> {
        val c = Calendar.getInstance(zona)
        return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `tanggal sama di tahun lalu muncul sebagai kenangan`() {
        val (tahun, bulan, hari) = hariIni()
        val tahunLalu = tanggal(tahun - 1, bulan, hari)

        val hasil = AturanSenjaMemories.filtering(listOf(berkas("a", tahunLalu)), zona)

        assertThat(hasil).hasSize(1)
        assertThat(hasil[0].id).isEqualTo("a")
    }

    @Test
    fun `tanggal beda tidak muncul`() {
        val (tahun, bulan, hari) = hariIni()
        val kemarin = Calendar.getInstance(zona).apply { add(Calendar.DAY_OF_MONTH, -1) }
        val lain = tanggal(tahun - 1, kemarin.get(Calendar.MONTH), kemarin.get(Calendar.DAY_OF_MONTH))

        val hasil = AturanSenjaMemories.filtering(listOf(berkas("b", lain)), zona)

        assertThat(hasil).isEmpty()
    }

    @Test
    fun `berkas tahun berjalan dikecualikan`() {
        val (tahun, bulan, hari) = hariIni()
        val tahunIni = tanggal(tahun, bulan, hari)

        val hasil = AturanSenjaMemories.filtering(listOf(berkas("c", tahunIni)), zona)

        assertThat(hasil).isEmpty()
    }

    @Test
    fun `beberapa tahun diurutkan dari yang terbaru`() {
        val (tahun, bulan, hari) = hariIni()
        val lama = berkas("lama", tanggal(tahun - 5, bulan, hari))
        val baru = berkas("baru", tanggal(tahun - 1, bulan, hari))

        val hasil = AturanSenjaMemories.filtering(listOf(lama, baru), zona)

        assertThat(hasil.map { it.id }).containsExactly("baru", "lama").inOrder()
    }

    @Test
    fun `daftar kosong menghasilkan daftar kosong`() {
        assertThat(AturanSenjaMemories.filtering(emptyList(), zona)).isEmpty()
    }

    @Test
    fun `formatTanggalHariIni menghasilkan teks tanggal`() {
        val teks = AturanSenjaMemories.formatTanggalHariIni(zona)
        assertThat(teks).isNotEmpty()
    }
}
