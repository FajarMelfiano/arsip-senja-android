package com.arsipsenja.data.repo

/**
 * Representasi kandidat grup vault untuk dipilih pengguna di Pengaturan.
 */
data class KandidatVault(
    val chatId: Long,
    val nama: String,
    /** True bila grup sudah mode forum (topik bisa dipakai langsung). */
    val forumSiap: Boolean,
    /**
     * Peran pengguna di grup ini (Paket A4 — badge Pemilik/Admin).
     * Default PEMILIK agar konstruksi lama (yang selalu owner-filtered)
     * tetap kompilasi tanpa perubahan perilaku.
     */
    val peran: PeranPenggunaChat = PeranPenggunaChat.PEMILIK,
)

/**
 * Peran pengguna di dalam obrolan Telegram.
 */
enum class PeranPenggunaChat {
    PEMILIK,
    ADMIN,
    ANGGOTA,
    TAK_DIKETAHUI,
}

/**
 * Data obrolan mentah untuk proses penyaringan kandidat vault.
 */
data class ChatKandidatMentah(
    val id: Long,
    val nama: String,
    val isSupergroup: Boolean,
    val isChannel: Boolean,
    val peran: PeranPenggunaChat,
    val forumSiap: Boolean,
)

/**
 * Aturan murni JVM untuk penyaringan kandidat vault dan deteksi otomatis.
 *
 * Prinsip keamanan (fail-closed):
 * - Hanya supergroup (bukan channel) yang boleh menjadi vault.
 * - Pengguna WAJIB menjadi pemilik atau admin.
 * - Grup milik orang lain atau yang gagal diperiksa kepemilikannya (ANGGOTA, TAK_DIKETAHUI)
 *   DIBUANG demi keamanan data pengguna.
 * - Obrolan di luar 100 teratas (dari paginasi penuh) tetap diproses dan dapat lolos saringan.
 * - Duplikasi antara hasil paginasi dan SearchChats dibersihkan berdasarkan ID obrolan.
 */
object AturanDeteksiVault {

    /**
     * Memeriksa apakah peran pengguna memenuhi kriteria pemilik/admin.
     * Menggunakan fail-closed: hanya PEMILIK dan ADMIN yang diterima.
     */
    fun adalahPemilikAtauAdmin(peran: PeranPenggunaChat): Boolean =
        peran == PeranPenggunaChat.PEMILIK || peran == PeranPenggunaChat.ADMIN

    /**
     * Saring daftar obrolan mentah menjadi kandidat vault.
     */
    fun saringKandidat(daftar: List<ChatKandidatMentah>): List<KandidatVault> {
        return daftar
            .filter { it.isSupergroup && !it.isChannel }
            .filter { adalahPemilikAtauAdmin(it.peran) }
            .distinctBy { it.id }
            .map {
                KandidatVault(
                    chatId = it.id,
                    nama = it.nama.ifBlank { "Grup ${it.id}" },
                    forumSiap = it.forumSiap,
                )
            }
    }

    /**
     * Saring daftar obrolan untuk deteksi vault otomatis siap pakai.
     * Selain syarat saringKandidat, obrolan wajib sudah forumSiap = true.
     */
    fun saringDeteksiOtomatis(daftar: List<ChatKandidatMentah>): List<KandidatVault> {
        return saringKandidat(daftar).filter { it.forumSiap }
    }
}
