package com.arsipsenja.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.arsipsenja.util.TipeFile

/**
 * Koleksi arsip: satu channel Telegram privat (topicId = 0, kompatibel dengan
 * data lama) atau satu topik forum di dalam grup vault (topicId != 0).
 *
 * Primary key komposit (chatId, topicId) karena banyak topik berbagi
 * satu chatId vault yang sama.
 *
 * Metadata kategori kustom:
 * - [label]: teks pendek (maks 24 karakter) untuk chip, null bila kosong.
 * - [warna]: ARGB untuk dot/chip kategori, 0 = warna bawaan tema.
 * - [tipeFile]: CSV nama enum [TipeFile] yang dirutekan ke kategori ini.
 *   String kosong = menangani semua tipe (kompatibel data lama).
 *
 * Kunci koleksi (gerbang privasi lokal, BUKAN enkripsi):
 * - [kunciTipe]: nama enum [TipeKunci] (`TIDAK_ADA` = tidak dikunci).
 * - [kunciGaram]/[kunciHash]: garam + hash SHA-256 rahasia; null bila tak terkunci.
 * - [kunciBiometrik]: 1 = boleh dibuka dengan biometrik perangkat.
 *
 * Asal impor (tab pisah):
 * - [asalImpor]: true = hasil "Tarik dari Telegram", tampil HANYA di tab
 *   Impor; false = koleksi pribadi buatan pengguna, tampil di Dasbor/Pohon/
 *   Unggah. Koleksi pribadi tidak pernah diubah oleh penarikan impor.
 */
@Entity(
    tableName = "koleksi",
    primaryKeys = ["chatId", "topicId"],
)
data class KoleksiEntity(
    val chatId: Long,
    val topicId: Int = 0,
    val nama: String,
    val deskripsi: String?,
    @ColumnInfo(defaultValue = "NULL") val label: String? = null,
    @ColumnInfo(defaultValue = "0") val warna: Int = 0,
    @ColumnInfo(defaultValue = "''") val tipeFile: String = "",
    @ColumnInfo(defaultValue = "'TIDAK_ADA'") val kunciTipe: String = "TIDAK_ADA",
    @ColumnInfo(defaultValue = "NULL") val kunciGaram: String? = null,
    @ColumnInfo(defaultValue = "NULL") val kunciHash: String? = null,
    @ColumnInfo(defaultValue = "0") val kunciBiometrik: Int = 0,
    @ColumnInfo(defaultValue = "0") val asalImpor: Boolean = false,
    val jumlahBerkas: Int,
    val dibuatPada: Long,
    val terakhirSinkron: Long,
) {
    /** Identitas stabil untuk ViewModel, Worker, dan kueri. */
    val kunci: KunciKoleksi get() = KunciKoleksi(chatId, topicId)

    /** True bila koleksi ini adalah topik forum (bukan channel lama). */
    val adalahTopik: Boolean get() = topicId != 0

    /** True bila koleksi diproteksi kunci (jenis apa pun selain TIDAK_ADA). */
    val terkunci: Boolean get() = kunciTipe != TipeKunci.TIDAK_ADA.name

    /** True bila koleksi terkunci dan boleh dibuka memakai biometrik. */
    val bolehBiometrik: Boolean get() = terkunci && kunciBiometrik == 1

    /**
     * Himpunan tipe berkas yang dirutekan ke kategori ini.
     * Nilai kosong/invalid diabaikan; bila tidak ada satupun yang valid,
     * seluruh tipe ditangani (semantik "semua tipe").
     */
    val tipeDitangani: Set<TipeFile>
        get() =
            tipeFile
                .split(",")
                .mapNotNull { mentah ->
                    mentah.trim().takeIf { it.isNotEmpty() }?.let {
                        runCatching { TipeFile.valueOf(it) }.getOrNull()
                    }
                }.toSet()
                .ifEmpty { TipeFile.entries.toSet() }
}
