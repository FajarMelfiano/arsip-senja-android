package com.arsipsenja.ui.viewmodel

/**
 * Satu-satunya bentuk pesan status di aplikasi.
 *
 * Sumber kebenaran nada adalah ViewModel yang membuat pesan ini — UI TIDAK
 * menebak nada dari isi teks (pola lama: substring "gagal"). Semua pesan
 * status (sukses, gagal, info) tampil lewat PopupStatus, bukan strip.
 *
 * @param aksiUlang true bila popup perlu tombol aksi kedua
 * "Buat vault baru & coba lagi" (hanya kegagalan buat koleksi yang
 * disebabkan vault rusak). Tombol itu memanggil aksi ulang ViewModel
 * yang juga menutup popup ini sendiri.
 */
enum class NadaStatus {
    SUKSES,
    GALAT,
    INFO,
}

data class PesanStatus(
    val nada: NadaStatus,
    val teks: String,
    val aksiUlang: Boolean = false,
)
