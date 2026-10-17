package com.arsipsenja.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Log operasi mutasi yang tertunda disinkronkan ke Telegram (Paket B1).
 *
 * Setiap mutasi yang mengubah status di Telegram (rename/pindah/hapus
 * berkas, buat/hapus/rename koleksi, kunci/buka koleksi) dicatat dulu
 * sebagai PENDING, dijalankan, lalu ditandai SELESAI. Bila eksekusi gagal
 * karena jaringan, entri tetap PENDING dan [SinkronOperasiWorker]
 * menerapkannya ulang saat koneksi pulih. Entri bersifat idempoten:
 * penerapan ulang operasi yang sama tidak menimbulkan efek ganda.
 *
 * [payloadJson] menyimpan parameter operasi sebagai JSON sederhana
 * (pasangan kunci=nilai dipisah ';'), cukup untuk membangun ulang
 * pemanggilan tanpa dependensi library JSON tambahan.
 */
@Entity(tableName = "operasi_tertunda")
data class OperasiTertundaEntity(
    /** UUID unik per mutasi — juga kunci idempotensi. */
    @PrimaryKey val id: String,
    /** Salah satu dari [JenisOperasi]. */
    val jenis: String,
    /** ID target: berkasId untuk berkas, "chatId:topicId" untuk koleksi. */
    val targetId: String,
    /** Parameter operasi, format "kunci=nilai;kunci=nilai". */
    val payloadJson: String = "",
    /** PENDING / SELESAI / GAGAL / DIBATALKAN. */
    val status: String = StatusOperasi.PENDING,
    /** Milis epoch saat operasi dibuat — juga timestamp untuk LWW. */
    val dibuatPada: Long = 0L,
    /** Jumlah percobaan penerapan ulang. */
    val percobaan: Int = 0,
)

/** Nilai [OperasiTertundaEntity.jenis] yang didukung. */
object JenisOperasi {
    const val UBAH_KOLEKSI = "UBAH_KOLEKSI"
    const val HAPUS_KOLEKSI = "HAPUS_KOLEKSI"
    const val BUAT_KOLEKSI = "BUAT_KOLEKSI"
    const val KUNCI_KOLEKSI = "KUNCI_KOLEKSI"
    const val HAPUS_BERKAS = "HAPUS_BERKAS"
}

/** Nilai [OperasiTertundaEntity.status] yang didukung. */
object StatusOperasi {
    const val PENDING = "PENDING"
    const val SELESAI = "SELESAI"
    const val GAGAL = "GAGAL"

    /** Dibatalkan karena kalah konflik melawan pembaruan remote. */
    const val DIBATALKAN = "DIBATALKAN"
}
