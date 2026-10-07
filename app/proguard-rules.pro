# okhttp references optional TLS platforms reflectively; they are absent on Android.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Shizuku is invoked reflectively (Shizuku.newProcess lookup) — keep its API surface.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
