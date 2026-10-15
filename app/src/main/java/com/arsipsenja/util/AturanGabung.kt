package com.arsipsenja.util

import com.arsipsenja.data.local.BerkasArsipEntity

/**
 * Logika murni untuk pengurutan dan verifikasi kelengkapan potongan berkas besar.
 *
 * Fungsi utama:
 * - Mengurutkan potongan berdasarkan indeks potongan (naik).
 * - Memastikan semua potongan berasal dari kelompok yang sama ([grupId] konsisten).
 * - Memastikan [jumlahPotongan] konsisten dan cocok dengan jumlah potongan nyata.
 * - Memastikan tidak ada celah dalam indeks (harus 1, 2, 3, ..., N).
 * - Memastikan semua potongan memiliki hash Sha256 yang sama (asal file asli).
 *
 * Semua fungsi ini murni dan dapat diuji terpisah tanpa mengandalkan Android framework.
 */
object AturanGabung {

    /**
     * Mengurutkan dan memverifikasi kelengkapan daftar potongan.
     *
     * @param potongan Daftar entitas potongan yang masing-masing memiliki [indeksPotongan] dan [hashSha256].
     * @return Pasangan berisi daftar potongan yang sudah terurut dan hash Sha256 yang bersama-sama.
     * @throws IllegalArgumentException Jika daftar potongan kosong, ada celah dalam indeks,
     *                                  atau hash Sha256 tidak konsisten antar potongan.
     */
    fun urutanDanHash(potongan: List<BerkasArsipEntity>): Pair<List<BerkasArsipEntity>, String> {
        if (potongan.isEmpty()) {
            throw IllegalArgumentException("Daftar potongan tidak boleh kosong")
        }

        // Semua potongan harus berasal dari satu kelompok yang sama.
        val grupPertama = potongan[0].grupId
        if (grupPertama.isNullOrBlank()) {
            throw IllegalArgumentException("grupId potongan pertama tidak boleh kosong")
        }
        potongan.forEach { p ->
            if (p.grupId != grupPertama) {
                throw IllegalArgumentException(
                    "grupId tidak konsisten: diharapkan $grupPertama, " +
                        "tetapi potongan ${p.indeksPotongan} memiliki ${p.grupId}",
                )
            }
        }

        // jumlahPotongan harus konsisten dan cocok dengan jumlah nyata.
        val jumlahPertama = potongan[0].jumlahPotongan
        if (jumlahPertama <= 0) {
            throw IllegalArgumentException("jumlahPotongan harus positif, got $jumlahPertama")
        }
        potongan.forEach { p ->
            if (p.jumlahPotongan != jumlahPertama) {
                throw IllegalArgumentException(
                    "jumlahPotongan tidak konsisten: diharapkan $jumlahPertama, " +
                        "tetapi potongan ${p.indeksPotongan} memiliki ${p.jumlahPotongan}",
                )
            }
        }
        if (potongan.size != jumlahPertama) {
            throw IllegalArgumentException(
                "Jumlah potongan nyata ${potongan.size} tidak cocok dengan " +
                    "jumlahPotongan $jumlahPertama",
            )
        }

        // Urutkan berdasarkan indeks potongan
        val terurut = potongan.sortedBy { it.indeksPotongan }

        // Verifikasi bahwa indeks mulai dari 1 dan bertambah 1 setiap langkah
        val indeksTerurut = terurut.map { it.indeksPotongan }
        val expectedIndeks = (1..terurut.size).toList()
        if (indeksTerurut != expectedIndeks) {
            val hilang = expectedIndeks.filterNot { indeksTerurut.contains(it) }
            throw IllegalArgumentException("Ada celah dalam indeks potongan: $hilang")
        }

        // Verifikasi bahwa semua potongan memiliki hash Sha256 yang sama dan tidak kosong
        val hashPertama = terurut[0].hashSha256
        if (hashPertama.isNullOrBlank()) {
            throw IllegalArgumentException("Hash Sha256 potongan pertama tidak boleh kosong")
        }
        for (p in terurut) {
            if (p.hashSha256 != hashPertama) {
                throw IllegalArgumentException("Hash Sha256 tidak konsisten: diharapkan $hashPertama, tetapi potongan ${p.indeksPotongan} memiliki ${p.hashSha256}")
            }
        }

        return Pair(terurut, hashPertama)
    }
}