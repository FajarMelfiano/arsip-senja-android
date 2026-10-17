package com.arsipsenja.data.repo

/**
 * Aturan murni jeda/lanjut otomatis antrean unggah (Paket B3).
 *
 * Unggahan dijeda otomatis bila baterai rendah (<15%) atau perangkat memakai
 * data seluler — kecuali pengguna mematikan masing-masing perilaku di
 * Pengaturan. Saat kondisi pulih (baterai cukup / kembali ke WiFi),
 * WorkManager melanjutkan otomatis karena constraint terpenuhi lagi.
 *
 * Catatan ambang baterai: Android menandai baterai rendah di sekitar 15%
 * (ACTION_BATTERY_LOW). Worker memakai [android.os.BatteryManager] untuk
 * membaca level persis; aturan ini memakai ambang yang sama agar konsisten.
 */
object AturanJedaOtomatis {

    /** Ambang baterai rendah dalam persen. */
    const val AMBANG_BATERAI_RENDAH = 15

    /** Alasan unggahan dijeda otomatis. */
    enum class Alasan {
        /** Baterai di bawah ambang dan jeda-baterai aktif. */
        BATERAI_RENDAH,

        /** Memakai data seluler dan jeda-seluler aktif. */
        DATA_SELULER,
    }

    /**
     * Tentukan apakah unggahan harus dijeda otomatis sekarang.
     *
     * @param bateraiPersen level baterai 0–100, atau null bila tak terbaca.
     * @param diDataSeluler true bila jaringan aktif adalah data seluler.
     * @param jedaBateraiAktif toggle Pengaturan "Jeda saat baterai rendah".
     * @param jedaSelulerAktif toggle Pengaturan "Jeda saat data seluler".
     * @return [Alasan] bila harus dijeda, null bila boleh jalan.
     */
    fun harusJeda(
        bateraiPersen: Int?,
        diDataSeluler: Boolean,
        jedaBateraiAktif: Boolean,
        jedaSelulerAktif: Boolean,
    ): Alasan? {
        if (jedaBateraiAktif && bateraiPersen != null && bateraiPersen < AMBANG_BATERAI_RENDAH) {
            return Alasan.BATERAI_RENDAH
        }
        if (jedaSelulerAktif && diDataSeluler) {
            return Alasan.DATA_SELULER
        }
        return null
    }

    /** Pesan Bahasa Indonesia untuk alasan jeda. */
    fun pesanAlasan(alasan: Alasan): String = when (alasan) {
        Alasan.BATERAI_RENDAH ->
            "Unggahan dijeda otomatis: baterai di bawah $AMBANG_BATERAI_RENDAH%. " +
                "Akan lanjut sendiri saat baterai cukup."
        Alasan.DATA_SELULER ->
            "Unggahan dijeda otomatis: memakai data seluler. " +
                "Akan lanjut sendiri saat tersambung WiFi."
    }
}
