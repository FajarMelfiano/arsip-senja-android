// File build root — plugin dideklarasikan di sini, konfigurasi aplikasi di :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// Selaraskan kotlin-stdlib dengan versi Kotlin Gradle plugin (2.0.21):
// beberapa dependensi menarik stdlib 2.2.x yang metadata-nya tidak bisa
// dibaca compiler 2.0.x (error kspDebugKotlin di CI).
subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin"
                && requested.name.startsWith("kotlin-stdlib")
            ) {
                useVersion("2.0.21")
                because("Align dengan Kotlin Gradle plugin 2.0.21")
            }
        }
    }
}
