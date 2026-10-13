package com.arsipsenja.data.telegram

import com.arsipsenja.data.local.BerkasArsipEntity
import com.arsipsenja.util.TipeFile
import org.drinkless.tdlib.TdApi

/** Ringkasan berkas yang diekstrak dari satu pesan Telegram. */
data class BerkasMentah(
    val chatId: Long,
    val messageId: Long,
    val nama: String,
    val ukuran: Long,
    val tanggal: Long,
    val mime: String,
    val tipe: TipeFile,
    val tdFileId: Int,
    val tdLocalPath: String?,
    val caption: String?,
)

/**
 * Ekstrak berkas media dari pesan. Mengembalikan null bila pesan bukan berkas
 * (teks, stiker, polling, dsb.).
 */
fun TdApi.Message.keBerkasMentah(): BerkasMentah? {
    val isi = content ?: return null
    return when (isi) {
        is TdApi.MessageDocument -> {
            val d = isi.document ?: return null
            BerkasMentah(
                chatId = chatId,
                messageId = id,
                nama = d.fileName?.takeIf { it.isNotBlank() } ?: "berkas_${id}",
                ukuran = d.document?.ukuranTotal() ?: 0L,
                tanggal = date * 1000L,
                mime = d.mimeType.orEmpty(),
                tipe = TipeFile.dariMime(d.mimeType, d.fileName.orEmpty()),
                tdFileId = d.document?.id ?: 0,
                tdLocalPath = d.document?.local?.path,
                caption = isi.caption?.text,
            )
        }
        is TdApi.MessageVideo -> {
            val v = isi.video ?: return null
            BerkasMentah(
                chatId = chatId,
                messageId = id,
                nama = v.fileName?.takeIf { it.isNotBlank() } ?: "video_$id.mp4",
                ukuran = v.video?.ukuranTotal() ?: 0L,
                tanggal = date * 1000L,
                mime = v.mimeType.orEmpty(),
                tipe = TipeFile.VIDEO,
                tdFileId = v.video?.id ?: 0,
                tdLocalPath = v.video?.local?.path,
                caption = isi.caption?.text,
            )
        }
        is TdApi.MessageAudio -> {
            val a = isi.audio ?: return null
            BerkasMentah(
                chatId = chatId,
                messageId = id,
                nama = a.fileName?.takeIf { it.isNotBlank() } ?: "audio_$id",
                ukuran = a.audio?.ukuranTotal() ?: 0L,
                tanggal = date * 1000L,
                mime = a.mimeType.orEmpty(),
                tipe = TipeFile.AUDIO,
                tdFileId = a.audio?.id ?: 0,
                tdLocalPath = a.audio?.local?.path,
                caption = isi.caption?.text ?: a.title,
            )
        }
        is TdApi.MessagePhoto -> {
            val p = isi.photo ?: return null
            val terbesar = p.sizes?.lastOrNull() ?: return null
            val file = terbesar.photo ?: return null
            BerkasMentah(
                chatId = chatId,
                messageId = id,
                nama = "foto_$id.jpg",
                ukuran = file.ukuranTotal(),
                tanggal = date * 1000L,
                mime = "image/jpeg",
                tipe = TipeFile.GAMBAR,
                tdFileId = file.id,
                tdLocalPath = file.local?.path,
                caption = isi.caption?.text,
            )
        }
        is TdApi.MessageAnimation -> {
            val g = isi.animation ?: return null
            BerkasMentah(
                chatId = chatId,
                messageId = id,
                nama = g.fileName?.takeIf { it.isNotBlank() } ?: "animasi_$id.gif",
                ukuran = g.animation?.ukuranTotal() ?: 0L,
                tanggal = date * 1000L,
                mime = g.mimeType.orEmpty(),
                tipe = TipeFile.GAMBAR,
                tdFileId = g.animation?.id ?: 0,
                tdLocalPath = g.animation?.local?.path,
                caption = isi.caption?.text,
            )
        }
        else -> null
    }
}

/** Ukuran efektif berkas TDLib (expectedSize bila size masih nol). */
fun TdApi.File.ukuranTotal(): Long = when {
    size > 0L -> size
    expectedSize > 0L -> expectedSize
    else -> 0L
}

/** Ubah berkas mentah menjadi baris indeks lokal. */
fun BerkasMentah.keEntity(koleksiId: Long?, nomorKatalog: Int, sekarang: Long): BerkasArsipEntity =
    BerkasArsipEntity(
        id = BerkasArsipEntity.kunci(chatId, messageId),
        messageId = messageId,
        chatId = chatId,
        koleksiId = koleksiId,
        nama = nama,
        ukuran = ukuran,
        tanggal = tanggal,
        mime = mime,
        tipe = tipe.name,
        nomorKatalog = nomorKatalog,
        tdFileId = tdFileId,
        tdLocalPath = tdLocalPath,
        deskripsi = caption?.takeIf { it.isNotBlank() },
        dibuatPada = sekarang,
    )
