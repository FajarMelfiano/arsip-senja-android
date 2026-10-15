plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.arsipsenja"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arsipsenja"
        minSdk = 26
        targetSdk = 34
        // Nomor build CI dipakai sebagai versionCode agar APK tiap build
        // bisa dipasang menimpa versi sebelumnya.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Keystore debug PERSISTEN (disuplai CI dari secret ANDROID_DEBUG_KEYSTORE_B64).
    // Tanpa ini, tiap runner CI membuat debug key baru sehingga APK build baru
    // ditolak Android saat update menimpa ("package conflicts with an existing
    // package"). Jika berkas tidak ada (build lokal), pakai debug key bawaan.
    signingConfigs {
        getByName("debug") {
            val persistent = rootProject.file("debug.p12")
            if (persistent.exists()) {
                storeFile = persistent
                storeType = "pkcs12"
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    // Satu APK per ABI + satu APK universal (semua ABI).
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// Skema Room diekspor ke app/schemas/<versi>.json setiap kali KSP berjalan.
// Tanpa ini, MigrationTestHelper tidak punya sumber kebenaran untuk memverifikasi
// bahwa migrasi menghasilkan skema yang sama dengan yang mendeklarasikan entity.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(false)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.biometric)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // TDLib prabuilt (org.drinkless.tdlib + libtdjni.so, 4 ABI termasuk arm64-v8a)
    implementation(libs.tdlib.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    // MigrationTestHelper memverifikasi migrasi Room terhadap skema yang
    // benar-benar diekspor, bukan sekadar SQL yang ditulis tangan.
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)

    // Uji instrumented: memverifikasi mekanisme pada runtime Android sungguhan.
    // Nilai ForegroundInfo dan ServiceInfo hanya bisa dipercaya di device.
    androidTestImplementation(libs.androidx.test.junit)
    // Runner sendiri tidak ikut lewat androidx.test.ext:junit; tanpa ini kelas
    // AndroidJUnitRunner tidak ada di APK test dan instrumentasi gagal start.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.truth)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
