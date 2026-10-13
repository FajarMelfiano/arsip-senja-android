package com.arsipsenja.data.telegram

import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.util.TipeFile
import com.google.common.truth.Truth.assertThat
import org.drinkless.tdlib.TdApi
import org.junit.Test

class PemetaTelegramTest {

    private fun berkasMentah(
        chatId: Long = -100200L,
        messageId: Long = 4242L,
        nama: String = "laporan.pdf",
        ukuran: Long = 2048L,
        tipe: TipeFile = TipeFile.DOKUMEN,
    ) = BerkasMentah(
        chatId = chatId,
        messageId = messageId,
        nama = nama,
        ukuran = ukuran,
        tanggal = 1_700_000_000_000L,
        mime = "application/pdf",
        tipe = tipe,
        tdFileId = 98765,
        tdLocalPath = null,
        caption = "Catatan singkat",
    )

    @Test
    fun `keEntity memetakan seluruh kolom indeks`() {
        val entity = berkasMentah().keEntity(
            koleksiId = -100200L,
            nomorKatalog = 24,
            sekarang = 1_700_000_100_000L,
        )

        assertThat(entity.id).isEqualTo(BerkasArsipEntity.kunci(-100200L, 4242L))
        assertThat(entity.id).isEqualTo("-100200:4242")
        assertThat(entity.chatId).isEqualTo(-100200L)
        assertThat(entity.messageId).isEqualTo(4242L)
        assertThat(entity.koleksiId).isEqualTo(-100200L)
        assertThat(entity.nomorKatalog).isEqualTo(24)
        assertThat(entity.tdFileId).isEqualTo(98765)
        assertThat(entity.tipe).isEqualTo("DOKUMEN")
        assertThat(entity.deskripsi).isEqualTo("Catatan singkat")
        assertThat(entity.dibuatPada).isEqualTo(1_700_000_100_000L)
        assertThat(entity.ukuran).isEqualTo(2048L)
    }

    @Test
    fun `caption kosong menjadi deskripsi null`() {
        val mentah = berkasMentah().copy(caption = "   ")
        val entity = mentah.keEntity(koleksiId = null, nomorKatalog = 1, sekarang = 0L)
        assertThat(entity.koleksiId).isNull()
        assertThat(entity.deskripsi).isNull()
    }

    @Test
    fun `kunci entri stabil dan unik per pesan`() {
        assertThat(BerkasArsipEntity.kunci(1L, 2L)).isEqualTo("1:2")
        assertThat(BerkasArsipEntity.kunci(1L, 3L)).isNotEqualTo("1:2")
    }

    @Test
    fun `ukuranTotal memakai expectedSize bila size nol`() {
        val kosong = TdApi.File(7, 0, 5000, null, null)
        assertThat(kosong.ukuranTotal()).isEqualTo(5000L)
        val terisi = TdApi.File(7, 4096, 9999, null, null)
        assertThat(terisi.ukuranTotal()).isEqualTo(4096L)
        val nihil = TdApi.File(7, 0, 0, null, null)
        assertThat(nihil.ukuranTotal()).isEqualTo(0L)
    }

    @Test
    fun `progres persen menghitung naik dan tuntas`() {
        val berjalan = ProgresBerkas(
            fileId = 1,
            path = "/tmp/a",
            ukuran = 1000L,
            terkirim = 250L,
            terunduh = 0L,
            selesaiUnggah = false,
            selesaiUnduh = false,
        )
        assertThat(berjalan.persenUnggah).isEqualTo(25)
        assertThat(berjalan.persenUnduh).isEqualTo(0)

        val tuntas = berjalan.copy(terkirim = 1000L, selesaiUnggah = true)
        assertThat(tuntas.persenUnggah).isEqualTo(100)

        val tanpaUkuran = berjalan.copy(ukuran = 0L, terkirim = 0L)
        assertThat(tanpaUkuran.persenUnggah).isEqualTo(-1)
    }
}
