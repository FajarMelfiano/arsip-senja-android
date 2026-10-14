package com.arsipsenja.data.local

/**
 * Aturan murni (tanpa Android/Room) untuk pemisahan koleksi pribadi vs impor.
 *
 * Dipisah dari [com.arsipsenja.data.repo.ArsipRepository] agar bisa diuji
 * unit murni di JVM: seluruh keputusan hanya memakai data class, tanpa DAO.
 *
 * Aturan:
 * - Koleksi yang SUDAH dikenal sebagai pribadi (kunci sama, asalImpor=false)
 *   TIDAK boleh duplikat/timpa menjadi impor — pribadi selalu menang.
 * - Entri baru atau yang memang bertanda impor boleh masuk tab Impor.
 */
object PenandaImpor {

    /**
     * True bila hasil tarikan boleh dicatat sebagai koleksi impor.
     * [lama] adalah entitas lokal dengan kunci yang sama, null bila baru.
     */
    fun bolehJadiImpor(lama: KoleksiEntity?): Boolean =
        lama == null || lama.asalImpor

    /** Tandai entri tarikan sebagai impor tanpa mengubah field lain. */
    fun tandaiImpor(entri: KoleksiEntity): KoleksiEntity =
        entri.copy(asalImpor = true)

    /**
     * Himpunan kunci (chatId, topicId) milik koleksi impor — dipakai
     * ViewModel untuk menyaring berkas impor dari katalog pribadi.
     */
    fun kunciImpor(daftar: List<KoleksiEntity>): Set<Pair<Long, Int>> =
        daftar.filter { it.asalImpor }.map { it.chatId to it.topicId }.toSet()

    /** True bila berkas milik salah satu koleksi impor. */
    fun milikImpor(berkas: BerkasArsipEntity, kunci: Set<Pair<Long, Int>>): Boolean =
        ((berkas.koleksiId ?: berkas.chatId) to berkas.topicId) in kunci
}
