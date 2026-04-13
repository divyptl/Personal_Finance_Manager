# ============================================================
# WealthFlow ProGuard / R8 rules
# Applied only to release builds (isMinifyEnabled = true).
# ============================================================

# Keep line numbers for crash reports; rename source file to obscure layout.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# ---- Our Room entities and projections (Room reflects on them at runtime) ----
-keep class com.example.personalfinancemanager.Transaction { *; }
-keep class com.example.personalfinancemanager.Stock { *; }
-keep class com.example.personalfinancemanager.Budget { *; }
-keep class com.example.personalfinancemanager.CategorySum { *; }
-keep class com.example.personalfinancemanager.MonthlyTotal { *; }
-keep class com.example.personalfinancemanager.MonthlyCategoryTotal { *; }

# ---- WorkManager (daily budget checks) ----
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# ---- Gson (uses reflection on field names) ----
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---- PDFBox (Tom Roush Android port) ----
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.pdfbox.** { *; }
# PDFBox uses BouncyCastle reflectively for encryption
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# ---- MPAndroidChart ----
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ---- TOTP / Apache Commons Codec (transitive of dev.samstevens.totp) ----
-dontwarn dev.samstevens.totp.**
-keep class dev.samstevens.totp.** { *; }
-dontwarn org.apache.commons.codec.**

# ZXing pulls in desktop AWT/ImageIO classes for QR rendering which we don't
# use on Android. Suppress the missing-class errors entirely.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn com.google.zxing.client.j2se.**
-dontwarn com.google.zxing.client.**

# ---- Tink (used by androidx.security.crypto) ----
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# ---- AndroidX Lifecycle ----
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }

# ---- Our broker abstraction (interface kept for runtime polymorphism) ----
-keep interface com.example.personalfinancemanager.BrokerApi { *; }

# ---- Strip verbose log calls in release ----
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# ---- Generic Android boilerplate ----
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends android.content.BroadcastReceiver
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}
