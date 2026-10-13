package com.arsipsenja

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Layar darurat: bila aplikasi crash, tampilkan tumpukan galat agar bisa
 * dibaca/disalin, bukan sekadar force-close misterius.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val jejak = intent.getStringExtra(EKSTRA_JEJAK)
            ?: bacaJejakTerakhir()
            ?: "Tidak ada detail galat."

        val tata = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val judul = TextView(this).apply {
            text = "Arsip Senja terhenti"
            textSize = 20f
        }
        val pesan = TextView(this).apply {
            text = "Maaf, aplikasi mengalami galat. " +
                "Salin detail di bawah ini dan kirimkan agar bisa diperbaiki."
            textSize = 14f
        }
        val gulung = ScrollView(this)
        val isi = TextView(this).apply {
            text = jejak.take(20000)
            textSize = 11f
            isTextSelectable = true
            setPadding(0, 24, 0, 24)
        }
        gulung.addView(isi)

        val tombolSalin = Button(this).apply {
            text = "Salin detail galat"
            setOnClickListener {
                val papan = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                papan.setPrimaryClip(ClipData.newPlainText("crash", jejak))
                Toast.makeText(this@CrashActivity, "Disalin", Toast.LENGTH_SHORT).show()
            }
        }
        val tombolTutup = Button(this).apply {
            text = "Tutup"
            setOnClickListener { finishAffinity() }
        }

        tata.addView(judul)
        tata.addView(pesan)
        tata.addView(gulung, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        tata.addView(tombolSalin)
        tata.addView(tombolTutup)
        setContentView(tata)
    }

    private fun bacaJejakTerakhir(): String? = try {
        val berkas = File(filesDir, NAMA_BERKAS_JEJAK)
        if (berkas.exists()) berkas.readText() else null
    } catch (e: Exception) {
        null
    }

    companion object {
        const val EKSTRA_JEJAK = "jejak_galat"
        const val NAMA_BERKAS_JEJAK = "crash-terakhir.txt"

        fun niat(context: Context, jejak: String): Intent =
            Intent(context, CrashActivity::class.java).apply {
                putExtra(EKSTRA_JEJAK, jejak.take(20000))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
    }
}

/**
 * Penangkap crash global: tulis jejak ke berkas lalu buka [CrashActivity].
 * Dipasang paling awal di [ArsipSenjaApp.onCreate].
 */
object PenangkapCrash : Thread.UncaughtExceptionHandler {

    private var bawaan: Thread.UncaughtExceptionHandler? = null
    private var terpasang = false

    fun pasang(context: Context) {
        if (terpasang) return
        terpasang = true
        val appContext = context.applicationContext
        bawaan = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { utas, lemparan ->
            try {
                val penulis = StringWriter()
                lemparan.printStackTrace(PrintWriter(penulis))
                val jejak = "${lemparan}\n${penulis}"
                Log.e("ArsipSenja", "CRASH:\n$jejak")
                File(appContext.filesDir, CrashActivity.NAMA_BERKAS_JEJAK)
                    .writeText(jejak.take(100000))
                appContext.startActivity(CrashActivity.niat(appContext, jejak))
            } catch (e: Exception) {
                Log.e("ArsipSenja", "Penangkap crash ikut gagal", e)
            } finally {
                // Beri waktu layar crash tampil, lalu hentikan proses seperti biasa.
                try { Thread.sleep(1200) } catch (_: InterruptedException) { }
                bawaan?.uncaughtException(utas, lemparan)
            }
        }
    }
}
