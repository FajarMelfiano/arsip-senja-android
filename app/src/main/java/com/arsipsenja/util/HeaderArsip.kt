package com.arsipsenja.util

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

/**
 * Header metadata arsip yang ditanam pada baris pertama caption pesan Telegram.
 *
 * ## Kenapa header ini ada
 *
 * Dulu metadata arsip hanya hidup di Room (database lokal). Karena backup
 * Android dimatikan, uninstall berarti metadata hilang padahal byte-nya masih
 * utuh di Telegram. Header ini memindahkan sumber kebenaran **ke dalam
 * Telegram itu sendiri**, sehingga katalog dapat dibangun ulang dari nol:
 *
 * - `h` — SHA-256 isi berkas → verifikasi integritas, deteksi duplikat,
 *   dan content-addressed storage (nama berkas = bukti isi).
 * - `g` — UUID kelompok → mengikat potongan `part1/part2/...` menjadi satu
 *   berkas logis, dan membuat berkas punya "grup".
 * - `i` — `indeks/jumlah` → urutan & kelengkapan potongan.
 * - `n` — nama asli berkas (URL-encoded) → dapat dipulihkan utuh walau nama
 *   pesan Telegram sudah diubah atau tidak cocok.
 * - `s` — ukuran total → dapat dihitung ulang tiap potongan untuk
 *   mendeteksi potongan yang hilang.
 *
 * ## Kompatibilitas
 *
 * Pesan lama tanpa header akan menghasilkan [urai] = null dan seluruh jalur
 * kode lama tetap berfungsi. Header sengaja ditulis agar field baru dapat
 * ditambahkan tanpa merusak parser.
 *
 * Format (baris pertama caption):
 * ```
 * AS1 h=<64 hex> g=<uuid> i=<idx>/<total> n=<encoded> s=<byte>
 * <caption bebas milik pengguna>
 * ```
 */
data class HeaderArsip(
    /** SHA-256 isi berkas dalam hex huruf kecil; null bila tidak diketahui. */
    val hashSha256: String? = null,
    /** UUID kelompok berkas; sama untuk semua potongan dari satu berkas. */
    val grupId: String? = null,
    /** Indeks potongan mulai 1; null bila berkas tidak dipecah. */
    val indeksPotongan: Int? = null,
    /** Total potongan berkas asli; null bila tidak dipecah. */
    val jumlahPotongan: Int? = null,
    /** Nama berkas asli sebelum dipecah. */
    val namaAsli: String? = null,
    /** Ukuran total berkas asli dalam byte. */
    val ukuranTotal: Long? = null,
) {
    /** Header ini berasal dari berkas hasil pemecahan? */
    val terpecah: Boolean get() = jumlahPotongan != null && jumlahPotongan > 1

    /** Header ini punya hash untuk needs integritas? */
    val punyaHash: Boolean get() = hashSha256 != null

    companion object {
        /** Penanda baris pertama caption. */
        const val SENTINEL = "AS1"

        /** Panjang SHA-256 dalam hex (32 byte). */
        const val PANJANG_HASH = 64

        /**
         * Header untuk berkas yang tidak dipecah.
         *
         * @param hashSha256 hash isi berkas; null bila belum dihitung.
         * @param namaAsli nama berkas asli.
         * @param ukuranTotal ukuran berkas dalam byte.
         */
        fun utuh(
            hashSha256: String?,
            namaAsli: String,
            ukuranTotal: Long,
        ): HeaderArsip = HeaderArsip(
            hashSha256 = hashSha256,
            grupId = null,
            indeksPotongan = null,
            jumlahPotongan = null,
            namaAsli = namaAsli,
            ukuranTotal = ukuranTotal,
        )

        /**
         * Header untuk satu potongan dari berkas yang dipecah.
         *
         * @param grupId UUID kelompok; dibuat otomatis bila null.
         */
        fun potongan(
            grupId: String?,
            indeks: Int,
            jumlah: Int,
            namaAsli: String,
            ukuranTotal: Long,
            hashPotongan: String? = null,
        ): HeaderArsip {
            require(indeks >= 1) { "indeks potongan mulai dari 1, bukan $indeks" }
            require(jumlah >= 1) { "jumlah potongan minimal 1, bukan $jumlah" }
            require(indeks <= jumlah) { "indeks $indeks melebihi jumlah $jumlah" }
            return HeaderArsip(
                hashSha256 = hashPotongan,
                grupId = grupId ?: UUID.randomUUID().toString(),
                indeksPotongan = indeks,
                jumlahPotongan = jumlah,
                namaAsli = namaAsli,
                ukuranTotal = ukuranTotal,
            )
        }
    }
}

