# Huawei Wear Engine
-keep class com.huawei.wearengine.** { *; }
-dontwarn com.huawei.wearengine.**

# Huawei HMS Core
-keep class com.huawei.hms.** { *; }
-dontwarn com.huawei.hms.**

# Eclipse Paho MQTT
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.dev2026.dooropener.door.DoorEvent { *; }

# Keep door event data classes used with Gson
-keepclassmembers class com.dev2026.dooropener.door.DoorEvent** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# AndroidX Security Crypto (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
