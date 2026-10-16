package com.arsipsenja.data.repo

import com.arsipsenja.data.local.BerkasArsipEntity

/**
 * Representasi satu kelompok berkas duplikat yang memiliki
 * (koleksiId, topicId, nama, ukuran) yang identik dengan jumlah > 1.
 */
data class GrupDuplikat(
    val koleksiId: Long,
    val topicId: Int,
    val nama: String,
    val ukuran: Long,
    val tanggalTerbaru: Long,
    val berkas: List<BerkasArsipEntity>,
) {
    /** Jumlah rangkap dalam kelompok ini. */
    val jumlahRangkap: Int get() = berkas.size

    /** Satu berkas yang disimpan saat perapian (nomorKatalog terkecil). */
    val yangDipertahankan: BerkasArsipEntity get() = berkas.first()

    /** Baris-baris duplikat yang akan dihapus saat perapian. */
    val yangDihapus: List<BerkasArsipEntity> get() = berkas.drop(1)
}

/**
 * Logika murni untuk identifikasi dan perapian berkas duplikat di indeks.
 */
object AturanDuplikat {

    /**
     * Mengelompokkan berkas berdasarkan (koleksiId, topicId, nama, ukuran).
     * Berkas dengan ukuran <= 0 B (metadata belum siap/tak tentu) DIKECUALIKAN
     * untuk mencegah false-positive perapian.
     * Hanya kelompok dengan jumlah rangkap > 1 yang dihasilkan.
     * Dalam tiap kelompok, berkas diurutkan berdasarkan [BerkasArsipEntity.nomorKatalog] terkecil.
     */
    fun kelompokkan(daftar: List<BerkasArsipEntity>): List<GrupDuplikat> {
        return daftar
            .filter { it.ukuran > 0L }
            .groupBy { Triple(it.koleksiId ?: it.chatId, it.topicId, it.nama to it.ukuran) }
            .filter { (_, grup) -> grup.size > 1 }
            .map { (kunci, grup) ->
                val urut = grup.sortedWith(
                    compareBy<BerkasArsipEntity> { it.nomorKatalog }
                        .thenBy { it.dibuatPada }
                        .thenBy { it.id },
                )
                GrupDuplikat(
                    koleksiId = kunci.first,
                    topicId = kunci.second,
                    nama = kunci.third.first,
                    ukuran = kunci.third.second,
                    tanggalTerbaru = urut.maxOf { it.tanggal },
                    berkas = urut,
                )
            }
            .sortedByDescending { it.tanggalTerbaru }
    }

    /**
     * Memisahkan berkas ke dalam dua daftar:
     * - `first`: satu berkas per grup yang dipertahankan (nomorKatalog terkecil),
     * - `second`: seluruh baris duplikat lainnya yang akan dihapus.
     */
    fun pisahkanUntukRapikan(grup: List<GrupDuplikat>): Pair<List<BerkasArsipEntity>, List<BerkasArsipEntity>> {
        val dipertahankan = grup.map { it.yangDipertahankan }
        val dihapus = grup.flatMap { it.yangDihapus }
        return dipertahankan to dihapus
    }
}
