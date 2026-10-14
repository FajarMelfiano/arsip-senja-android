package com.arsipsenja.ui.viewmodel

import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.util.TipeFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Antrean penamaan: duplikat, deteksi baru-tercakup dari kunci (bukan
 * nama), konstruksi item Buat/Tarik, dan ringkasan status.
 */
class AntreanPenamaanTest {

    private fun koleksi(
        chatId: Long = -1001L,
        topicId: Int = 5,
        nama: String = "Tes",
    ) = KoleksiEntity(
        chatId = chatId,
        topicId = topicId,
        nama = nama,
        deskripsi = null,
        jumlahBerkas = 0,
        dibuatPada = 0L,
        terakhirSinkron = 0L,
    )

    // ---- namaDuplikat ----

    @Test
    fun duplikatNamaSamaPersisDitolak() {
        assertTrue(AntreanPenamaan.namaDuplikat("Gambar", listOf("Gambar", "Video"), null))
    }

    @Test
    fun duplikatTidakPeduliKapitalisasi() {
        assertTrue(AntreanPenamaan.namaDuplikat("gambar", listOf("Gambar"), null))
    }

    @Test
    fun namaLainTidakDuplikat() {
        assertFalse(AntreanPenamaan.namaDuplikat("Dokumen", listOf("Gambar"), null))
    }

    @Test
    fun namaSendiriYangDiabaikanBolehDipakaiLagi() {
        // Tarik: Simpan tanpa mengubah nama entri itu sendiri sah.
        assertFalse(AntreanPenamaan.namaDuplikat("Gambar", listOf("Gambar"), "Gambar"))
        assertFalse(AntreanPenamaan.namaDuplikat("gambar", listOf("Gambar"), "GAMBAR"))
    }

    @Test
    fun namaSamaDuaKoleksiTetapDuplikatMeskiSatuDiabaikan() {
        // Dua koleksi sudah bernama "Gambar": satu kemunculan adalah nama
        // entri itu sendiri (diabaikan), satu lagi milik koleksi lain.
        assertTrue(
            AntreanPenamaan.namaDuplikat("Gambar", listOf("Gambar", "Gambar"), "Gambar"),
        )
    }

    @Test
    fun namaKosongBukanDuplikat() {
        // Kosong ditangani validasi wajib-isi, bukan validasi duplikat.
        assertFalse(AntreanPenamaan.namaDuplikat("   ", listOf("Gambar"), null))
    }

    @Test
    fun duplikatMemangkasSpasi() {
        assertTrue(AntreanPenamaan.namaDuplikat("  Gambar  ", listOf("Gambar"), null))
    }

    // ---- baruTercakup: kunci, bukan nama ----

    @Test
    fun kunciSamaNamaBerubahBukanBaru() {
        val sebelum = setOf(KunciKoleksi(-1001L, 5))
        val sesudah = listOf(koleksi(nama = "Nama Baru di Telegram"))
        assertTrue(AntreanPenamaan.baruTercakup(sebelum, sesudah).isEmpty())
    }

    @Test
    fun namaSamaKunciBerbedaTetapBaru() {
        val sebelum = setOf(KunciKoleksi(-1001L, 5))
        val sesudah = listOf(koleksi(chatId = -1002L, topicId = 7, nama = "Tes"))
        assertEquals(1, AntreanPenamaan.baruTercakup(sebelum, sesudah).size)
    }

    @Test
    fun tanpaYangLamaSemuaBaru() {
        val sesudah = listOf(koleksi(), koleksi(chatId = -1002L, topicId = 0))
        assertEquals(2, AntreanPenamaan.baruTercakup(emptySet(), sesudah).size)
    }

    // ---- dariTarik ----

    @Test
    fun dariTarikTopikDanChannelBedaKonteks() {
        val items = AntreanPenamaan.dariTarik(
            listOf(
                koleksi(nama = "Gambar"),
                koleksi(chatId = -1002L, topicId = 0, nama = "Grup Lama"),
            ),
        )
        assertEquals(2, items.size)
        assertEquals(KunciKoleksi(-1001L, 5), items[0].kunci)
        assertEquals("Gambar", items[0].prefill)
        assertEquals("Gambar", items[0].namaAbaikan)
        assertTrue(items[0].konteks.contains("Topik"))
        assertTrue(items[1].konteks.contains("Channel"))
    }

    // ---- dariBuat ----

    @Test
    fun dariBuatMengikutiUrutanTipeFileDanMembawaPengaturanBersama() {
        val items = AntreanPenamaan.dariBuat(
            tipeDipilih = setOf(TipeFile.DOKUMEN, TipeFile.GAMBAR),
            deskripsi = "d",
            label = "l",
            warna = 3,
            kunci = null,
        )
        assertEquals(listOf(TipeFile.GAMBAR, TipeFile.DOKUMEN), items.map { it.tipe })
        assertEquals(listOf("Gambar", "Dokumen"), items.map { it.prefill })
        assertTrue(items.all { it.deskripsi == "d" && it.label == "l" && it.warna == 3 })
        assertTrue(items.all { it.kunci == null && it.namaAbaikan == null })
        assertTrue(items.all { it.konteks == "Topik baru di vault" })
    }

    @Test
    fun dariBuatTanpaTipeKosong() {
        assertTrue(
            AntreanPenamaan.dariBuat(emptySet(), "", "", 0, null).isEmpty(),
        )
    }

    // ---- ringkasan ----

    @Test
    fun ringkasanBuatSuksesSemua() {
        val hasil = AntreanPenamaan.ringkasanBuat(dibuat = 2, gagal = 0)
        assertEquals(NadaStatus.SUKSES, hasil.nada)
        assertEquals("2 koleksi dibuat.", hasil.teks)
    }

    @Test
    fun ringkasanBuatNolNolInfo() {
        val hasil = AntreanPenamaan.ringkasanBuat(dibuat = 0, gagal = 0)
        assertEquals(NadaStatus.INFO, hasil.nada)
        assertEquals("Tidak ada koleksi dibuat.", hasil.teks)
    }

    @Test
    fun ringkasanBuatGagalSemuaGalat() {
        val hasil = AntreanPenamaan.ringkasanBuat(dibuat = 0, gagal = 1)
        assertEquals(NadaStatus.GALAT, hasil.nada)
        assertEquals("1 koleksi gagal dibuat.", hasil.teks)
    }

    @Test
    fun ringkasanBuatSebagianGagalGalat() {
        val hasil = AntreanPenamaan.ringkasanBuat(dibuat = 1, gagal = 1)
        assertEquals(NadaStatus.GALAT, hasil.nada)
        assertEquals("1 koleksi dibuat, 1 gagal.", hasil.teks)
    }

    @Test
    fun ringkasanTarikDenganSufiksDasbor() {
        val hasil = AntreanPenamaan.ringkasanTarik(
            total = 3,
            gagal = 0,
            sufiks = " — lihat di tab Impor",
        )
        assertEquals(NadaStatus.SUKSES, hasil.nada)
        assertEquals("3 koleksi impor diperbarui — lihat di tab Impor.", hasil.teks)
    }

    @Test
    fun ringkasanTarikGagalDinamaiGalat() {
        val hasil = AntreanPenamaan.ringkasanTarik(total = 3, gagal = 1)
        assertEquals(NadaStatus.GALAT, hasil.nada)
        assertEquals("3 koleksi impor diperbarui, 1 gagal dinamai.", hasil.teks)
    }
}
