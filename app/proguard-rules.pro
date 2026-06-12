# ComfyUI Client ProGuard Rules

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.comfyui.client.data.model.** { *; }
-keepclassmembers class com.comfyui.client.data.api.** { *; }

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.comfyui.client.**$$serializer { *; }
-keepclassmembers class com.comfyui.client.** {
    *** Companion;
}
-keepclasseswithmembers class com.comfyui.client.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil
-dontwarn coil.**

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