/** Pola hash SHA-256 hex huruf kecil. */
private val POLA_HASH = Regex("^[0-9a-f]{${HeaderArsip.PANJANG_HASH}}$")

/** Pola UUID (versi 1–5). */
private val POLA_UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/** Pola satu pasangan `kunci=nilai` pada header. */
private val POLA_TOKEN = Regex("^([a-z])=(\\S*)$")

/**
 * Rakit baris header. Nilai yang null/hanya-kosong otomatis dilewati sehingga
 * header tidak pernah memuat `g=` kosong.
 *
 * @param barisCaptionBaru caption pengguna yang akan menyusul header (opsional).
 * @return caption lengkap: header + baris kosong + caption pengguna.
 */
fun HeaderArsip.susun(barisCaptionBaru: String? = null): String {
    require(namaAsli == null || namaAsli.isNotBlank()) { "namaAsli tidak boleh kosong" }
    val token = buildList {
        add(HeaderArsip.SENTINEL)
        hashSha256?.takeIf { POLA_HASH.matches(it) }?.let { add("h=$it") }
        grupId?.takeIf { POLA_UUID.matches(it) }?.let { add("g=$it") }
        if (terpecah && indeksPotongan != null && jumlahPotongan != null) {
            add("i=$indeksPotongan/$jumlahPotongan")
        }
        namaAsli?.let { add("n=${URLEncoder.encode(it, "UTF-8")}") }
        ukuranTotal?.takeIf { it >= 0 }?.let { add("s=$it") }
    }
    val ekorCaption = barisCaptionBaru?.trim().orEmpty()
    return if (ekorCaption.isEmpty()) token.joinToString(" ") else token.joinToString(" ") + "\n\n" + ekorCaption
}

/**
 * Baca header dari caption pesan Telegram.
 *
 * Parsing bersifat **lenient**: field yang rusak hanya membuat field itu null,
 * bukan menggagalkan seluruh parse. Header dari versi berikutnya (ada kunci
 * asing) tetap akan terbaca.
 *
 * @return header, atau null bila caption ini bukan caption Arsip Senja.
 */
fun uraiHeaderArsip(caption: String?): HeaderArsip? {
    val baris = caption?.lineSequence()?.firstOrNull()?.trim().orEmpty()
    if (baris.isEmpty()) return null
    val token = baris.split(' ')
    if (token.firstOrNull() != HeaderArsip.SENTINEL) return null

    var hash: String? = null
    var grup: String? = null
    var indeks: Int? = null
    var jumlah: Int? = null
    var nama: String? = null
    var ukuran: Long? = null

    for (bagian in token.drop(1)) {
        val cocok = POLA_TOKEN.matchEntire(bagian) ?: continue
        when (cocok.groupValues[1]) {
            "h" -> hash = cocok.groupValues[2].takeIf { POLA_HASH.matches(it) }
            "g" -> grup = cocok.groupValues[2].takeIf { POLA_UUID.matches(it) }
            "i" -> {
                val bagianI = cocok.groupValues[2].split('/')
                if (bagianI.size == 2) {
                    val i = bagianI[0].toIntOrNull()
                    val j = bagianI[1].toIntOrNull()
                    // Tolak i=0 dan j=0: keduanya tidak mungkin hasil pemecahan.
                    if (i != null && j != null && i >= 1 && j >= 1 && i <= j) {
                        indeks = i
                        jumlah = j
                    }
                }
            }
            "n" -> nama = runCatching {
                URLDecoder.decode(cocok.groupValues[2], "UTF-8")
            }.getOrNull()?.takeIf { it.isNotBlank() }
            "s" -> ukuran = cocok.groupValues[2].toLongOrNull()?.takeIf { it >= 0 }
        }
    }
    return HeaderArsip(hash, grup, indeks, jumlah, nama, ukuran)
}

/**
 * Ambil caption pengguna saja — header sudah dibuang.
 *
 * Mengembalikan null bila caption hanya berisi header, sehingga pemanggil bisa
 * membedakan "tanpa caption" dari "caption kosong".
 */
fun captionTanpaHeader(caption: String?): String? {
    if (caption.isNullOrBlank()) return null
    if (uraiHeaderArsip(caption) == null) return caption
    return caption.lineSequence().drop(1).joinToString("\n").trim().takeIf { it.isNotEmpty() }
}