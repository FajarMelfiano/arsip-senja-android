package com.arsipsenja.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import com.arsipsenja.util.ArahUrut
import com.arsipsenja.util.TipeFile
import com.arsipsenja.util.UrutBerkas

/**
 * Kueri daftar berkas dasbor: pencarian as-you-type, filter tipe/koleksi, urutan.
 * Disusun sebagai SQL parameterisasi (bukan string concatenation nilai user).
 */
data class KueriTampilan(
    val kata: String? = null,
    val tipe: TipeFile? = null,
    val koleksiId: Long? = null,
    val urut: UrutBerkas = UrutBerkas.TANGGAL,
    val arah: ArahUrut = ArahUrut.TURUN,
) {
    fun bangun(): SimpleSQLiteQuery {
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
        if (koleksiId != null) {
            klausa += "koleksiId = ?"
            argumen += koleksiId
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
        return SimpleSQLiteQuery(sql, argumen.toTypedArray())
    }
}
