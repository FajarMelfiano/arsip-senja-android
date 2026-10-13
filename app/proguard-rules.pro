# Aturan R8/ProGuard — Arsip Senja.

# TDLib: kelas dihasilkan + dipakai lewat JNI, jangan di-strip/diubah nama.
-keep class org.drinkless.tdlib.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-dontwarn org.drinkless.tdlib.**

# Serialisasi (kotlinx) untuk rute navigasi type-safe.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.arsipsenja.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room.
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Compose & Material3 memakai rules bawaan AGP; tambahkan hanya yang perlu.
-keep class androidx.credentials.** { *; }
