package com.arsipsenja.data.local

import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.TipeFile
import com.arsipsenja.util.UrutBerkas
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KueriTampilanTest {

    @Test
    fun `tanpa saringan hanya urutan`() {
        val q = KueriTampilan().bangun()
        assertThat(q.sql)
            .isEqualTo("SELECT * FROM berkas_arsip  ORDER BY tanggal DESC, nomorKatalog DESC")
        assertThat(q.arguments).isEmpty()
    }

    @Test
    fun `kata kunci diturunkan ke lower like parameterisasi`() {
        val q = KueriTampilan(kata = "  Foto Senja ").bangun()
        assertThat(q.sql).startsWith("SELECT * FROM berkas_arsip WHERE LOWER(nama) LIKE ?")
        assertThat(q.arguments).containsExactly("%foto senja%")
    }

    @Test
    fun `filter tipe dan koleksi digabung and`() {
        val q = KueriTampilan(
            tipe = TipeFile.VIDEO,
            kunciKoleksi = KunciKoleksi(chatId = -100123L),
            urut = UrutBerkas.UKURAN,
            arah = ArahUrut.NAIK,
        ).bangun()
        assertThat(q.sql).isEqualTo(
            "SELECT * FROM berkas_arsip WHERE tipe = ? AND koleksiId = ? AND topicId = 0 " +
                "ORDER BY ukuran ASC, nomorKatalog DESC",
        )
        assertThat(q.arguments).containsExactly("VIDEO", -100123L).inOrder()
    }

    @Test
    fun `filter koleksi topik memakai chatId dan topicId`() {
        val q = KueriTampilan(
            kunciKoleksi = KunciKoleksi(chatId = -100456L, topicId = 3),
        ).bangun()
        assertThat(q.sql).isEqualTo(
            "SELECT * FROM berkas_arsip WHERE koleksiId = ? AND topicId = ? " +
                "ORDER BY tanggal DESC, nomorKatalog DESC",
        )
        assertThat(q.arguments).containsExactly(-100456L, 3).inOrder()
    }

    @Test
    fun `urutan nama memakai collate nocase`() {
        val q = KueriTampilan(urut = UrutBerkas.NAMA, arah = ArahUrut.TURUN).bangun()
        assertThat(q.sql).contains("ORDER BY nama COLLATE NOCASE DESC, nomorKatalog DESC")
    }

    @Test
    fun `kata kosong tidak menyisakan where`() {
        val q = KueriTampilan(kata = "   ").bangun()
        assertThat(q.sql).doesNotContain("WHERE")
    }
}
