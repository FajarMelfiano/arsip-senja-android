package com.arsipsenja.data.repo

import com.arsipsenja.data.local.BerkasArsipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian unit JVM untuk AturanDuplikat:
 * - Pengelompokan berkas duplikat yang identik (nama, ukuran, koleksi, topik)
 * - Berkas ukuran <= 0 (belum ada metadata/ukuran tak tentu) dikecualikan (aman dari false-positive)
 * - Rapikan menyisakan tepat 1 berkas per grup (nomorKatalog terkecil)
 * - Beda nama, beda ukuran, beda topik, atau beda koleksi tidak dikelompokkan
 */
class AturanDuplikatTest {

    private fun buatBerkas(
        id: String,
        messageId: Long,
        chatId: Long = -100123L,
        koleksiId: Long? = -100123L,
        topicId: Int = 1,
        nama: String = "dokumen.pdf",
        ukuran: Long = 1024L,
        nomorKatalog: Int = 1,
        tanggal: Long = 1000L,
    ): BerkasArsipEntity = BerkasArsipEntity(
        id = id,
        messageId = messageId,
        chatId = chatId,
        koleksiId = koleksiId,
        topicId = topicId,
        nama = nama,
        ukuran = ukuran,
        tanggal = tanggal,
        mime = "application/pdf",
        tipe = "dokumen",
        nomorKatalog = nomorKatalog,
        tdFileId = 1,
        tdLocalPath = null,
    )

    @Test
    fun kelompokkanBerkasIdentikBerjumlahLebihDariSatu() {
        val b1 = buatBerkas(id = "1:1", messageId = 1L, nama = "foto.jpg", ukuran = 2048L, nomorKatalog = 1)
        val b2 = buatBerkas(id = "1:2", messageId = 2L, nama = "foto.jpg", ukuran = 2048L, nomorKatalog = 2)
        val b3 = buatBerkas(id = "1:3", messageId = 3L, nama = "lain.jpg", ukuran = 2048L, nomorKatalog = 3)

        val grup = AturanDuplikat.kelompokkan(listOf(b1, b2, b3))

        assertEquals("Harus ada 1 grup duplikat", 1, grup.size)
        val g = grup.first()
        assertEquals("Nama grup harus foto.jpg", "foto.jpg", g.nama)
        assertEquals("Ukuran grup harus 2048", 2048L, g.ukuran)
        assertEquals("Jumlah rangkap harus 2", 2, g.jumlahRangkap)
        assertEquals("Berkas yang dipertahankan harus b1 (nomorKatalog 1)", b1, g.yangDipertahankan)
        assertEquals("Berkas yang dihapus harus b2", listOf(b2), g.yangDihapus)
    }

    @Test
    fun berkasUkuranNolAtauNegatifDikecualikanDariDuplikat() {
        // Dua file berbeda dengan nama sama yang ukurannya 0 B (metadata TDLib belum siap)
        // TIDAK boleh dianggap duplikat agar salah satunya tidak terhapus tanpa sengaja!
        val b1 = buatBerkas(id = "1:10", messageId = 10L, nama = "skripsi.docx", ukuran = 0L, nomorKatalog = 10)
        val b2 = buatBerkas(id = "1:11", messageId = 11L, nama = "skripsi.docx", ukuran = 0L, nomorKatalog = 11)
        val bNegatif = buatBerkas(id = "1:12", messageId = 12L, nama = "skripsi.docx", ukuran = -1L, nomorKatalog = 12)

        val grup = AturanDuplikat.kelompokkan(listOf(b1, b2, bNegatif))

        assertTrue("Berkas berukuran <= 0 tidak boleh dimasukkan ke grup duplikat", grup.isEmpty())
    }

    @Test
    fun pisahkanUntukRapikanMenyisakanTepatSatuPerGrup() {
        val b1 = buatBerkas(id = "1:1", messageId = 1L, nama = "video.mp4", ukuran = 5000L, nomorKatalog = 1)
        val b2 = buatBerkas(id = "1:2", messageId = 2L, nama = "video.mp4", ukuran = 5000L, nomorKatalog = 2)
        val b3 = buatBerkas(id = "1:3", messageId = 3L, nama = "video.mp4", ukuran = 5000L, nomorKatalog = 3)

        val b4 = buatBerkas(id = "1:4", messageId = 4L, nama = "lagu.mp3", ukuran = 3000L, nomorKatalog = 4)
        val b5 = buatBerkas(id = "1:5", messageId = 5L, nama = "lagu.mp3", ukuran = 3000L, nomorKatalog = 5)

        val grup = AturanDuplikat.kelompokkan(listOf(b1, b2, b3, b4, b5))
        assertEquals(2, grup.size)

        val (dipertahankan, dihapus) = AturanDuplikat.pisahkanUntukRapikan(grup)

        assertEquals("Tepat 2 berkas dipertahankan (1 per grup)", 2, dipertahankan.size)
        assertTrue(dipertahankan.contains(b1))
        assertTrue(dipertahankan.contains(b4))

        assertEquals("Tepat 3 berkas dihapus (2 dari grup pertama, 1 dari grup kedua)", 3, dihapus.size)
        assertTrue(dihapus.contains(b2))
        assertTrue(dihapus.contains(b3))
        assertTrue(dihapus.contains(b5))
    }

    @Test
    fun bedaNamaUkuranTopikAtauKoleksiTidakDikelompokkan() {
        val b1 = buatBerkas(id = "1:1", messageId = 1L, chatId = -1001L, koleksiId = null, topicId = 1, nama = "a.txt", ukuran = 100L)
        val b2 = buatBerkas(id = "1:2", messageId = 2L, chatId = -1001L, koleksiId = null, topicId = 1, nama = "b.txt", ukuran = 100L)
        val b3 = buatBerkas(id = "1:3", messageId = 3L, chatId = -1001L, koleksiId = null, topicId = 1, nama = "a.txt", ukuran = 200L)
        val b4 = buatBerkas(id = "1:4", messageId = 4L, chatId = -1001L, koleksiId = null, topicId = 2, nama = "a.txt", ukuran = 100L)
        val b5 = buatBerkas(id = "1:5", messageId = 5L, chatId = -1002L, koleksiId = null, topicId = 1, nama = "a.txt", ukuran = 100L)

        val grup = AturanDuplikat.kelompokkan(listOf(b1, b2, b3, b4, b5))

        assertTrue("Seluruh berkas berbeda kriteria tidak boleh dikelompokkan", grup.isEmpty())
    }
}
