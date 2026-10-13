package com.arsipsenja.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.hasRoute
import androidx.navigation.toRoute
import com.arsipsenja.data.telegram.StatusOtorisasi
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.screens.BerkasScreen
import com.arsipsenja.ui.screens.DasborScreen
import com.arsipsenja.ui.screens.LoginScreen
import com.arsipsenja.ui.screens.PengaturanScreen
import com.arsipsenja.ui.screens.TautanScreen
import com.arsipsenja.ui.screens.UnggahScreen
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.LocalKontainer
import androidx.compose.runtime.collectAsState

/** Satu item bilah navigasi bawah. */
private data class ItemBawah(val label: String, val ikon: ImageVector, val buat: () -> Any)

/**
 * Rangka navigasi utama: NavHost type-safe + bilah navigasi bawah
 * (tampil hanya di layar tingkat atas, hilang di login & detail berkas).
 */
@Composable
fun ArsipNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val wadah = checkNotNull(LocalKontainer.current) { "Kontainer aplikasi belum dipasang" }
    val status by wadah.telegramClient.status.collectAsState()

    val entri by navController.currentBackStackEntryAsState()
    val tujuan = entri?.destination
    val diLogin = tujuan?.hasRoute<RuteLogin>() == true
    val diBerkas = tujuan?.hasRoute<RuteBerkas>() == true

    // Pindah layar mengikuti status otorisasi Telegram.
    LaunchedEffect(status, tujuan) {
        when (status) {
            is StatusOtorisasi.Siap -> {
                if (tujuan?.hasRoute<RuteDasbor>() != true) {
                    navController.navigate(RuteDasbor) {
                        popUpTo(RuteLogin) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            is StatusOtorisasi.Keluar -> {
                if (tujuan?.hasRoute<RuteLogin>() != true) {
                    navController.navigate(RuteLogin) {
                        popUpTo(RuteDasbor) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            else -> Unit
        }
    }

    val itemBawah = listOf(
        ItemBawah("Dasbor", ArsipIcons.LayoutGrid) { RuteDasbor },
        ItemBawah("Unggah", ArsipIcons.Upload) { RuteUnggah },
        ItemBawah("Tautan", ArsipIcons.Link) { RuteTautan },
        ItemBawah("Pengaturan", ArsipIcons.Settings) { RutePengaturan },
    )

    Scaffold(
        containerColor = com.arsipsenja.ui.theme.WarnaArsip.Kanvas,
        bottomBar = {
            if (!diLogin && !diBerkas) {
                NavigationBar(containerColor = WarnaArsip.Veil) {
                    itemBawah.forEach { item ->
                        val rute = item.buat()
                        val terpilih = when (rute) {
                            is RuteDasbor -> tujuan?.hasRoute<RuteDasbor>() == true
                            is RuteUnggah -> tujuan?.hasRoute<RuteUnggah>() == true
                            is RuteTautan -> tujuan?.hasRoute<RuteTautan>() == true
                            else -> tujuan?.hasRoute<RutePengaturan>() == true
                        }
                        NavigationBarItem(
                            selected = terpilih,
                            onClick = {
                                navController.navigate(rute) {
                                    popUpTo(RuteDasbor) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.ikon,
                                    contentDescription = item.label,
                                )
                            },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = WarnaArsip.Amber,
                                selectedTextColor = WarnaArsip.Amber,
                                unselectedIconColor = WarnaArsip.Tinta3,
                                unselectedTextColor = WarnaArsip.Tinta3,
                                indicatorColor = WarnaArsip.AmberRedup,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = RuteLogin,
            modifier = Modifier.padding(padding),
            containerColor = WarnaArsip.Kanvas,
        ) {
            composable<RuteLogin> { LoginScreen() }
            composable<RuteDasbor> {
                DasborScreen(
                    onBukaBerkas = { id -> navController.navigate(RuteBerkas(id)) },
                )
            }
            composable<RuteUnggah> { UnggahScreen() }
            composable<RuteTautan> { TautanScreen() }
            composable<RutePengaturan> { PengaturanScreen() }
            composable<RuteBerkas> { daftar ->
                val rute = daftar.toRoute<RuteBerkas>()
                BerkasScreen(
                    berkasId = rute.berkasId,
                    onKembali = { navController.popBackStack() },
                )
            }
        }
    }
}
