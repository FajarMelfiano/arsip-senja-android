package com.arsipsenja.data.repo

/**
 * Status mesin sinkron otomatis untuk UI.
 *
 * Batasan jujur: TDLib client hanya hidup saat proses aplikasi berjalan —
 * realtime penuh saat aplikasi dibuka; saat aplikasi dimatikan total,
 * sinkron tertunda sampai aplikasi dibuka lagi. Kalimat ini juga tampil
 * di dialog progres agar tidak ada klaim realtime 24 jam.
 */
data class StatusRealtime(
    /** True bila mesin realtime sudah dimulai (sekali per proses). */
    val aktif: Boolean = false,
    /** True bila TDLib melaporkan ConnectionStateReady. */
    val tersambung: Boolean = false,
    /** True bila sebuah sinkron (manual maupun otomatis) sedang berjalan. */
    val menyinkronkan: Boolean = false,
    /** Waktu sinkron terakhir yang berhasil (epoch ms); null bila belum pernah. */
    val sinkronTerakhir: Long? = null,
    /** Jumlah pesan baru yang masuk indeks via jalur realtime. */
    val pesanDiproses: Long = 0,
    /** Jumlah pesan yang diabaikan (chat tak dikenal / bukan berkas). */
    val pesanDiabaikan: Long = 0,
    /** Jumlah pesan yang dilewati karena duplikat. */
    val duplikatDilewati: Long = 0,
    /** Berapa kali sinkron otomatis (koneksi pulih / foreground) berjalan. */
    val sinkronOtomatis: Long = 0,
)
