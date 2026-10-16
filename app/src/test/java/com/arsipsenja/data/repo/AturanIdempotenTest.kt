package com.arsipsenja.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian unit murni JVM untuk guard idempoten:
 * - Ukuran 0 vs ukuran asli (fallback pencocokan nama + chat),
 * - Beda nama,
 * - Beda chat/koleksi,
 * - Di dalam jendela 7 hari,
 * - Di luar jendela 7 hari (> 7 hari).
 */
class AturanIdempotenTest {

    private val sekarang = 1_000_000_000_000L
    private val enamHariLalu = sekarang - (6L * 24L * 60L * 60L * 1000L)
    private val delapanHariLalu = sekarang - (8L * 24L * 60L * 60L * 1000L)

    @Test
    fun ukuranSamaDanChatSamaDalamJendelaCocok() {
        val hasil = AturanIdempoten.cocok(
            namaKandidat = "foto_liburan.jpg",
            ukuranKandidat = 1024L,
            chatIdKandidat = -100123L,
            namaTarget = "foto_liburan.jpg",
            ukuranTarget = 1024L,
            chatIdTarget = -100123L,
            waktuTarget = enamHariLalu,
            sekarang = sekarang,
        )
        assertTrue("Nama, ukuran, dan chat sama dalam jendela 7 hari harus cocok", hasil)
    }

    @Test
    fun ukuranTargetNolFallbackCocokNamaDanChat() {
        // Kasus: baris di indeks tersimpan dengan ukuran 0 B karena TDLib belum selesai ambil metadata
        val hasil = AturanIdempoten.cocok(
            namaKandidat = "dokumen.pdf",
            ukuranKandidat = 5000L,
            chatIdKandidat = -100456L,
            namaTarget = "dokumen.pdf",
            ukuranTarget = 0L,
            chatIdTarget = -100456L,
            waktuTarget = enamHariLalu,
            sekarang = sekarang,
        )
        assertTrue("Ukuran target 0 harus fallback mencocokkan nama + chat", hasil)
    }

    @Test
    fun ukuranKandidatNolFallbackCocokNamaDanChat() {
        // Kasus: berkas lokal sementara berukuran 0
        val hasil = AturanIdempoten.cocok(
            namaKandidat = "catatan.txt",
            ukuranKandidat = 0L,
            chatIdKandidat = -100789L,
            namaTarget = "catatan.txt",
            ukuranTarget = 2048L,
            chatIdTarget = -100789L,
            waktuTarget = enamHariLalu,
            sekarang = sekarang,
        )
        assertTrue("Ukuran kandidat 0 harus fallback mencocokkan nama + chat", hasil)
    }

    @Test
    fun ukuranKeduanyaNolFallbackCocokNamaDanChat() {
        val hasil = AturanIdempoten.cocok(
            namaKandidat = "video.mp4",
            ukuranKandidat = 0L,
            chatIdKandidat = -100999L,
            namaTarget = "video.mp4",
            ukuranTarget = 0L,
            chatIdTarget = -100999L,
            waktuTarget = enamHariLalu,
            sekarang = sekarang,
        )
        assertTrue("Keduanya ukuran 0 harus cocok berdasarkan nama + chat", hasil)
    }

    @Test
    fun bedaNamaTidakCocok() {
        val hasil = AturanIdempoten.cocok(
            namaKandidat = "berkas_a.zip",
            ukuranKandidat = 1024L,
            chatIdKandidat = -100123L,
            namaTarget = "berkas_b.zip",
            ukuranTarget = 1024L,
            chatIdTarget = -100123L,
            waktuTarget = enamHariLalu,
            sekarang = sekarang,
        )
        assertFalse("Beda nama tidak boleh cocok", hasil)
    }

    @Test
    fun bedaChatTidakCocok() {
        val hasil = AturanIdempoten.cocok(
            namaKandidat = "arsip.tar.gz",
            ukuranKandidat = 4096L,
            chatIdKandidat = -100111L,
            namaTarget = "arsip.tar.gz",
            ukuranTarget = 4096L,
            chatIdTarget = -100222L,
            waktuTarget = enamHariLalu,
            sekarang = sekarang,
        )
        assertFalse("Beda chatId tidak boleh cocok", hasil)
    }

    @Test
    fun bedaUkuranBilaKeduanyaPositifTidakCocok() {
        val hasil = AturanIdempoten.cocok(
            namaKandidat = "audio.mp3",
            ukuranKandidat = 1000L,
            chatIdKandidat = -100333L,
            namaTarget = "audio.mp3",
            ukuranTarget = 2000L,
            chatIdTarget = -100333L,
            waktuTarget = enamHariLalu,
            sekarang = sekarang,
        )
        assertFalse("Beda ukuran saat keduanya > 0 tidak boleh cocok", hasil)
    }

    @Test
    fun diLuarJendelaTujuhHariTidakCocok() {
        val hasil = AturanIdempoten.cocok(
            namaKandidat = "berkas_lama.docx",
            ukuranKandidat = 1024L,
            chatIdKandidat = -100444L,
            namaTarget = "berkas_lama.docx",
            ukuranTarget = 1024L,
            chatIdTarget = -100444L,
            waktuTarget = delapanHariLalu,
            sekarang = sekarang,
        )
        assertFalse("Target berumur 8 hari (> jendela default 7 hari) tidak boleh cocok", hasil)
    }

    @Test
    fun waktuMasaDepanMelebihiJendelaTidakCocok() {
        val hasil = AturanIdempoten.cocok(
            namaKandidat = "berkas.dat",
            ukuranKandidat = 1024L,
            chatIdKandidat = -100555L,
            namaTarget = "berkas.dat",
            ukuranTarget = 1024L,
            chatIdTarget = -100555L,
            waktuTarget = sekarang + (10L * 24L * 60L * 60L * 1000L),
            sekarang = sekarang,
        )
        assertFalse("Waktu aneh di masa depan melebihi jendela tidak boleh cocok", hasil)
    }
}
