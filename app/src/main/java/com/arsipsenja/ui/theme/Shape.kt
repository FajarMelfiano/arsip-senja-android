package com.arsipsenja.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radius Arsip Senja: kartu katalog 10dp, lembar besar 16dp, chip penuh.
 */
val BentukArsip: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Radius sudut kartu katalog. */
val SudutKartu = RoundedCornerShape(10.dp)

/** Radius lembar/sheet. */
val SudutLembar = RoundedCornerShape(16.dp)

/** Bentuk pil untuk chip & tombol kecil. */
val SudutPil = RoundedCornerShape(999.dp)
