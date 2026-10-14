package com.arsipsenja.data.local

import com.arsipsenja.data.repo.GalatButuhVaultBaru
import com.arsipsenja.data.repo.PenjagaVault
import com.arsipsenja.data.telegram.TelegramException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kunci koleksi (Build #24): hash/verifikasi, validasi tiap tipe,
 * perluBukaKunci, migrasi 3→4, preservasi metadata, dan regresi
 * terjemahkanGalatForum Build #23.
 *
 * Batasan jujur: migrasi hanya diverifikasi di level objek Migration
 * (start/end version) + review SQL — MigrationTestHelper (artefak
 * room-testing) tidak tersedia di proyek ini, jadi uji buka-tutup database
 * SQLite tidak dijalankan di sini.
 */
class KunciRahasiaTest {

    private fun entitasTerkunci(
        tipe: TipeKunci = TipeKunci.PIN,
        garam: String = "garam-uji",
        hash: String = "hash-uji",
    ) = KoleksiEntity(
        chatId = -1001L,
        topicId = 7,
        nama = "Rahasia",
        deskripsi = null,
        kunciTipe = tipe.name,
        kunciGaram = garam,
        kunciHash = hash,
        jumlahBerkas = 0,
        dibuatPada = 0L,
        terakhirSinkron = 0L,
    )

    private fun entitasTerbuka() = KoleksiEntity(
        chatId = -1001L,
        topicId = 7,
        nama = "Umum",
        deskripsi = null,
        jumlahBerkas = 0,
        dibuatPada = 0L,
        terakhirSinkron = 0L,
    )

    // ---- Hash / verifikasi ----

    @Test
    fun rahasiaBenarLolosVerifikasi() {
        val garam = KunciRahasia.buatGaram()
        val hash = KunciRahasia.hash("1234", garam)
        assertTrue(KunciRahasia.verifikasi("1234", garam, hash))
    }

    @Test
    fun rahasiaSalahGagalVerifikasi() {
        val garam = KunciRahasia.buatGaram()
        val hash = KunciRahasia.hash("1234", garam)
        assertFalse(KunciRahasia.verifikasi("4321", garam, hash))
    }

    @Test
    fun garamBerbedaMenghasilkanHashBerbeda() {
        val a = KunciRahasia.hash("rahasia-sama", KunciRahasia.buatGaram())
        val b = KunciRahasia.hash("rahasia-sama", KunciRahasia.buatGaram())
        assertNotEquals(a, b)
    }

    @Test
    fun hashTidakSamaDenganPlaintext() {
        val garam = KunciRahasia.buatGaram()
        val hash = KunciRahasia.hash("1234", garam)
        assertFalse(hash.contains("1234"))
        assertEquals(64, hash.length)
    }

    @Test
    fun garamKosongAtauHashKosongSelaluGagal() {
        assertFalse(KunciRahasia.verifikasi("1234", "", "abc"))
        assertFalse(KunciRahasia.verifikasi("1234", "garam", ""))
    }

    // ---- Validasi tiap tipe ----

    @Test
    fun pinValidEmpatSampaiEnamDigit() {
        assertNull(KunciRahasia.validasi(TipeKunci.PIN, "1234"))
        assertNull(KunciRahasia.validasi(TipeKunci.PIN, "123456"))
    }

    @Test
    fun pinPendekAtauBukanAngkaDitolak() {
        assertNotEquals(null, KunciRahasia.validasi(TipeKunci.PIN, "123"))
        assertNotEquals(null, KunciRahasia.validasi(TipeKunci.PIN, "1234567"))
        assertNotEquals(null, KunciRahasia.validasi(TipeKunci.PIN, "12ab"))
        assertNotEquals(null, KunciRahasia.validasi(TipeKunci.PIN, ""))
        assertEquals("PIN harus 4–6 angka.", KunciRahasia.validasi(TipeKunci.PIN, "12"))
    }

    @Test
    fun kataSandiMinimalEmpatKarakter() {
        assertNull(KunciRahasia.validasi(TipeKunci.KATA_SANDI, "abcd"))
        assertEquals(
            "Kata sandi minimal 4 karakter.",
            KunciRahasia.validasi(TipeKunci.KATA_SANDI, "abc"),
        )
    }

    @Test
    fun polaMinimalEmpatTitik() {
        assertNull(KunciRahasia.validasi(TipeKunci.POLA, "0-1-4-7-8"))
        assertEquals("Pola minimal 4 titik.", KunciRahasia.validasi(TipeKunci.POLA, "0-1-2"))
        assertEquals("Pola minimal 4 titik.", KunciRahasia.validasi(TipeKunci.POLA, ""))
    }

    @Test
    fun polaDuplikatDanLuarJangkauanDitolak() {
        assertNotEquals(null, KunciRahasia.validasi(TipeKunci.POLA, "0-1-1-4-7"))
        assertNotEquals(null, KunciRahasia.validasi(TipeKunci.POLA, "0-1-4-9"))
    }

    @Test
    fun validasiFormulirMenolakKonfirmasiTakCocok() {
        assertEquals(
            "Konfirmasi tidak cocok.",
            KunciRahasia.validasiFormulir(true, TipeKunci.PIN, "1234", "4321", emptyList(), emptyList()),
        )
        assertEquals(
            "Konfirmasi tidak cocok.",
            KunciRahasia.validasiFormulir(
                true, TipeKunci.POLA,
                "", "", listOf(0, 1, 4, 7), listOf(0, 1, 4, 8),
            ),
        )
    }

    @Test
    fun validasiFormulirMatiSelaluLolos() {
        assertNull(
            KunciRahasia.validasiFormulir(
                false, TipeKunci.TIDAK_ADA, "", "", emptyList(), emptyList(),
            ),
        )
        assertNull(KunciRahasia.rangkai(false, TipeKunci.PIN, "1234", emptyList(), false))
    }

    @Test
    fun rangkaiPolaMemakaiStringUrutanTitik() {
        val kunci = KunciRahasia.rangkai(true, TipeKunci.POLA, "", listOf(0, 1, 4, 7, 8), true)
        assertEquals(TipeKunci.POLA, kunci?.tipe)
        assertEquals("0-1-4-7-8", kunci?.rahasia)
        assertEquals(true, kunci?.biometrik)
    }

    // ---- perluBukaKunci ----

    @Test
    fun tanpaKunciTidakPerluBuka() {
        assertFalse(KunciRahasia.perluBukaKunci(entitasTerbuka(), emptySet()))
        assertFalse(KunciRahasia.perluBukaKunci(null, emptySet()))
    }

    @Test
    fun terkunciBelumDibukaPerluBuka() {
        assertTrue(KunciRahasia.perluBukaKunci(entitasTerkunci(), emptySet()))
    }

    @Test
    fun terkunciSetelahDibukaTidakPerluBuka() {
        val entitas = entitasTerkunci()
        assertFalse(KunciRahasia.perluBukaKunci(entitas, setOf(entitas.kunci)))
    }

    // ---- Migrasi 3 → 4 ----

    @Test
    fun migrasiTigaKeEmpatTerdaftarDenganBenar() {
        assertEquals(3, MIGRASI_3_4.startVersion)
        assertEquals(4, MIGRASI_3_4.endVersion)
    }

    // ---- Galat terstruktur vault (P2a) ----

    @Test
    fun galatButuhVaultBaruAdalahTelegramException() {
        val galat: TelegramException = GalatButuhVaultBaru("pesan uji Pengaturan")
        assertTrue(galat.message?.contains("Pengaturan") == true)
        // Penangkap lama (terjemahkanGalatForum) membiarkan pesan jelas apa adanya.
        assertEquals(galat.pesan, PenjagaVault.terjemahkanGalatForum(galat.pesan))
    }

    // ---- Regresi Build #23: terjemahkanGalatForum lama tetap lolos ----

    @Test
    fun regresiGalatForumBuild23() {
        val hasil = PenjagaVault.terjemahkanGalatForum("The chat is not a forum")
        assertFalse(hasil.contains("The chat is not a forum"))
        assertTrue(hasil.contains("Pengaturan > Vault Telegram"))
        assertEquals(
            "Jaringan bermasalah",
            PenjagaVault.terjemahkanGalatForum("Jaringan bermasalah"),
        )
    }
}
