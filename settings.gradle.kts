pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // AAR prabuilt TDLib (org.drinkless.tdlib + libtdjni.so) dari mirror
        // TGX-Android/tdlib — lihat README bagian "Pilihan TDLib".
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.capullo-tech")
            }
        }
    }
}

rootProject.name = "Arsip Senja"
include(":app")
