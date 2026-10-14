package com.arsipsenja.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.TipeFile
import com.arsipsenja.util.UrutBerkas

/** Hasil rakitan kueri: SQL parameterisasi + daftar argumennya. */
data class KueriDibangun(
    val sql: String,
    val arguments: List<Any?>,
) {
    /** Bungkus menjadi query yang dimengerti Room. */
    fun keSupportQuery(): SupportSQLiteQuery =
        SimpleSQLiteQuery(sql, arguments.toTypedArray())
}

/**
 * Kueri daftar berkas dasbor: pencarian as-you-type, filter tipe/koleksi, urutan.
 * Disusun sebagai SQL parameterisasi (bukan string concatenation nilai user).
 */
data class KueriTampilan(
    val kata: String? = null,
    val tipe: TipeFile? = null,
    val kunciKoleksi: KunciKoleksi? = null,
    val urut: UrutBerkas = UrutBerkas.TANGGAL,
    val arah: ArahUrut = ArahUrut.TURUN,
) {
    fun bangun(): KueriDibangun {
        val klausa = mutableListOf<String>()
        val argumen = mutableListOf<Any>()

        val kataBersih = kata?.trim()?.lowercase().orEmpty()
        if (kataBersih.isNotEmpty()) {
            klausa += "LOWER(nama) LIKE ?"
            argumen += "%$kataBersih%"
        }
        if (tipe != null) {
            klausa += "tipe = ?"
            argumen += tipe.name
        }
        if (kunciKoleksi != null) {
            klausa += "koleksiId = ?"
            argumen += kunciKoleksi.chatId
            if (kunciKoleksi.adalahTopik) {
                klausa += "topicId = ?"
                argumen += kunciKoleksi.topicId
            } else {
                // Channel lama: topicId selalu 0 — filter eksplisit agar tak
                // tercampur baris topik bila chatId suatu saat dipakai ulang.
                klausa += "topicId = 0"
            }
        }

        val kolomUrut = when (urut) {
            UrutBerkas.NAMA -> "nama COLLATE NOCASE"
            UrutBerkas.TANGGAL -> "tanggal"
            UrutBerkas.UKURAN -> "ukuran"
        }
        val arahUrut = if (arah == ArahUrut.TURUN) "DESC" else "ASC"

        val where = if (klausa.isEmpty()) "" else "WHERE " + klausa.joinToString(" AND ")
        val sql = "SELECT * FROM berkas_arsip $where " +
            "ORDER BY $kolomUrut $arahUrut, nomorKatalog DESC"
        return KueriDibangun(sql, argumen)
    }
}
