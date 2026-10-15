package com.arsipsenja.util

import com.arsipsenja.data.local.BerkasArsipEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Unit test untuk [AturanGabung].
 * Konvensi: JUnit4 + Truth (tanpa AssertJ).
 */
class AturanGabungTest {

    private fun potongan(
        indeks: Int,
        jumlah: Int = 3,
        hash: String? = "abc",
        grupId: String? = "grup1",
    ) = BerkasArsipEntity(
        id = "p$indeks",
        messageId = indeks.toLong(),
        chatId = 1L,
        koleksiId = null,
        nama = "file.part$indeks",
        ukuran = 100,
        tanggal = 0,
        mime = "application/octet-stream",
        tipe = "BINARY",
        tdFileId = indeks,
        tdLocalPath = null,
        deskripsi = null,
        dibuatPada = 0,
        hashSha256 = hash,
        grupId = grupId,
        indeksPotongan = indeks,
        jumlahPotongan = jumlah,
        namaAsli = "file",
        ukuranTotal = 300,
    )

    @Test
    fun `potongan valid diurutkan dan hash dikembalikan`() {
        val acak = listOf(potongan(3), potongan(1), potongan(2))

        val (terurut, hash) = AturanGabung.urutanDanHash(acak)

        assertThat(terurut.map { it.indeksPotongan }).containsExactly(1, 2, 3).inOrder()
        assertThat(hash).isEqualTo("abc")
    }

    @Test
    fun `celah indeks melempar dengan nomor yang hilang`() {
        val potongan = listOf(potongan(1), potongan(3))

        val galat = assertThrows(IllegalArgumentException::class.java) {
            AturanGabung.urutanDanHash(potongan)
        }
        assertThat(galat).hasMessageThat().contains("Ada celah dalam indeks potongan")
        assertThat(galat).hasMessageThat().contains("2")
    }

    @Test
    fun `indeks duplikat melempar`() {
        val potongan = listOf(potongan(1), potongan(1), potongan(2))

        assertThrows(IllegalArgumentException::class.java) {
            AturanGabung.urutanDanHash(potongan)
        }
    }

    @Test
    fun `hash tak konsisten melempar dengan kedua nilai`() {
        val potongan = listOf(potongan(1, hash = "abc"), potongan(2, hash = "def"))

        val galat = assertThrows(IllegalArgumentException::class.java) {
            AturanGabung.urutanDanHash(potongan)
        }
        assertThat(galat).hasMessageThat().contains("Hash Sha256 tidak konsisten")
        assertThat(galat).hasMessageThat().contains("abc")
        assertThat(galat).hasMessageThat().contains("def")
    }

    @Test
    fun `daftar kosong melempar`() {
        val galat = assertThrows(IllegalArgumentException::class.java) {
            AturanGabung.urutanDanHash(emptyList())
        }
        assertThat(galat).hasMessageThat().contains("Daftar potongan tidak boleh kosong")
    }

    @Test
    fun `hash kosong melempar`() {
        val potongan = listOf(potongan(1, jumlah = 1, hash = ""))

        val galat = assertThrows(IllegalArgumentException::class.java) {
            AturanGabung.urutanDanHash(potongan)
        }
        assertThat(galat).hasMessageThat().contains("Hash Sha256 potongan pertama tidak boleh kosong")
    }

    @Test
    fun `satu potongan tunggal lolos`() {
        val (terurut, hash) = AturanGabung.urutanDanHash(listOf(potongan(1, jumlah = 1)))
        assertThat(terurut).hasSize(1)
        assertThat(hash).isEqualTo("abc")
    }

    @Test
    fun `grupId tak konsisten melempar`() {
        val potongan = listOf(
            potongan(1, grupId = "grup1"),
            potongan(2, grupId = "grup2"),
            potongan(3, grupId = "grup1"),
        )

        val galat = assertThrows(IllegalArgumentException::class.java) {
            AturanGabung.urutanDanHash(potongan)
        }
        assertThat(galat).hasMessageThat().contains("grupId tidak konsisten")
    }

    @Test
    fun `jumlahPotongan tak konsisten melempar`() {
        val potongan = listOf(
            potongan(1, jumlah = 3),
            potongan(2, jumlah = 4),
            potongan(3, jumlah = 3),
        )

        val galat = assertThrows(IllegalArgumentException::class.java) {
            AturanGabung.urutanDanHash(potongan)
        }
        assertThat(galat).hasMessageThat().contains("jumlahPotongan tidak konsisten")
    }

    @Test
    fun `jumlah nyata tak cocok jumlahPotongan melempar`() {
        // DB hanya punya 2 baris padahal jumlahPotongan = 3
        val potongan = listOf(potongan(1, jumlah = 3), potongan(2, jumlah = 3))

        val galat = assertThrows(IllegalArgumentException::class.java) {
            AturanGabung.urutanDanHash(potongan)
        }
        assertThat(galat).hasMessageThat().contains("tidak cocok dengan jumlahPotongan")
    }
}
