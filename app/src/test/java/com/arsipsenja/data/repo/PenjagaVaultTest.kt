package com.arsipsenja.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aturan pemulihan vault (kasus error "Forums can be enabled in
 * supergroups only"):
 * a. vault bukan supergroup + tanpa koleksi → ganti otomatis,
 * b. vault bukan supergroup + ada koleksi → galat jelas, vault dipertahankan,
 * c. supergroup belum forum → aktivasi otomatis + coba lagi (regresi Build #21).
 *
 * Aturan diuji lewat [PenjagaVault] murni (tanpa TDLib/DAO) — keputusan yang
 * sama dipakai [ArsipRepository] saat runtime.
 */
class PenjagaVaultTest {

    // ---- a. vault basi tanpa koleksi → buang dan buat baru otomatis ----

    @Test
    fun vaultBukanSupergroupTanpaKoleksiDigantiOtomatis() {
        val putusan = PenjagaVault.putuskanVaultBukanSupergroup(
            labelTipe = "channel",
            adaKoleksiTerdaftar = false,
        )
        assertEquals(PenjagaVault.Keputusan.BuangDanBuatBaru, putusan)
    }

    // ---- b. vault bukan supergroup + ada koleksi → galat jelas ----

    @Test
    fun vaultBukanSupergroupDenganKoleksiDitolakDenganPesanJelas() {
        val putusan = PenjagaVault.putuskanVaultBukanSupergroup(
            labelTipe = "channel",
            adaKoleksiTerdaftar = true,
        )
        assertTrue(putusan is PenjagaVault.Keputusan.Tolak)
        val pesan = (putusan as PenjagaVault.Keputusan.Tolak).pesan
        // Menyebut masalah sebenarnya (tipe salah), bukan kepemilikan grup.
        assertTrue(pesan.contains("bukan grup"))
        assertTrue(pesan.contains("channel"))
        // Langkah konkret yang bisa ditindaklanjuti.
        assertTrue(pesan.contains("Pengaturan > Vault Telegram"))
        assertFalse(pesan.contains("pemilik"))
    }

    @Test
    fun pesanVaultBukanSupergroupBerbahasaIndonesia() {
        val pesan = PenjagaVault.pesanVaultBukanSupergroup("grup biasa")
        assertTrue(pesan.contains("grup biasa"))
        assertTrue(pesan.contains("topik tidak bisa dibuat"))
    }

    // ---- c. regresi Build #21: supergroup belum forum → aktivasi + coba lagi ----

    @Test
    fun galatBukanForumMemicuAktivasiOtomatis() {
        // Pesan TDLib saat GetForumTopics/CreateForumTopic di grup non-forum.
        assertTrue(PenjagaVault.galatBukanForum("The chat is not a forum"))
        assertTrue(PenjagaVault.galatBukanForum("CHAT_NOT_FORUM"))
    }

    @Test
    fun galatHanyaSupergroupBukanPemicuAktivasi() {
        // Error dari laporan 14 Okt 2026: vault BUKAN supergroup. Memanggil
        // ToggleSupergroupIsForum untuk ini pasti gagal lagi, jadi jalur
        // aktivasi otomatis TIDAK boleh dipakai.
        val pesan = "Forums can be enabled in supergroups only"
        assertTrue(PenjagaVault.galatHanyaSupergroup(pesan))
        assertFalse(PenjagaVault.galatBukanForum(pesan))
    }

    @Test
    fun pesanAktivasiGagalTetapMenyebutHakPemilikUntukSupergroupAsli() {
        // Supergroup asli yang gagal diaktifkan = soal izin → pesan pemilik
        // tetap benar di jalur ini.
        val pesan = PenjagaVault.pesanAktivasiGagal("CHAT_NOT_MODIFIED")
        assertTrue(pesan.contains("pemilik grup"))
        assertTrue(pesan.contains("Pengaturan"))
    }

    // ---- d. pertahanan terakhir: tak ada pesan mentah "not a forum" di UI ----

    @Test
    fun galatNotAForumDiterjemahkanKeBahasaIndonesia() {
        val hasil = PenjagaVault.terjemahkanGalatForum("The chat is not a forum")
        assertFalse(hasil.contains("The chat is not a forum"))
        assertTrue(hasil.contains("belum menjadi forum"))
        assertTrue(hasil.contains("Pengaturan > Vault Telegram"))
    }

    @Test
    fun galatHanyaSupergroupDiterjemahkanKePesanVault() {
        val hasil = PenjagaVault.terjemahkanGalatForum(
            "Forums can be enabled in supergroups only",
        )
        assertFalse(hasil.contains("Forums can be enabled"))
        assertTrue(hasil.contains("bukan grup"))
        assertTrue(hasil.contains("Pengaturan > Vault Telegram"))
    }

    @Test
    fun galatBukanSupergroupLokalDiterjemahkanKePesanVault() {
        val hasil = PenjagaVault.terjemahkanGalatForum("Bukan chat supergroup: -123")
        assertFalse(hasil.contains("Bukan chat supergroup: -123"))
        assertTrue(hasil.contains("bukan grup"))
        assertTrue(hasil.contains("tak diketahui"))
    }

    @Test
    fun pesanNonForumLolosApaAdanya() {
        assertEquals(
            "Jaringan bermasalah",
            PenjagaVault.terjemahkanGalatForum("Jaringan bermasalah"),
        )
    }

    @Test
    fun pesanForumMasihGagalMenyebutPemilikDanVaultTelegram() {
        val pesan = PenjagaVault.pesanForumMasihGagal()
        assertTrue(pesan.contains("pemilik grup"))
        assertTrue(pesan.contains("Pengaturan > Vault Telegram"))
    }

    @Test
    fun pesanSudahJelasTidakDiubahLagi() {
        val jelas = PenjagaVault.pesanVaultBukanSupergroup("channel")
        assertEquals(jelas, PenjagaVault.terjemahkanGalatForum(jelas))
        val aktivasi = PenjagaVault.pesanAktivasiGagal("CHAT_NOT_MODIFIED")
        assertEquals(aktivasi, PenjagaVault.terjemahkanGalatForum(aktivasi))
    }

    // ---- Auto-detect vault milik sendiri (Build #26) ----

    private val calonDua = listOf(
        PenjagaVault.CalonVault(-1001, "Grup Lain"),
        PenjagaVault.CalonVault(-1002, "Arsip Senja"),
    )

    @Test
    fun adopsiNamaCocokMenangAtasYangLain() {
        val adopsi = PenjagaVault.pilihAdopsi(calonDua, "Arsip Senja")
        assertEquals(PenjagaVault.CalonVault(-1002, "Arsip Senja"), adopsi)
    }

    @Test
    fun adopsiSatuSatunyaKandidatTanpaNamaCocok() {
        val adopsi = PenjagaVault.pilihAdopsi(
            listOf(PenjagaVault.CalonVault(-1003, "Arsip Lama")),
            "Arsip Senja",
        )
        assertEquals(PenjagaVault.CalonVault(-1003, "Arsip Lama"), adopsi)
    }

    @Test
    fun adopsiBeberapaKandidatTanpaNamaCocokMenolakMenebak() {
        val calon = listOf(
            PenjagaVault.CalonVault(-1004, "Grup A"),
            PenjagaVault.CalonVault(-1005, "Grup B"),
        )
        assertEquals(null, PenjagaVault.pilihAdopsi(calon, "Arsip Senja"))
    }

    @Test
    fun adopsiTanpaKandidatMenghasilkanNull() {
        assertEquals(null, PenjagaVault.pilihAdopsi(emptyList(), "Arsip Senja"))
    }

    @Test
    fun cariNamaSamaTidakPeduliKapitalisasiDanSpasi() {
        val ketemu = PenjagaVault.cariNamaSama(calonDua, "  arsip SENJA ")
        assertEquals(PenjagaVault.CalonVault(-1002, "Arsip Senja"), ketemu)
        assertEquals(null, PenjagaVault.cariNamaSama(calonDua, "Tidak Ada"))
    }

    @Test
    fun adopsiVaultLamaHanyaBilaTidakAdaKoleksiBertopik() {
        assertTrue(PenjagaVault.bolehAdopsiVaultLama(emptyList()))
        assertTrue(PenjagaVault.bolehAdopsiVaultLama(listOf(0, 0)))
        assertFalse(PenjagaVault.bolehAdopsiVaultLama(listOf(0, 7)))
        assertFalse(PenjagaVault.bolehAdopsiVaultLama(listOf(3)))
    }

    @Test
    fun pesanDeteksiDanDedupeMenyebutNamaVault() {
        assertTrue(
            PenjagaVault.pesanAdopsiOtomatis("Arsip Senja")
                .contains("terdeteksi otomatis"),
        )
        assertTrue(
            PenjagaVault.pesanVaultSudahAda("Arsip Senja")
                .contains("sudah ada, dipakai lagi"),
        )
        assertTrue(
            PenjagaVault.pesanBanyakKandidat(3)
                .contains("3 grup milikmu"),
        )
    }

    @Test
    fun grupBaruSahHanyaSupergroupBukanChannel() {
        assertTrue(PenjagaVault.grupBaruSah(isSupergroup = true, isChannel = false))
        assertFalse(PenjagaVault.grupBaruSah(isSupergroup = true, isChannel = true))
        assertFalse(PenjagaVault.grupBaruSah(isSupergroup = false, isChannel = false))
        assertFalse(PenjagaVault.grupBaruSah(isSupergroup = false, isChannel = true))
    }

    @Test
    fun pesanBuatVaultChannelMenyebutTipeDanJalurManual() {
        val pesan = PenjagaVault.pesanBuatVaultChannel("channel")
        assertTrue(pesan.contains("channel"))
        assertTrue(pesan.contains("bukan grup"))
        assertTrue(pesan.contains("manual"))
    }
}
