package com.arsipsenja.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan murni mesin realtime: keputusan ingest, ambang sinkron ulang,
 * dedupe, dan konstanta jendela/ambang.
 */
class AturanRealtimeTest {

    // ---- keputusanPesan ----

    @Test
    fun pesanBerkasDikenalBaruDiproses() {
        assertEquals(
            AturanRealtime.KeputusanPesan.PROSES,
            AturanRealtime.keputusanPesan(dikenal = true, sudahAda = false, adaBerkas = true),
        )
    }

    @Test
    fun chatAsingDiabaikanWalauBerkas() {
        // Tidak boleh membuat koleksi liar dari chat/topik tak dikenal.
        assertEquals(
            AturanRealtime.KeputusanPesan.ABAIKAN_TAK_DIKENAL,
            AturanRealtime.keputusanPesan(dikenal = false, sudahAda = false, adaBerkas = true),
        )
    }

    @Test
    fun duplikatDilewatiWalauDikenal() {
        assertEquals(
            AturanRealtime.KeputusanPesan.LEWATI_DUPLIKAT,
            AturanRealtime.keputusanPesan(dikenal = true, sudahAda = true, adaBerkas = true),
        )
    }

    @Test
    fun pesanTeksDikenalDilewati() {
        assertEquals(
            AturanRealtime.KeputusanPesan.LEWATI_BUKAN_BERKAS,
            AturanRealtime.keputusanPesan(dikenal = true, sudahAda = false, adaBerkas = false),
        )
    }

    @Test
    fun chatAsingMenangAtasDuplikat() {
        // Urutan guard: tak-dikenal dicek pertama — pesan asing tidak
        // pernah masuk indeks apa pun.
        assertEquals(
            AturanRealtime.KeputusanPesan.ABAIKAN_TAK_DIKENAL,
            AturanRealtime.keputusanPesan(dikenal = false, sudahAda = true, adaBerkas = true),
        )
    }

    // ---- bolehSinkronUlang ----

    @Test
    fun belumPernahSinkronBolehJalan() {
        assertTrue(AturanRealtime.bolehSinkronUlang(null, sekarang = 1_000_000L))
    }

    @Test
    fun baruSajaSinkronDilewati() {
        // 30 detik lalu — masih dalam ambang 60 detik.
        assertFalse(AturanRealtime.bolehSinkronUlang(1_000_000L, sekarang = 1_030_000L))
    }

    @Test
    fun melewatiAmbangBolehJalan() {
        // 61 detik lalu — sudah lewat ambang.
        assertTrue(AturanRealtime.bolehSinkronUlang(1_000_000L, sekarang = 1_061_000L))
    }

    @Test
    fun tepatDiAmbangBolehJalan() {
        assertTrue(AturanRealtime.bolehSinkronUlang(1_000_000L, sekarang = 1_060_000L))
    }

    // ---- kunciDedupe ----

    @Test
    fun kunciDedupeFormatChatIdMessageId() {
        assertEquals("-100123:456", AturanRealtime.kunciDedupe(-100123L, 456L))
    }

    @Test
    fun kunciDedupeBerbedaUntukPesanBerbeda() {
        val a = AturanRealtime.kunciDedupe(-100123L, 456L)
        val b = AturanRealtime.kunciDedupe(-100123L, 457L)
        val c = AturanRealtime.kunciDedupe(-100124L, 456L)
        assertTrue(a != b && a != c && b != c)
    }

    // ---- Konstanta ----

    @Test
    fun jendelaBatchDuaDetik() {
        assertEquals(2000L, AturanRealtime.JENDELA_BATCH_MS)
    }

    @Test
    fun ambangSinkronUlangSatuMenit() {
        assertEquals(60_000L, AturanRealtime.AMBANG_SINKRON_ULANG_MS)
    }
}
