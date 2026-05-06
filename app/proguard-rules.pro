# ──────────────────────────────────────────────────────────────────────────────
# RETROFIT & OKHTTP
# ──────────────────────────────────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep interface * {
    @retrofit2.http.GET *;
    @retrofit2.http.POST *;
    @retrofit2.http.PUT *;
    @retrofit2.http.PATCH *;
    @retrofit2.http.DELETE *;
    @retrofit2.http.HEAD *;
}
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature, InnerClasses, EnclosingMethod

# ──────────────────────────────────────────────────────────────────────────────
# GSON & SERIALIZATION — Critical for Release APK JSON parsing
# ──────────────────────────────────────────────────────────────────────────────
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations

# Keep all @SerializedName annotated fields (even if other rules miss them)
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all data/model classes used for API request/response
-keep class com.pksafe.lock.manager.data.** { *; }
-keepclassmembers class com.pksafe.lock.manager.data.** { *; }
-keepnames class com.pksafe.lock.manager.data.** { *; }

# Keep the Retrofit API service interface
-keep interface com.pksafe.lock.manager.data.ApiService { *; }
-keep class com.pksafe.lock.manager.data.ApiService { *; }

# ──────────────────────────────────────────────────────────────────────────────
# KOTLIN COROUTINES
# ──────────────────────────────────────────────────────────────────────────────
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ──────────────────────────────────────────────────────────────────────────────
# FIREBASE
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ──────────────────────────────────────────────────────────────────────────────
# ANDROID DEVICE ADMIN (Enterprise)
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.pksafe.lock.manager.receiver.AdminReceiver { *; }
-keep public class * extends android.app.admin.DeviceAdminReceiver {
    <init>(...);
    void on*(...);
}

# ──────────────────────────────────────────────────────────────────────────────
# ANDROID COMPONENTS
# ──────────────────────────────────────────────────────────────────────────────
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ──────────────────────────────────────────────────────────────────────────────
# MAPS / LOCATION
# ──────────────────────────────────────────────────────────────────────────────
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }
