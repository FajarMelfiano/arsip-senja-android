package com.arsipsenja.util

/**
 * Klasifikasi tipe berkas untuk filter dasbor dan pemilihan ikon.
 */
enum class TipeFile(val label: String) {
    GAMBAR("Gambar"),
    VIDEO("Video"),
    AUDIO("Audio"),
    DOKUMEN("Dokumen"),
    ARSIP("Arsip"),
    LAINNYA("Lainnya"),
    ;

    companion object {
        private val EKSTENSI_ARSIP = setOf(
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "tgz",
        )

        /** MIME dokumen yang dikenal; sisanya jatuh ke ekstensi. */
        private val MIME_DOKUMEN = setOf(
            "application/pdf",
            "application/rtf",
            "application/json",
            "application/xml",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/epub+zip",
        )

        fun dariMime(mime: String?, nama: String): TipeFile {
            val m = mime?.lowercase()?.substringBefore(';')?.trim().orEmpty()
            return when {
                m.isEmpty() -> dariEkstensi(nama)
                m.startsWith("image/") -> GAMBAR
                m.startsWith("video/") -> VIDEO
                m.startsWith("audio/") -> AUDIO
                m == "application/zip" ||
                    m == "application/x-rar-compressed" ||
                    m == "application/vnd.rar" ||
                    m == "application/x-7z-compressed" ||
                    m == "application/gzip" ||
                    m == "application/x-tar" -> ARSIP
                m in MIME_DOKUMEN -> DOKUMEN
                m.startsWith("text/") -> DOKUMEN
                else -> dariEkstensi(nama)
            }
        }

        fun dariEkstensi(nama: String): TipeFile {
            val ekstensi = nama.substringAfterLast('.', "").lowercase()
            return when (ekstensi) {
                "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg" -> GAMBAR
                "mp4", "mkv", "mov", "avi", "webm", "m4v", "3gp" -> VIDEO
                "mp3", "m4a", "aac", "flac", "ogg", "wav", "opus" -> AUDIO
                in EKSTENSI_ARSIP -> ARSIP
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv",
                "json", "xml", "epub", "rtf",
                -> DOKUMEN
                else -> LAINNYA
            }
        }
    }
}

/** Potongan nama berkas tanpa direktori, aman untuk nama path aneh. */
fun namaBerkas(path: String): String = path.substringAfterLast('/').ifEmpty { path }

/** Ekstensi berkas (tanpa titik) dalam huruf kecil; "" bila tidak ada. */
fun ekstensi(nama: String): String {
    val dot = nama.lastIndexOf('.')
    if (dot <= 0 || dot == nama.lastIndex) return ""
    return nama.substring(dot + 1).lowercase()
}
