package com.arsipsenja.data.local

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Jenis kunci pengaman per koleksi.
 *
 * POLA disimpan sebagai string urutan titik, mis. "0-1-4-7-8"
 * (indeks grid 3x3 dari kiri-atas), lalu di-hash seperti rahasia lain.
 */
enum class TipeKunci {
    TIDAK_ADA,
    PIN,
    KATA_SANDI,
    POLA,
}

/** Kunci baru yang diminta user saat buat/ubah koleksi (rahasia plaintext sesaat). */
data class KunciBaru(
    val tipe: TipeKunci,
    val rahasia: String,
    val biometrik: Boolean = false,
)

/**
 * Helper murni (tanpa Android/TDLib) untuk kunci koleksi — bisa diuji unit di JVM.
 *
 * CATATAN JUJUR: kunci ini adalah gerbang privasi lokal, BUKAN enkripsi.
 * Berkas di Telegram dan baris database tetap bisa dibaca bila perangkat
 * di-root atau database disalin. Jangan pernah mengklaim data "terenkripsi".
 * Rahasia plaintext TIDAK PERNAH disimpan — hanya garam + hash SHA-256.
 */
object KunciRahasia {

    /** Garam acak 16 byte per koleksi, dikodekan Base64. */
    fun buatGaram(): String {
        val acak = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().withoutPadding().encodeToString(acak)
    }

    /** Hash SHA-256 hex dari (garam + rahasia). Jangan pakai hash tanpa garam. */
    fun hash(rahasia: String, garam: String): String {
        val cerna = MessageDigest.getInstance("SHA-256")
        val gabung = (garam + rahasia).toByteArray(Charsets.UTF_8)
        return cerna.digest(gabung).joinToString("") { "%02x".format(it) }
    }

    /** True bila [rahasia] cocok dengan pasangan garam+hash tersimpan. */
    fun verifikasi(rahasia: String, garam: String, hashTersimpan: String): Boolean {
        if (garam.isBlank() || hashTersimpan.isBlank()) return false
        val hitung = hash(rahasia, garam)
        return MessageDigest.isEqual(
            hitung.toByteArray(Charsets.UTF_8),
            hashTersimpan.toByteArray(Charsets.UTF_8),
        )
    }

    /**
     * Validasi rahasia untuk [tipe].
     *
     * @return null bila valid, atau pesan Bahasa Indonesia spesifik bila tidak.
     */
    fun validasi(tipe: TipeKunci, rahasia: String): String? = when (tipe) {
        TipeKunci.TIDAK_ADA -> "Pilih jenis kunci dulu (PIN, kata sandi, atau pola)."
        TipeKunci.PIN ->
            if (rahasia.length in 4..6 && rahasia.all { it.isDigit() }) {
                null
            } else {
                "PIN harus 4–6 angka."
            }
        TipeKunci.KATA_SANDI ->
            if (rahasia.length >= 4) {
                null
            } else {
                "Kata sandi minimal 4 karakter."
            }
        TipeKunci.POLA -> validasiPola(rahasia)
    }

    private fun validasiPola(rahasia: String): String? {
        if (rahasia.isBlank()) return "Pola minimal 4 titik."
        val titik = rahasia.split("-").map { it.toIntOrNull() ?: return "Pola tidak valid." }
        if (titik.size < 4) return "Pola minimal 4 titik."
        if (titik.any { it !in 0..8 }) return "Pola hanya memakai titik 0–8."
        if (titik.toSet().size != titik.size) return "Titik pola tidak boleh diulang."
        return null
    }

    /** Rangkai daftar titik pola (indeks 0–8) menjadi string rahasia. */
    fun polaKeRahasia(titik: List<Int>): String = titik.joinToString("-")

    /** Urai string rahasia pola menjadi daftar titik; tak valid → daftar kosong. */
    fun rahasiaKePola(rahasia: String): List<Int> =
        runCatching {
            rahasia.split("-").map { it.toInt() }.takeIf { it.all { t -> t in 0..8 } }.orEmpty()
        }.getOrDefault(emptyList())

    /**
     * Validasi satu formulir kunci lengkap (dipakai form buat & ubah koleksi).
     *
     * @return null bila valid / kunci dimatikan, atau pesan galat spesifik.
     */
    fun validasiFormulir(
        diaktifkan: Boolean,
        tipe: TipeKunci,
        rahasia: String,
        konfirmasi: String,
        pola: List<Int>,
        polaUlang: List<Int>,
    ): String? {
        if (!diaktifkan) return null
        if (tipe == TipeKunci.TIDAK_ADA) return "Pilih jenis kunci dulu (PIN, kata sandi, atau pola)."
        return if (tipe == TipeKunci.POLA) {
            val awal = polaKeRahasia(pola)
            validasi(tipe, awal)?.let { return it }
            if (polaUlang.isEmpty()) return "Ulangi polamu untuk konfirmasi."
            if (polaKeRahasia(polaUlang) != awal) return "Konfirmasi tidak cocok."
            null
        } else {
            validasi(tipe, rahasia)?.let { return it }
            if (konfirmasi.isEmpty()) return "Ketik ulang untuk konfirmasi."
            if (konfirmasi != rahasia) return "Konfirmasi tidak cocok."
            null
        }
    }

    /**
     * Rangkai [KunciBaru] dari formulir yang SUDAH lolos [validasiFormulir];
     * null bila kunci dimatikan.
     */
    fun rangkai(
        diaktifkan: Boolean,
        tipe: TipeKunci,
        rahasia: String,
        pola: List<Int>,
        biometrik: Boolean,
    ): KunciBaru? {
        if (!diaktifkan) return null
        val rahasiaAkhir = if (tipe == TipeKunci.POLA) polaKeRahasia(pola) else rahasia
        return KunciBaru(tipe, rahasiaAkhir, biometrik)
    }

    /**
     * True bila koleksi terkunci DAN belum dibuka sesi ini.
     * Murni (tanpa DAO) agar mudah diuji: pemanggil memberi entity + set terbuka.
     */
    fun perluBukaKunci(entitas: KoleksiEntity?, terbuka: Set<KunciKoleksi>): Boolean {
        if (entitas == null || !entitas.terkunci) return false
        return entitas.kunci !in terbuka
    }
}
