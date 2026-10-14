package com.arsipsenja.data.local

/**
 * Identitas stabil satu koleksi.
 *
 * - Channel lama: topicId = 0 (satu channel = satu koleksi).
 * - Koleksi topik: pasangan (chatId vault, topicId forum).
 */
data class KunciKoleksi(
    val chatId: Long,
    val topicId: Int = 0,
) {
    val adalahTopik: Boolean get() = topicId != 0

    /** Label ringkas untuk log/debug; bukan untuk ditampilkan ke user. */
    override fun toString(): String = "$chatId:$topicId"
}
