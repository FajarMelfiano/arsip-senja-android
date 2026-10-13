@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.arsipsenja.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as FontGoogle
import androidx.compose.ui.unit.sp
import com.arsipsenja.R

/**
 * Penyedia Google Fonts (unduh saat pertama dipakai, lewat layanan Google Play).
 * Sertifikat penyedia ada di res/values/font_certs.xml.
 */
private val penyediaGoogleFonts = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

/** Fraunces — display/angka, memberi kesan buku katalog lama. */
val Fraunces: FontFamily = FontFamily(
    FontGoogle(GoogleFont("Fraunces"), penyediaGoogleFonts, FontWeight.Normal),
    FontGoogle(GoogleFont("Fraunces"), penyediaGoogleFonts, FontWeight.Medium),
    FontGoogle(GoogleFont("Fraunces"), penyediaGoogleFonts, FontWeight.SemiBold),
    FontGoogle(GoogleFont("Fraunces"), penyediaGoogleFonts, FontWeight.Bold),
)

/** Plus Jakarta Sans — teks isi, jernih di ukuran kecil. */
val JakartaSans: FontFamily = FontFamily(
    FontGoogle(GoogleFont("Plus Jakarta Sans"), penyediaGoogleFonts, FontWeight.Normal),
    FontGoogle(GoogleFont("Plus Jakarta Sans"), penyediaGoogleFonts, FontWeight.Medium),
    FontGoogle(GoogleFont("Plus Jakarta Sans"), penyediaGoogleFonts, FontWeight.SemiBold),
    FontGoogle(GoogleFont("Plus Jakarta Sans"), penyediaGoogleFonts, FontWeight.Bold),
)

/** Skala tipografi Arsip Senja: Fraunces untuk display/judul besar, Jakarta untuk sisanya. */
val TipografiArsip: Typography = Typography(
    displayLarge = Typography().displayLarge.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = Typography().displayMedium.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    displaySmall = Typography().displaySmall.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = Typography().titleSmall.copy(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = JakartaSans,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = JakartaSans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = Typography().bodySmall.copy(
        fontFamily = JakartaSans,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = Typography().labelLarge.copy(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = Typography().labelMedium.copy(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = Typography().labelSmall.copy(
        fontFamily = JakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp,
    ),
)
