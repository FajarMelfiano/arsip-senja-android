package com.arsipsenja.data.local

import com.arsipsenja.util.TipeFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing metadata kategori: label, warna, dan CSV tipeFile. */
class KoleksiKategoriTest {

    private fun koleksi(tipeFile: String = "") =
        KoleksiEntity(
            chatId = -1001L,
            topicId = 5,
            nama = "Foto",
            deskripsi = null,
            tipeFile = tipeFile,
            jumlahBerkas = 0,
            dibuatPada = 0L,
            terakhirSinkron = 0L,
        )

    @Test
    fun tipeKosongBerartiSemuaTipe() {
        assertEquals(TipeFile.entries.toSet(), koleksi("").tipeDitangani)
    }

    @Test
    fun csvTipeDiparsing() {
        assertEquals(setOf(TipeFile.GAMBAR, TipeFile.VIDEO), koleksi("GAMBAR,VIDEO").tipeDitangani)
    }

    @Test
    fun spasiDanNilaiInvalidDiabaikan() {
        assertEquals(setOf(TipeFile.DOKUMEN), koleksi(" DOKUMEN , BUKAN_TIPE ").tipeDitangani)
    }

    @Test
    fun semuaInvalidJatuhKeSemuaTipe() {
        assertEquals(TipeFile.entries.toSet(), koleksi("SALAH,,").tipeDitangani)
    }

    @Test
    fun defaultLabelDanWarna() {
        val k = koleksi()
        assertNull(k.label)
        assertEquals(0, k.warna)
        assertTrue(k.adalahTopik)
    }
}
