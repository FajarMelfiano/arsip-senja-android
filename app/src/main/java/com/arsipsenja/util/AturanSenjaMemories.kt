package com.arsipsenja.util

import com.arsipsenja.data.local.BerkasArsipEntity
import java.util.Calendar
import java.util.TimeZone

/**
 * Logika murni untuk menyaring berkas yang merupakan kenangan Senja:
 * berkas yang tanggalnya (hari dan bulan) sama dengan hari ini,
 * tetapi dari tahun-tahun sebelumnya (bukan tahun berjalan).
 *
 * Murni Kotlin/JVM tanpa dependensi Android — dapat diuji unit terpisah.
 */
object AturanSenjaMemories {

    /**
     * Mengembalikan berkas yang tanggalnya jatuh pada hari dan bulan yang sama
     * dengan hari ini di [zonaWaktu], tetapi tahunnya lebih kecil dari tahun ini.
     * Diurutkan dari yang terbaru ke terlama.
     */
    fun filtering(
        semuaBerkas: List<BerkasArsipEntity>,
        zonaWaktu: TimeZone,
    ): List<BerkasArsipEntity> {
        if (semuaBerkas.isEmpty()) return emptyList()

        val kini = Calendar.getInstance(zonaWaktu)
        val hariIni = kini.get(Calendar.DAY_OF_MONTH)
        val bulanIni = kini.get(Calendar.MONTH) // 0-based: Januari = 0
        val tahunIni = kini.get(Calendar.YEAR)

        return semuaBerkas
            .filter { berkas ->
                val cal = Calendar.getInstance(zonaWaktu).apply {
                    timeInMillis = berkas.tanggal
                }
                cal.get(Calendar.DAY_OF_MONTH) == hariIni &&
                    cal.get(Calendar.MONTH) == bulanIni &&
                    cal.get(Calendar.YEAR) < tahunIni
            }
            .sortedByDescending { it.tanggal }
    }

    /**
     * Tanggal hari ini dalam format tampil ringkas Bahasa Indonesia,
     * mis. "16 Okt". Murni format manual agar konsisten di semua perangkat.
     */
    fun formatTanggalHariIni(zonaWaktu: TimeZone): String {
        val kini = Calendar.getInstance(zonaWaktu)
        val hari = kini.get(Calendar.DAY_OF_MONTH)
        val bulan = kini.get(Calendar.MONTH) // 0-based
        val namaBulan =
            arrayOf(
                "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
                "Jul", "Agu", "Sep", "Okt", "Nov", "Des",
            )
        return "$hari ${namaBulan[bulan]}"
    }
}
