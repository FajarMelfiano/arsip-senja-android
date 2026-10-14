package com.arsipsenja.ui.pohon

import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.util.TipeFile

/**
 * Pohon arsip turunan (tanpa skema baru): Vault (akar) → Koleksi (kategori) →
 * Tipe berkas → Berkas.
 *
 * Murni Kotlin (tanpa Android/Compose) agar bisa diuji unit di JVM. Status
 * buka/tutup TIDAK disimpan di sini — itu urusan ViewModel.
 */
sealed interface SimpulPohon {
    val idSimpul: String
    val nama: String
    val jumlahBerkas: Int
    val totalUkuran: Long
    val anak: List<SimpulPohon>
}

/** Akar pohon: ringkasan seluruh vault. */
data class SimpulAkar(
    override val anak: List<SimpulPohon> = emptyList(),
    override val jumlahBerkas: Int = 0,
    override val totalUkuran: Long = 0L,
) : SimpulPohon {
    override val idSimpul: String = "akar"
    override val nama: String = "Vault"
}

/**
 * Satu koleksi. [koleksi] null berarti kelompok "Tanpa kategori": berkas
 * yang tidak punya koleksi yang cocok (koleksiId null / kunci tak ditemukan).
 */
data class SimpulKoleksi(
    val koleksi: KoleksiEntity?,
    override val nama: String,
    override val idSimpul: String,
    override val anak: List<SimpulPohon> = emptyList(),
    override val jumlahBerkas: Int = 0,
    override val totalUkuran: Long = 0L,
) : SimpulPohon {
    /** True bila ini kelompok "Tanpa kategori", bukan koleksi nyata. */
    val yatim: Boolean get() = koleksi == null
}

/** Satu tipe berkas di dalam sebuah koleksi. */
data class SimpulTipe(
    val tipe: TipeFile,
    override val idSimpul: String,
    override val anak: List<SimpulPohon> = emptyList(),
    override val jumlahBerkas: Int = 0,
    override val totalUkuran: Long = 0L,
) : SimpulPohon {
    override val nama: String = tipe.label
}

/** Satu berkas (daun pohon). */
data class SimpulBerkas(
    val berkas: BerkasArsipEntity,
) : SimpulPohon {
    override val idSimpul: String = "berkas:${berkas.id}"
    override val nama: String = berkas.nama
    override val jumlahBerkas: Int = 1
    override val totalUkuran: Long = berkas.ukuran
    override val anak: List<SimpulPohon> = emptyList()
}

/**
 * Hasil pembangunan pohon + ringkasan akar untuk header layar.
 * Saat [kataCari] aktif, ringkasan mencerminkan cabang yang terlihat
 * (koleksi/tipe kosong karena filter disembunyikan).
 */
data class PohonArsip(
    val akar: SimpulAkar,
    val jumlahKoleksi: Int,
    val jumlahBerkas: Int,
    val totalUkuran: Long,
) {
    companion object {
        fun kosong() = PohonArsip(SimpulAkar(), 0, 0, 0L)
    }
}

/** Tipe berkas dari nilai [BerkasArsipEntity.tipe]; nilai invalid → LAINNYA. */
fun tipeDariBerkas(berkas: BerkasArsipEntity): TipeFile =
    runCatching { TipeFile.valueOf(berkas.tipe) }.getOrDefault(TipeFile.LAINNYA)

/**
 * Bangun pohon dari koleksi + berkas yang ada.
 *
 * - Pengelompokan berkas ke koleksi memakai (koleksiId, topicId) yang cocok
 *   dengan kunci koleksi; sisanya masuk "Tanpa kategori".
 * - Filter [kataCari] berdasarkan nama berkas (case-insensitive); leluhur
 *   simpul yang cocok tetap terlihat.
 * - Urutan: koleksi sesuai urutan daftar masuk, tipe sesuai urutan
 *   [TipeFile.entries], berkas urut nama (case-insensitive).
 */
fun bangunPohon(
    koleksi: List<KoleksiEntity>,
    berkas: List<BerkasArsipEntity>,
    kataCari: String = "",
): PohonArsip {
    val cari = kataCari.trim().lowercase()
    val tersaring = if (cari.isEmpty()) {
        berkas
    } else {
        berkas.filter { it.nama.lowercase().contains(cari) }
    }

    val perKunci = koleksi.associateBy { it.chatId to it.topicId }
    val isiKoleksi = mutableMapOf<KoleksiEntity, MutableList<BerkasArsipEntity>>()
    val yatim = mutableListOf<BerkasArsipEntity>()
    tersaring.forEach { b ->
        val induk = b.koleksiId?.let { perKunci[it to b.topicId] }
        if (induk == null) {
            yatim += b
        } else {
            isiKoleksi.getOrPut(induk) { mutableListOf() } += b
        }
    }

    fun anakTipe(daftar: List<BerkasArsipEntity>, awalanId: String): List<SimpulTipe> =
        TipeFile.entries.mapNotNull { tipe ->
            val isi = daftar.filter { tipeDariBerkas(it) == tipe }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.nama })
            if (isi.isEmpty()) {
                null
            } else {
                SimpulTipe(
                    tipe = tipe,
                    idSimpul = "$awalanId/tipe:${tipe.name}",
                    anak = isi.map { SimpulBerkas(it) },
                    jumlahBerkas = isi.size,
                    totalUkuran = isi.sumOf { it.ukuran },
                )
            }
        }

    val simpulKoleksi = koleksi.mapNotNull { k ->
        val isi = isiKoleksi[k].orEmpty()
        // Koleksi kosong karena filter disembunyikan; tanpa filter,
        // koleksi 0 berkas tetap muncul (dengan 0 anak).
        if (cari.isNotEmpty() && isi.isEmpty()) return@mapNotNull null
        val id = "koleksi:${k.chatId}:${k.topicId}"
        SimpulKoleksi(
            koleksi = k,
            nama = k.nama,
            idSimpul = id,
            anak = anakTipe(isi, id),
            jumlahBerkas = isi.size,
            totalUkuran = isi.sumOf { it.ukuran },
        )
    }
    val simpulYatim = if (yatim.isEmpty()) {
        null
    } else {
        val id = "koleksi:yatim"
        SimpulKoleksi(
            koleksi = null,
            nama = "Tanpa kategori",
            idSimpul = id,
            anak = anakTipe(yatim, id),
            jumlahBerkas = yatim.size,
            totalUkuran = yatim.sumOf { it.ukuran },
        )
    }

    val anak = simpulKoleksi + listOfNotNull(simpulYatim)
    val akar = SimpulAkar(
        anak = anak,
        jumlahBerkas = anak.sumOf { it.jumlahBerkas },
        totalUkuran = anak.sumOf { it.totalUkuran },
    )
    return PohonArsip(
        akar = akar,
        jumlahKoleksi = simpulKoleksi.size,
        jumlahBerkas = akar.jumlahBerkas,
        totalUkuran = akar.totalUkuran,
    )
}
