package com.arsipsenja.ui.nav

import kotlinx.serialization.Serializable

/** Rute type-safe (kotlinx.serialization) untuk navigasi Compose. */

@Serializable
data object RuteLogin

@Serializable
data object RuteDasbor

@Serializable
data object RuteUnggah

@Serializable
data object RuteTautan

@Serializable
data object RutePengaturan

@Serializable
data class RuteBerkas(val berkasId: String)

/** Empat tujuan yang tampil di bilah navigasi bawah. */
data class DestinasiBawah(
    val rute: Any,
    val label: String,
    val ikon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelAksesibilitas: String,
)
