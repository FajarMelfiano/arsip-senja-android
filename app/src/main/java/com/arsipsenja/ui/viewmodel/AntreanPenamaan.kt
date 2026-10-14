package com.arsipsenja.ui.viewmodel

import com.arsipsenja.data.local.KoleksiEntity
import com.arsipsenja.data.local.KunciBaru
import com.arsipsenja.data.local.KunciKoleksi
import com.arsipsenja.util.TipeFile

/**
 * Satu item antrean popup penamaan koleksi.
 *
 * Satu popup dalam satu waktu; antrean diproses berurutan. Dua asal:
 * - BUAT: [tipe] terisi, [kunci] null — tiap Simpan membuat SATU topik +
 *   SATU koleksi untuk tipe itu, memakai pengaturan bersama
 *   ([deskripsi], [label], [warna], [kunciRahasia]).
 * - TARIK: [kunci] terisi, [tipe] null — tiap Simpan mengganti nama koleksi
 *   hasil tarikan itu (field lain dipertahankan); Lewati mempertahankan
 *   nama topik/chat dari Telegram.
 *
 * [namaAbaikan] = nama yang BOLEH dipakai meski ada di [namaTerpakai]:
 * untuk Tarik, nama entri itu sendiri (Simpan tanpa mengubah nama sah);
 * untuk Buat, null (prefill hanya saran label tipe, bukan nama entitas).
 */
data class ItemPenamaan(
    val tipe: TipeFile? = null,
    val kunci: KunciKoleksi? = null,
    val prefill: String,
    val konteks: String,
    val namaAbaikan: String? = null,
    val deskripsi: String = "",
    val label: String = "",
    val warna: Int = 0,
    val kunciRahasia: KunciBaru? = null,
)

/**
 * Logika murni antrean penamaan + ringkasan — tanpa Compose/TDLib,
 * bisa diuji unit di JVM.
 */
object AntreanPenamaan {

    /**
     * True bila [nama] sudah dipakai koleksi lain (di luar [abaikan]).
     *
     * [abaikan] hanya menghapus SATU kemunculan nama itu dari hitungan:
     * bila dua koleksi sudah bernama sama (mis. dua topik "Gambar" hasil
     * Tarik yang keduanya dilewati), nama itu tetap duplikat untuk entri
     * kedua — satu kemunculan adalah nama entri itu sendiri, sisanya
     * milik koleksi lain.
     */
    fun namaDuplikat(nama: String, terpakai: List<String>, abaikan: String?): Boolean {
        val bersih = nama.trim()
        if (bersih.isEmpty()) return false
        val jumlah = terpakai.count { it.equals(bersih, ignoreCase = true) }
        val jatahAbaikan =
            if (abaikan != null && abaikan.equals(bersih, ignoreCase = true)) 1 else 0
        return jumlah > jatahAbaikan
    }

    /**
     * Entri hasil tarikan yang BARU tercakup: kuncinya belum dikenal
     * SEBELUM tarik. Perbandingan memakai kunci (chatId, topicId),
     * BUKAN nama — topik bernama sama dengan koleksi lain tetap baru,
     * koleksi yang namanya berubah di Telegram tidak ditanya ulang.
     */
    fun baruTercakup(
        sebelum: Set<KunciKoleksi>,
        sesudah: List<KoleksiEntity>,
    ): List<KoleksiEntity> = sesudah.filter { it.kunci !in sebelum }

    /** Item penamaan untuk entri baru hasil tarikan. */
    fun dariTarik(barangBaru: List<KoleksiEntity>): List<ItemPenamaan> =
        barangBaru.map { entri ->
            val konteks = if (entri.adalahTopik) {
                "Topik “${entri.nama}” dari Telegram belum punya koleksi."
            } else {
                "Channel “${entri.nama}” dari Telegram belum punya koleksi."
            }
            ItemPenamaan(
                kunci = entri.kunci,
                prefill = entri.nama,
                konteks = konteks,
                namaAbaikan = entri.nama,
            )
        }

    /** Item penamaan untuk pembuatan koleksi, urutan [TipeFile.entries]. */
    fun dariBuat(
        tipeDipilih: Set<TipeFile>,
        deskripsi: String,
        label: String,
        warna: Int,
        kunci: KunciBaru?,
    ): List<ItemPenamaan> =
        TipeFile.entries.filter { it in tipeDipilih }.map { tipe ->
            ItemPenamaan(
                tipe = tipe,
                prefill = tipe.label,
                konteks = "Topik baru di vault",
                deskripsi = deskripsi,
                label = label,
                warna = warna,
                kunciRahasia = kunci,
            )
        }

    /** Ringkasan sesudah antrean Buat habis. */
    fun ringkasanBuat(dibuat: Int, gagal: Int): PesanStatus = when {
        dibuat == 0 && gagal == 0 ->
            PesanStatus(NadaStatus.INFO, "Tidak ada koleksi dibuat.")
        gagal == 0 ->
            PesanStatus(NadaStatus.SUKSES, "$dibuat koleksi dibuat.")
        dibuat == 0 ->
            PesanStatus(NadaStatus.GALAT, "$gagal koleksi gagal dibuat.")
        else ->
            PesanStatus(NadaStatus.GALAT, "$dibuat koleksi dibuat, $gagal gagal.")
    }

    /** Ringkasan sesudah antrean Tarik habis. */
    fun ringkasanTarik(total: Int, gagal: Int, sufiks: String = ""): PesanStatus =
        if (gagal == 0) {
            PesanStatus(NadaStatus.SUKSES, "$total koleksi impor diperbarui$sufiks.")
        } else {
            PesanStatus(
                NadaStatus.GALAT,
                "$total koleksi impor diperbarui$sufiks, $gagal gagal dinamai.",
            )
        }
}
