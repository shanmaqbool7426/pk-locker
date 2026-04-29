# Retrofit and OkHttp
-keepattributes Signature, InnerClasses, AnnotationDefault
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# GSON Models (Keep your data classes)
-keep class com.example.pklocker.data.** { *; }
-keepclassmembers class com.example.pklocker.data.** { *; }

# Firebase Messaging
-keep class com.google.firebase.** { *; }

# Serialization
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}