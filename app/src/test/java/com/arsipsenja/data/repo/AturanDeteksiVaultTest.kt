package com.arsipsenja.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian unit JVM murni untuk AturanDeteksiVault:
 * - Deteksi mencakup supergroup milik sendiri (owner/admin) di luar 100 obrolan teratas
 * - Grup milik orang lain atau gagal periksa hak ditolak (fail-closed)
 * - Channel atau grup biasa (bukan supergroup) ditolak
 * - Deduplikasi antara paginasi dan pencarian
 * - Saringan deteksi otomatis hanya meloloskan grup yang sudah forum-siap
 */
class AturanDeteksiVaultTest {

    private fun buatChat(
        id: Long,
        nama: String = "Grup $id",
        isSupergroup: Boolean = true,
        isChannel: Boolean = false,
        peran: PeranPenggunaChat = PeranPenggunaChat.PEMILIK,
        forumSiap: Boolean = true,
    ): ChatKandidatMentah = ChatKandidatMentah(
        id = id,
        nama = nama,
        isSupergroup = isSupergroup,
        isChannel = isChannel,
        peran = peran,
        forumSiap = forumSiap,
    )

    @Test
    fun supergroupMilikSendiriDiLuar100TeratasLolos() {
        val daftar = mutableListOf<ChatKandidatMentah>()
        // Buat 100 obrolan awal (mis. chat pribadi atau grup orang lain)
        for (i in 1..100) {
            daftar += buatChat(
                id = i.toLong(),
                nama = "Obrolan $i",
                peran = PeranPenggunaChat.ANGGOTA,
            )
        }

        // Obrolan ke-105: supergroup milik sendiri (PEMILIK) di luar 100 teratas
        val vaultLama105 = buatChat(
            id = 105L,
            nama = "Arsip Senja Vault Lama",
            peran = PeranPenggunaChat.PEMILIK,
            forumSiap = true,
        )
        daftar += vaultLama105

        // Obrolan ke-115: supergroup di mana pengguna adalah ADMIN di luar 100 teratas
        val vaultAdmin115 = buatChat(
            id = 115L,
            nama = "Vault Kantor",
            peran = PeranPenggunaChat.ADMIN,
            forumSiap = false,
        )
        daftar += vaultAdmin115

        val kandidat = AturanDeteksiVault.saringKandidat(daftar)

        assertEquals("Tepat 2 grup pemilik/admin di luar 100 teratas yang lolos", 2, kandidat.size)
        assertTrue("Vault ke-105 harus ditemukan", kandidat.any { it.chatId == 105L })
        assertTrue("Vault ke-115 harus ditemukan", kandidat.any { it.chatId == 115L })
    }

    @Test
    fun grupOrangLainDitolakFailClosed() {
        val anggotaBiasa = buatChat(id = 1L, peran = PeranPenggunaChat.ANGGOTA)
        val gagalPeriksa = buatChat(id = 2L, peran = PeranPenggunaChat.TAK_DIKETAHUI)
        val channelMilikSendiri = buatChat(id = 3L, isChannel = true, peran = PeranPenggunaChat.PEMILIK)
        val grupBiasaBukanSupergroup = buatChat(id = 4L, isSupergroup = false, peran = PeranPenggunaChat.PEMILIK)

        val kandidat = AturanDeteksiVault.saringKandidat(
            listOf(anggotaBiasa, gagalPeriksa, channelMilikSendiri, grupBiasaBukanSupergroup),
        )

        assertTrue("Grup orang lain, channel, atau grup biasa tidak boleh lolos saringan kandidat", kandidat.isEmpty())
    }

    @Test
    fun duplikasiAntaraPaginasiDanPencarianDibersihkan() {
        val dariPaginasi = buatChat(id = 1001L, nama = "Arsip Senja")
        val dariPencarian = buatChat(id = 1001L, nama = "Arsip Senja")

        val kandidat = AturanDeteksiVault.saringKandidat(listOf(dariPaginasi, dariPencarian))

        assertEquals("Deduplikasi harus menghasilkan tepat 1 kandidat", 1, kandidat.size)
        assertEquals(1001L, kandidat.first().chatId)
    }

    @Test
    fun deteksiOtomatisHanyaMeloloskanYangForumSiap() {
        val belumForum = buatChat(id = 1L, nama = "Grup Biasa", forumSiap = false)
        val sudahForum = buatChat(id = 2L, nama = "Grup Forum", forumSiap = true)

        val semuaKandidat = AturanDeteksiVault.saringKandidat(listOf(belumForum, sudahForum))
        assertEquals("Saring kandidat umum meloloskan keduanya", 2, semuaKandidat.size)

        val otomatis = AturanDeteksiVault.saringDeteksiOtomatis(listOf(belumForum, sudahForum))
        assertEquals("Deteksi otomatis hanya meloloskan grup yang forumSiap", 1, otomatis.size)
        assertEquals(2L, otomatis.first().chatId)
    }
}
