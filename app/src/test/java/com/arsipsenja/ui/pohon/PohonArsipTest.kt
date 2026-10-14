package com.arsipsenja.ui.pohon

import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.util.TipeFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Builder pohon arsip murni (Vault → Koleksi → Tipe → Berkas):
 * struktur, hitungan, total ukuran, filter nama, dan koleksi kosong.
 */
class PohonArsipTest {

    private fun koleksi(
        chatId: Long,
        topicId: Int,
        nama: String,
    ) = KoleksiEntity(
        chatId = chatId,
        topicId = topicId,
        nama = nama,
        deskripsi = null,
        jumlahBerkas = 0,
        dibuatPada = 0L,
        terakhirSinkron = 0L,
    )

    private fun berkas(
        nama: String,
        koleksiId: Long?,
        topicId: Int,
        tipe: String,
        ukuran: Long,
    ) = BerkasArsipEntity(
        id = "$koleksiId:$nama",
        messageId = nama.hashCode().toLong(),
        chatId = koleksiId ?: -100999L,
        koleksiId = koleksiId,
        topicId = topicId,
        nama = nama,
        ukuran = ukuran,
        tanggal = 1_000L,
        mime = "application/octet-stream",
        tipe = tipe,
        nomorKatalog = 0,
        tdFileId = 0,
        tdLocalPath = null,
        deskripsi = null,
        dibuatPada = 1_000L,
    )

    @Test
    fun duaKoleksiTigaBerkasStrukturHitunganUkuranBenar() {
        val daftar = listOf(
            koleksi(-1001L, 1, "Kenangan"),
            koleksi(-1001L, 2, "Dokumen"),
        )
        val berkas = listOf(
            berkas("foto.jpg", -1001L, 1, TipeFile.GAMBAR.name, 1_000L),
            berkas("surat.pdf", -1001L, 1, TipeFile.DOKUMEN.name, 2_000L),
            berkas("lagu.mp3", null, 0, TipeFile.AUDIO.name, 4_000L),
        )

        val pohon = bangunPohon(daftar, berkas)

        assertEquals(2, pohon.jumlahKoleksi)
        assertEquals(3, pohon.jumlahBerkas)
        assertEquals(7_000L, pohon.totalUkuran)
        assertEquals(3, pohon.akar.anak.size)

        val kenangan = pohon.akar.anak[0] as SimpulKoleksi
        assertEquals("Kenangan", kenangan.nama)
        assertEquals(2, kenangan.jumlahBerkas)
        assertEquals(3_000L, kenangan.totalUkuran)
        // Tipe sesuai urutan enum: GAMBAR sebelum DOKUMEN.
        assertEquals(2, kenangan.anak.size)
        assertEquals(TipeFile.GAMBAR, (kenangan.anak[0] as SimpulTipe).tipe)
        assertEquals(TipeFile.DOKUMEN, (kenangan.anak[1] as SimpulTipe).tipe)
        val daun = (kenangan.anak[0] as SimpulTipe).anak[0] as SimpulBerkas
        assertEquals("foto.jpg", daun.nama)
        assertEquals(1, daun.jumlahBerkas)

        val yatim = pohon.akar.anak[2] as SimpulKoleksi
        assertTrue(yatim.yatim)
        assertEquals("Tanpa kategori", yatim.nama)
        assertEquals(1, yatim.jumlahBerkas)
        assertEquals(4_000L, yatim.totalUkuran)
    }

    @Test
    fun filterNamaMenyisakanCabangCocokDanLeluhurnya() {
        val daftar = listOf(
            koleksi(-1001L, 1, "Kenangan"),
            koleksi(-1001L, 2, "Dokumen"),
        )
        val berkas = listOf(
            berkas("foto pantai.jpg", -1001L, 1, TipeFile.GAMBAR.name, 1_000L),
            berkas("surat.pdf", -1001L, 1, TipeFile.DOKUMEN.name, 2_000L),
            berkas("nota.pdf", -1001L, 2, TipeFile.DOKUMEN.name, 500L),
        )

        val pohon = bangunPohon(daftar, berkas, "pantai")

        assertEquals(1, pohon.jumlahBerkas)
        assertEquals(1, pohon.akar.anak.size)
        val kenangan = pohon.akar.anak[0] as SimpulKoleksi
        assertEquals("Kenangan", kenangan.nama)
        // Hanya tipe GAMBAR yang bertahan; DOKUMEN disembunyikan.
        assertEquals(1, kenangan.anak.size)
        assertEquals(TipeFile.GAMBAR, (kenangan.anak[0] as SimpulTipe).tipe)
    }

    @Test
    fun koleksiKosongTetapMunculTanpaFilter() {
        val daftar = listOf(
            koleksi(-1001L, 1, "Kenangan"),
            koleksi(-1001L, 2, "Kosong"),
        )
        val berkas = listOf(
            berkas("foto.jpg", -1001L, 1, TipeFile.GAMBAR.name, 1_000L),
        )

        val pohon = bangunPohon(daftar, berkas)

        assertEquals(2, pohon.jumlahKoleksi)
        val kosong = pohon.akar.anak[1] as SimpulKoleksi
        assertEquals("Kosong", kosong.nama)
        assertEquals(0, kosong.jumlahBerkas)
        assertEquals(0L, kosong.totalUkuran)
        assertTrue(kosong.anak.isEmpty())
    }

    @Test
    fun berkasUrutNamaDanTipeInvalidJadiLainnya() {
        val daftar = listOf(koleksi(-1001L, 1, "Campuran"))
        val berkas = listOf(
            berkas("zebra.bin", -1001L, 1, "TIPU_TAK_ADA", 100L),
            berkas("apel.bin", -1001L, 1, "TIPU_TAK_ADA", 200L),
        )

        val pohon = bangunPohon(daftar, berkas)

        val campuran = pohon.akar.anak[0] as SimpulKoleksi
        assertEquals(1, campuran.anak.size)
        val lainnya = campuran.anak[0] as SimpulTipe
        assertEquals(TipeFile.LAINNYA, lainnya.tipe)
        val nama = lainnya.anak.map { (it as SimpulBerkas).nama }
        assertEquals(listOf("apel.bin", "zebra.bin"), nama)
    }
}
