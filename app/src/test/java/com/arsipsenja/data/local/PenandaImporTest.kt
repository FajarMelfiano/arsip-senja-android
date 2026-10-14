package com.arsipsenja.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan pisah pribadi vs impor: pribadi tidak pernah duplikat jadi impor,
 * entri impor bertanda tanpa mengubah field lain, penyaringan berkas tepat.
 */
class PenandaImporTest {

    private fun koleksi(
        chatId: Long = -1001L,
        topicId: Int = 5,
        asalImpor: Boolean = false,
    ) = KoleksiEntity(
        chatId = chatId,
        topicId = topicId,
        nama = "Tes",
        deskripsi = null,
        asalImpor = asalImpor,
        jumlahBerkas = 0,
        dibuatPada = 0L,
        terakhirSinkron = 0L,
    )

    private fun berkas(
        chatId: Long = -1001L,
        koleksiId: Long? = -1001L,
        topicId: Int = 5,
    ) = BerkasArsipEntity(
        id = "$chatId:7",
        messageId = 7L,
        chatId = chatId,
        koleksiId = koleksiId,
        topicId = topicId,
        nama = "a.pdf",
        ukuran = 1L,
        tanggal = 0L,
        mime = "application/pdf",
        tipe = "DOKUMEN",
        tdFileId = 0,
        tdLocalPath = null,
        deskripsi = null,
        dibuatPada = 0L,
    )

    @Test
    fun entriBaruBolehJadiImpor() {
        assertTrue(PenandaImpor.bolehJadiImpor(null))
    }

    @Test
    fun koleksiPribadiTidakBolehJadiImpor() {
        assertFalse(PenandaImpor.bolehJadiImpor(koleksi(asalImpor = false)))
    }

    @Test
    fun koleksiImporLamaBolehDisegarkan() {
        assertTrue(PenandaImpor.bolehJadiImpor(koleksi(asalImpor = true)))
    }

    @Test
    fun tandaiImporHanyaUbahFlag() {
        val awal = koleksi(asalImpor = false)
        val hasil = PenandaImpor.tandaiImpor(awal)
        assertTrue(hasil.asalImpor)
        assertEquals(awal.copy(asalImpor = true), hasil)
    }

    @Test
    fun kunciImporHanyaBerisiImpor() {
        val daftar = listOf(
            koleksi(chatId = -1001L, topicId = 5, asalImpor = true),
            koleksi(chatId = -1002L, topicId = 0, asalImpor = false),
        )
        assertEquals(setOf(-1001L to 5), PenandaImpor.kunciImpor(daftar))
    }

    @Test
    fun berkasMilikImporTerdeteksi() {
        val kunci = setOf(-1001L to 5)
        assertTrue(PenandaImpor.milikImpor(berkas(), kunci))
        assertFalse(PenandaImpor.milikImpor(berkas(chatId = -1009L, koleksiId = -1009L), kunci))
    }

    @Test
    fun berkasYatimPakaiChatId() {
        val kunci = setOf(-1001L to 5)
        assertTrue(PenandaImpor.milikImpor(berkas(koleksiId = null), kunci))
    }

    @Test
    fun defaultKoleksiAdalahPribadi() {
        assertFalse(koleksi().asalImpor)
    }
}
