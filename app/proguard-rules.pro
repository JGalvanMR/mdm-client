# Mantener modelos de red (Gson los necesita)
-keep class com.mdm.client.data.models.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keepattributes Exceptions
-keep class com.google.gson.** { *; }

# DevicePolicyManager — no ofuscar nombres de métodos del sistema
-keep class android.app.admin.** { *; }

# Mantener receivers y services
-keep class com.mdm.client.receiver.** { *; }
-keep class com.mdm.client.service.** { *; }
-keep class com.mdm.client.worker.**  { *; }