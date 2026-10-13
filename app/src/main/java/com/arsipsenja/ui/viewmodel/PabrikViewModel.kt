package com.arsipsenja.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.arsipsenja.AppContainer
import com.arsipsenja.ArsipSenjaApp

/** Kanal Compose untuk [AppContainer] — diisi MainActivity, dibaca layar mana pun. */
val LocalKontainer = staticCompositionLocalOf<AppContainer?> { null }

/** Ambil [AppContainer] dari [CreationExtras] (DI manual tanpa Hilt). */
fun kontainerDari(extras: CreationExtras): AppContainer =
    (extras[APPLICATION_KEY] as ArsipSenjaApp).wadah

/**
 * Pabrik ViewModel berbasis kontainer: `viewModel(factory = X.Pabrik)`.
 * Satu blok menerima [AppContainer] lalu mengembalikan ViewModel yang benar.
 */
fun pabrikKontainer(blok: (AppContainer) -> ViewModel) = viewModelFactory {
    initializer { blok(kontainerDari(this)) }
}
