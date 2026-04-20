# FormLogic AI — ProGuard / R8 Rules

# ── Kotlin ─────────────────────────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { *; }
-keepclassmembers class kotlin.Lazy { *; }

# ── Coroutines ─────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Jetpack Compose ────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Navigation ─────────────────────────────────────────────────────────────────
-keep class androidx.navigation.** { *; }

# ── Retrofit / OkHttp ─────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class retrofit2.** { *; }
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}

# ── Kotlinx Serialization ──────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.formlogic.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# ── FormLogic app models (serialized over network) ─────────────────────────────
-keep class com.formlogic.models.** { *; }
-keep class com.formlogic.services.** { *; }

# ── DataStore ──────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── CameraX ────────────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }

# ── Coil ───────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── General Android ────────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature
-keepattributes Exceptions
