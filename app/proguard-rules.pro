# Retrofit and OkHttp
-keepattributes Signature, InnerClasses, AnnotationDefault
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# GSON Models (Keep your data classes)
-keep class com.pksafe.lock.manager.data.** { *; }
-keepclassmembers class com.pksafe.lock.manager.data.** { *; }

# Firebase Messaging
-keep class com.google.firebase.** { *; }

# Serialization
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Android Enterprise / Device Admin
# Keep the AdminReceiver and its methods so the system can find it
-keep class com.pksafe.lock.manager.receiver.AdminReceiver { *; }
-keep public class * extends android.app.admin.DeviceAdminReceiver {
    <init>(...);
    void on*(...);
}

# Keep all Manifest components to prevent obfuscation of class names
# referenced in the QR code or by the system
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
