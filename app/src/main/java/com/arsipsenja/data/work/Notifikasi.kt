package com.arsipsenja.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arsipsenja.R

/**
 * Kanal notifikasi & utilitas notifikasi untuk pekerjaan latar (unggah/unduh).
 */
object Notifikasi {

    const val KANAL_TRANSFER = "transfer"
    const val KANAL_SINKRON = "sinkron"

    private const val ID_UNGGUH = 1001
    private const val ID_UNDUH = 1002
    private const val ID_SINKRON = 1003

    /** Daftarkan kanal notifikasi (panggil sekali saat aplikasi start). */
    fun siapkan(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                KANAL_TRANSFER,
                "Transfer berkas",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progres unggah dan unduh arsip"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                KANAL_SINKRON,
                "Sinkronisasi arsip",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Pemberitahuan sinkronisasi koleksi"
            },
        )
    }

    private fun notifikasiTransfer(
        context: Context,
        id: Int,
        judul: String,
        isi: String,
        progresMaks: Int,
        progresSekarang: Int,
        selesai: Boolean,
    ): android.app.Notification {
        val buka = PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val bangun = NotificationCompat.Builder(context, KANAL_TRANSFER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(judul)
            .setContentText(isi)
            .setContentIntent(buka)
            .setOngoing(!selesai)
            .setAutoCancel(selesai)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (progresMaks > 0 && progresSekarang in 0..progresMaks) {
            bangun.setProgress(progresMaks, progresSekarang, false)
        } else if (!selesai) {
            bangun.setProgress(0, 0, true)
        }
        return bangun.build()
    }

    fun tampilkan(
        context: Context,
        id: Int,
        judul: String,
        isi: String,
        progresMaks: Int = 0,
        progresSekarang: Int = 0,
        selesai: Boolean = false,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        NotificationManagerCompat.from(context).notify(
            id,
            bangunTransfer(
                context, id, judul, isi, progresMaks, progresSekarang, selesai,
            ),
        )
    }

    /**
     * Bangun notifikasi transfer.
     *
     * Dipisah dari [tampilkan] karena `setForeground` memerlukan objek
     * Notification, sedangkan notifikasi biasa di-*notify* lewat
     * NotificationManager.
     */
    fun bangunTransfer(
        context: Context,
        id: Int,
        judul: String,
        isi: String,
        progresMaks: Int = 0,
        progresSekarang: Int = 0,
        selesai: Boolean = false,
    ): android.app.Notification =
        notifikasiTransfer(context, id, judul, isi, progresMaks, progresSekarang, selesai)

    /**
 * Notifikasi unggah untuk satu berkas.
 *
 * [id] dibiarkan opsional agar setiap berkas punya notifikasi sendiri.
 * Sebelumnya semua unggah berbagi satu id sehingga antrean berjalan saling
 * menimpa, dan multipart yang selesai per-potongan ikut menutup notifikasi
 * padahal potongan berikutnya masih berjalan.
 */
fun tampilkanUnggah(
        context: Context,
        nama: String,
        persen: Int,
        selesai: Boolean,
        id: Int = ID_UNGGUH,
    ) = tampilkan(
        context,
        id,
        if (selesai) "Unggah selesai" else "Mengunggah…",
        nama,
        progresMaks = 100,
        progresSekarang = persen,
        selesai = selesai,
    )

/** Notifikasi unduhan untuk satu berkas; lihat [tampilkanUnggah] soal id. */
fun tampilkanUnduh(
        context: Context,
        nama: String,
        persen: Int,
        selesai: Boolean,
        id: Int = ID_UNDUH,
    ) = tampilkan(
        context,
        id,
        if (selesai) "Unduhan selesai" else "Mengunduh…",
        nama,
        progresMaks = 100,
        progresSekarang = persen,
        selesai = selesai,
    )

    fun tampilkanSinkron(context: Context, pesan: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        NotificationManagerCompat.from(
            context,
        ).notify(ID_SINKRON, bangunSinkron(context, pesan))
    }

    private fun bangunSinkron(context: Context, pesan: String): android.app.Notification =
        NotificationCompat.Builder(context, KANAL_SINKRON)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Arsip Senja")
            .setContentText(pesan)
            .setAutoCancel(true)
            .build()

    fun batalkan(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun idUnggahKhusus(nama: String): Int = 1100 + (nama.hashCode() and 0xFF)

    fun idUnduhKhusus(berkasId: String): Int = 1200 + (berkasId.hashCode() and 0xFF)
}
