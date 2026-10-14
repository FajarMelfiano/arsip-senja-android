package com.arsipsenja.data.repo

/**
 * Aturan murni (tanpa Android/TDLib) untuk validasi dan pemulihan vault.
 *
 * Dipisah dari [ArsipRepository] agar bisa diuji unit murni di JVM:
 * seluruh keputusan di sini hanya memakai String/Boolean, tanpa GetChat
 * atau DAO. Repository bertugas mengambil fakta (tipe chat, jumlah koleksi)
 * lalu mendelegasikan keputusannya ke objek ini.
 */
object PenjagaVault {

    /** Status tipe chat vault hasil pemeriksaan GetChat. */
    sealed interface StatusVault {
        /** Supergroup biasa (bukan channel): satu-satunya vault yang sah. */
        data object Sah : StatusVault

        /** Chat bertipe lain (channel, grup biasa, privat, ...). */
        data class BukanSupergroup(val labelTipe: String) : StatusVault

        /** GetChat gagal sehingga tipe tak bisa dipastikan. */
        data object TakDiketahui : StatusVault
    }

    /** Keputusan pemulihan bila vault terbukti bukan supergroup. */
    sealed interface Keputusan {
        /** Vault basi tanpa koleksi: hapus yang lama, buat vault baru otomatis. */
        data object BuangDanBuatBaru : Keputusan

        /** Ada koleksi terdaftar: vault lama dipertahankan + galat jelas. */
        data class Tolak(val pesan: String) : Keputusan
    }

    /**
     * True bila pesan galat TDLib berarti "chat ini bukan forum"
     * (mis. dari GetForumTopics/CreateForumTopic pada grup non-forum).
     * Pesan "Forums can be enabled in supergroups only" SENGAJA tidak
     * termasuk: itu berarti chatnya BUKAN supergroup, sehingga memanggil
     * ToggleSupergroupIsForum pasti gagal lagi.
     */
    fun galatBukanForum(pesan: String): Boolean {
        if (galatHanyaSupergroup(pesan)) return false
        return pesan.contains("not a forum", ignoreCase = true) ||
            pesan.contains("not forum", ignoreCase = true) ||
            pesan.contains("CHAT_NOT_FORUM", ignoreCase = true) ||
            pesan.contains("forum", ignoreCase = true)
    }

    /** True bila pesan galat berarti "hanya berlaku untuk supergroup". */
    fun galatHanyaSupergroup(pesan: String): Boolean =
        pesan.contains("supergroup", ignoreCase = true)

    /** Galat jelas untuk vault bertipe salah yang masih dipakai koleksi. */
    fun pesanVaultBukanSupergroup(labelTipe: String): String =
        "Vault yang tersimpan bukan grup (tipe: $labelTipe), sehingga " +
            "topik tidak bisa dibuat. Pilih grup lain atau buat vault baru " +
            "di Pengaturan > Vault Telegram."

    /** Galat aktivasi forum untuk supergroup asli (biasanya soal hak pemilik). */
    fun pesanAktivasiGagal(pesanAsli: String): String =
        "Vault belum mode forum dan aktivasi otomatis gagal " +
            "($pesanAsli). Pastikan kamu pemilik grup, lalu " +
            "pilih ulang vault di Pengaturan."

    /**
     * Putuskan pemulihan vault bukan-supergroup.
     *
     * @param adaKoleksiTerdaftar true bila ada koleksi di indeks lokal yang
     * memakai vault ini (cek via koleksiDao oleh pemanggil).
     */
    fun putuskanVaultBukanSupergroup(
        labelTipe: String,
        adaKoleksiTerdaftar: Boolean,
    ): Keputusan =
        if (adaKoleksiTerdaftar) {
            Keputusan.Tolak(pesanVaultBukanSupergroup(labelTipe))
        } else {
            Keputusan.BuangDanBuatBaru
        }
}
