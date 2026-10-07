# okhttp references optional TLS platforms reflectively; they are absent on Android.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# espresso's errorprone annotations reference the JDK's javax.lang.model, absent on Android.
-dontwarn javax.lang.model.**

# Shizuku is invoked reflectively (Shizuku.newProcess lookup) — keep its API surface.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Instrumented tests share the app's classloader: androidx.test calls kotlin
# stdlib members the app itself never uses, so the shrunk debug APK must not
# strip them (S2-04 device run: CNFE kotlin.LazyKt in TestDirCalculator).
-keep class kotlin.** { *; }
