package com.arsipsenja.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val skemaSenja = darkColorScheme(
    primary = WarnaArsip.Amber,
    onPrimary = WarnaArsip.Kanvas,
    primaryContainer = WarnaArsip.AmberRedup,
    onPrimaryContainer = WarnaArsip.Tinta,
    secondary = WarnaArsip.Violet,
    onSecondary = WarnaArsip.Tinta,
    secondaryContainer = WarnaArsip.VioletRedup,
    onSecondaryContainer = WarnaArsip.Tinta,
    tertiary = WarnaArsip.AmberTerang,
    onTertiary = WarnaArsip.Kanvas,
    background = WarnaArsip.Kanvas,
    onBackground = WarnaArsip.Tinta,
    surface = WarnaArsip.Veil,
    onSurface = WarnaArsip.Tinta,
    surfaceVariant = WarnaArsip.Permukaan2,
    onSurfaceVariant = WarnaArsip.Tinta2,
    surfaceTint = WarnaArsip.Amber,
    inverseSurface = WarnaArsip.Tinta,
    inverseOnSurface = WarnaArsip.Kanvas,
    inversePrimary = WarnaArsip.VioletTerang,
    outline = WarnaArsip.GarisKuat,
    outlineVariant = WarnaArsip.GarisRambut,
    error = WarnaArsip.Bahaya,
    onError = WarnaArsip.Kanvas,
    errorContainer = WarnaArsip.BahayaRedup,
    onErrorContainer = WarnaArsip.Tinta,
    scrim = WarnaArsip.KanvasDalam,
)

/**
 * Tema Arsip Senja. Selalu gelap — arsip ini hidup di waktu senja.
 */
@Composable
fun ArsipSenjaTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = skemaSenja,
        typography = TipografiArsip,
        shapes = BentukArsip,
        content = content,
    )
}
