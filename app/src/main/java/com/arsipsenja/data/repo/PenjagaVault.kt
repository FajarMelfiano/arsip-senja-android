package com.arsipsenja.data.repo

import com.arsipsenja.data.telegram.TelegramException

/**
 * Galat vault terstruktur: vault rusak tetapi dipakai koleksi lain sehingga
 * TIDAK boleh diganti otomatis. UI menangkap tipe ini (bukan sekadar pesan)
 * untuk menampilkan tombol aksi "Buat vault baru & coba lagi".
 *
 * Subkelas [TelegramException] agar penangkap galat lama tetap berfungsi —
 * pesannya sudah Bahasa Indonesia dan menyebut "Pengaturan".
 */
class GalatButuhVaultBaru(
    pesan: String,
    sebab: Throwable? = null,
) : TelegramException(pesan, 0, sebab)

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
     * Galat bila aktivasi forum "berhasil" tetapi Telegram tetap menjawab
     * bukan-forum (propagasi / forum belum efektif). Bukan soal jaringan —
     * pengguna perlu memastikan kepemilikan lalu memilih ulang vault.
     */
    fun pesanForumMasihGagal(): String =
        "Vault masih belum menjadi forum setelah aktivasi otomatis. " +
            "Pastikan kamu pemilik grup, lalu pilih ulang vault di " +
            "Pengaturan > Vault Telegram atau buat vault baru."

    /**
     * Terjemahkan galat mentah (TDLib Inggris / validasi lokal) menjadi
     * pesan Bahasa Indonesia yang jelas + bisa ditindaklanjuti.
     * Pertahanan terakhir sebelum teks sampai ke UI: pesan non-forum
     * dikembalikan apa adanya, pesan Build #22 yang sudah jelas tidak
     * diubah (terdeteksi dari penanda "Pengaturan").
     */
    fun terjemahkanGalatForum(pesan: String): String {
        // Sudah diterjemahkan (pesan Build #22 / pesanForumMasihGagal):
        // semuanya menyebut "Pengaturan", yang tak pernah ada di teks TDLib.
        if (pesan.contains("Pengaturan")) return pesan
        // Validasi lokal grupIdDariChat — chatId bukan supergroup.
        if (pesan.startsWith("Bukan chat supergroup:")) {
            return pesanVaultBukanSupergroup("tak diketahui")
        }
        if (galatHanyaSupergroup(pesan)) {
            return pesanVaultBukanSupergroup("tak diketahui")
        }
        if (galatBukanForum(pesan)) {
            return pesanForumMasihGagal()
        }
        return pesan
    }

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

    // ---- Auto-detect vault milik sendiri (Build #26) ----

    /** Kandidat vault untuk auto-detect: supergroup milik sendiri, forum-siap. */
    data class CalonVault(val chatId: Long, val nama: String)

    /**
     * True bila vault lama yang tidak sah boleh diganti otomatis oleh
     * auto-adopt. Aman hanya bila tidak ada koleksi yang memakai vault
     * lama itu, atau semua koleksinya bertopicId == 0 (koleksi channel
     * lama yang tidak hidup di dalam topik vault).
     */
    fun bolehAdopsiVaultLama(topicIdKoleksi: List<Int>): Boolean =
        topicIdKoleksi.all { it == 0 }

    /** Cari calon dengan nama yang sama (case-insensitive, trim). */
    fun cariNamaSama(calon: List<CalonVault>, nama: String): CalonVault? {
        val bersih = nama.trim()
        return calon.firstOrNull { it.nama.trim().equals(bersih, ignoreCase = true) }
    }

    /**
     * Pilih satu calon untuk diadopsi otomatis: kecocokan nama persis
     * ("Arsip Senja") menang atas segalanya; bila tidak ada yang cocok
     * nama tetapi hanya ada SATU calon, pakai itu. Bila ada beberapa
     * calon tanpa kecocokan nama, kembalikan null — jangan menebak.
     */
    fun pilihAdopsi(calon: List<CalonVault>, namaBawaan: String): CalonVault? {
        if (calon.isEmpty()) return null
        cariNamaSama(calon, namaBawaan)?.let { return it }
        if (calon.size == 1) return calon.first()
        return null
    }

    /** Pesan bila auto-adopt berhasil: vault sehat terdeteksi dan dipakai. */
    fun pesanAdopsiOtomatis(nama: String): String =
        "Vault \"$nama\" terdeteksi otomatis dan dijadikan aktif."

    /** Pesan bila "Buat vault baru" menemukan vault lama bernama sama. */
    fun pesanVaultSudahAda(nama: String): String =
        "Vault \"$nama\" sudah ada, dipakai lagi."

    /** Pesan bila vault rusak tetapi ada beberapa grup milik sendiri siap pakai. */
    fun pesanBanyakKandidat(jumlah: Int): String =
        "Vault rusak. Ditemukan $jumlah grup milikmu yang siap dipakai — " +
            "pilih salah satu di \"Pilih grup lama\"."
}
