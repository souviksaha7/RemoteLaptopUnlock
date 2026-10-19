# Proguard rules for Remote Laptop Biometric Unlock
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.remoteunlock.app.** { *; }
