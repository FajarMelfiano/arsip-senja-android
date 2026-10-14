package com.arsipsenja.data.repo

import org.drinkless.tdlib.TdApi

/**
 * Aturan murni mesin realtime — tanpa TDLib hidup, tanpa database, tanpa
 * Android framework. Bisa di-unit-test langsung.
 *
 * Filosofi: event-driven + debounce. Pesan baru dari Telegram dikumpulkan
 * dalam jendela singkat lalu di-ingest sekaligus; sinkron ulang otomatis
 * hanya jalan bila sinkron terakhir sudah melewati ambang. Tidak ada
 * polling, tidak ada foreground service, tidak ada WorkManager periodik —
 * hemat baterai sesuai permintaan.
 */
object AturanRealtime {

    /**
     * Jendela batch pesan beruntun (ms). Pesan-pesan yang tiba dalam
     * jendela ini di-ingest dalam satu transaksi batch, bukan satu per
     * satu — menghindari churn database saat mis. 20 foto dikirim
     * beruntun dari perangkat lain.
     *
     * Dipilih 2 detik: cukup menampung burst kiriman manusia, cukup
     * singkat sehingga berkas terasa "langsung muncul".
     */
    const val JENDELA_BATCH_MS = 2000L

    /**
     * Ambang sinkron ulang otomatis (ms). Sinkron ringan (koneksi pulih /
     * kembali foreground) hanya jalan bila sinkron terakhir lebih tua
     * dari ini — mencegah spam saat koneksi flapping putus-pulih berulang.
     *
     * Dipilih 60 detik: riwayat terbaru hanya sepanjang 5 halaman x 100
     * pesan, jadi sinkron ringan murah; 60 detik cukup jarang untuk
     * tidak membebani radio/baterai.
     */
    const val AMBANG_SINKRON_ULANG_MS = 60_000L

    /** Keputusan ingest untuk satu pesan baru dari [TelegramClient.pesanBaru]. */
    enum class KeputusanPesan {
        /** Masukkan ke indeks lokal. */
        PROSES,
        /** Chat/topik belum dikenal — abaikan, jangan pernah buat koleksi liar. */
        ABAIKAN_TAK_DIKENAL,
        /** Id "$chatId:$messageId" sudah ada di indeks — lewati. */
        LEWATI_DUPLIKAT,
        /** Pesan bukan berkas (teks, stiker, polling, dsb.) — lewati. */
        LEWATI_BUKAN_BERKAS,
    }

    /**
     * Tentukan nasib satu pesan baru.
     *
     * @param dikenal true bila pasangan (chatId, topicId) pesan sudah
     * terdaftar sebagai koleksi (pribadi maupun impor).
     * @param sudahAda true bila id dedupe pesan sudah ada di indeks lokal.
     * @param adaBerkas true bila pesan mengandung berkas media/dokumen.
     */
    fun keputusanPesan(
        dikenal: Boolean,
        sudahAda: Boolean,
        adaBerkas: Boolean,
    ): KeputusanPesan = when {
        !dikenal -> KeputusanPesan.ABAIKAN_TAK_DIKENAL
        sudahAda -> KeputusanPesan.LEWATI_DUPLIKAT
        !adaBerkas -> KeputusanPesan.LEWATI_BUKAN_BERKAS
        else -> KeputusanPesan.PROSES
    }

    /**
     * True bila sinkron otomatis boleh jalan sekarang: sinkron terakhir
     * sudah melewati [AMBANG_SINKRON_ULANG_MS], atau belum pernah sinkron.
     */
    fun bolehSinkronUlang(
        sinkronTerakhir: Long?,
        sekarang: Long = System.currentTimeMillis(),
    ): Boolean =
        sinkronTerakhir == null || sekarang - sinkronTerakhir >= AMBANG_SINKRON_ULANG_MS

    /**
     * Id dedupe satu pesan: "$chatId:$messageId".
     * Sama dengan [BerkasArsipEntity.kunci] agar konsisten dengan indeks.
     */
    fun kunciDedupe(chatId: Long, messageId: Long): String = "$chatId:$messageId"

    /**
     * topicId forum dari pesan; 0 untuk pesan biasa/channel.
     * TDLib versi ini memakai field `topicId: MessageTopic` (bukan
     * messageThreadId) — dibuktikan dari constant pool classes.jar.
     */
    fun topikDariPesan(pesan: TdApi.Message): Int =
        (pesan.topicId as? TdApi.MessageTopicForum)?.forumTopicId ?: 0
}
