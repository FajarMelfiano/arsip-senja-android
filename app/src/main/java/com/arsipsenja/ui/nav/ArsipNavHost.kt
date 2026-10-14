package com.arsipsenja.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arsipsenja.data.telegram.StatusOtorisasi
import com.arsipsenja.ui.icons.ArsipIcons
import com.arsipsenja.ui.screens.BerkasScreen
import com.arsipsenja.ui.screens.DasborScreen
import com.arsipsenja.ui.screens.LoginScreen
import com.arsipsenja.ui.screens.PengaturanScreen
import com.arsipsenja.ui.screens.PohonArsipScreen
import com.arsipsenja.ui.screens.TautanScreen
import com.arsipsenja.ui.screens.UnggahScreen
import com.arsipsenja.ui.theme.WarnaArsip
import com.arsipsenja.ui.viewmodel.LocalKontainer

/** Rute string (API navigasi klasik — stabil di semua versi navigation 2.x). */
private object Rute {
    const val LOGIN = "login"
    const val DASBOR = "dasbor"
    const val UNGGAH = "unggah"
    const val TAUTAN = "tautan"
    const val POHON = "pohon"
    const val PENGATURAN = "pengaturan"
    const val BERKAS = "berkas/{berkasId}"

    fun berkas(berkasId: String) = "berkas/$berkasId"
}

/** Satu item bilah navigasi bawah. */
private data class ItemBawah(val label: String, val ikon: ImageVector, val rute: String)

/**
 * Rangka navigasi utama: NavHost + bilah navigasi bawah
 * (tampil hanya di layar tingkat atas, hilang di login & detail berkas).
 */
@Composable
fun ArsipNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val wadah = checkNotNull(LocalKontainer.current) { "Kontainer aplikasi belum dipasang" }
    val status by wadah.telegramClient.status.collectAsState()

    val entri by navController.currentBackStackEntryAsState()
    val ruteAktif = entri?.destination?.route
    val diLogin = ruteAktif == Rute.LOGIN
    val diBerkas = ruteAktif?.startsWith("berkas/") == true

    // Pindah layar HANYA saat status otorisasi BERUBAH.
    // (Versi lama memakai LaunchedEffect(status, ruteAktif) sehingga setiap
    // ganti tab memicu navigasi balik ke dasbor — tab tidak pernah bisa dibuka.)
    var statusLalu by remember { mutableStateOf<StatusOtorisasi?>(null) }
    LaunchedEffect(status) {
        val lalu = statusLalu
        statusLalu = status
        // Bandingkan kelas (bukan equals): hitung mundur OTP memakai data class
        // yang nilainya berubah tiap detik — itu bukan "perubahan" navigasi.
        if (lalu != null && lalu::class == status::class) return@LaunchedEffect
        val ruteKini = navController.currentBackStackEntry?.destination?.route
        when {
            status is StatusOtorisasi.Siap && ruteKini == Rute.LOGIN -> {
                navController.navigate(Rute.DASBOR) {
                    popUpTo(Rute.LOGIN) { inclusive = true }
                    launchSingleTop = true
                }
            }
            status is StatusOtorisasi.Keluar && ruteKini != Rute.LOGIN -> {
                navController.navigate(Rute.LOGIN) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val itemBawah = listOf(
        ItemBawah("Dasbor", ArsipIcons.LayoutGrid, Rute.DASBOR),
        ItemBawah("Unggah", ArsipIcons.Upload, Rute.UNGGAH),
        ItemBawah("Tautan", ArsipIcons.Link, Rute.TAUTAN),
        ItemBawah("Pohon", ArsipIcons.Folder, Rute.POHON),
        ItemBawah("Pengaturan", ArsipIcons.Settings, Rute.PENGATURAN),
    )

    Scaffold(
        containerColor = WarnaArsip.Kanvas,
        bottomBar = {
            if (!diLogin && !diBerkas) {
                NavigationBar(containerColor = WarnaArsip.Veil) {
                    itemBawah.forEach { item ->
                        NavigationBarItem(
                            selected = ruteAktif == item.rute,
                            onClick = {
                                navController.navigate(item.rute) {
                                    popUpTo(Rute.DASBOR) { saveState = true }
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
                            label = {
                                Text(
                                    item.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 11.sp,
                                    ),
                                )
                            },
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
            startDestination = Rute.LOGIN,
            modifier = Modifier.padding(padding),
        ) {
            composable(Rute.LOGIN) { LoginScreen() }
            composable(Rute.DASBOR) {
                DasborScreen(
                    onBukaBerkas = { id -> navController.navigate(Rute.berkas(id)) },
                )
            }
            composable(Rute.UNGGAH) { UnggahScreen() }
            composable(Rute.TAUTAN) { TautanScreen() }
            composable(Rute.POHON) {
                PohonArsipScreen(
                    onBukaBerkas = { id -> navController.navigate(Rute.berkas(id)) },
                )
            }
            composable(Rute.PENGATURAN) { PengaturanScreen() }
            composable(
                route = Rute.BERKAS,
                arguments = listOf(navArgument("berkasId") { type = NavType.StringType }),
            ) { daftar ->
                val berkasId = daftar.arguments?.getString("berkasId").orEmpty()
                BerkasScreen(
                    berkasId = berkasId,
                    onKembali = { navController.popBackStack() },
                )
            }
        }
    }
}
