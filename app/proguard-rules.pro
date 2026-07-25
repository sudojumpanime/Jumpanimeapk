# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep WebView JS interface classes and methods
-keep class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Retrofit & OkHttp
-keep,allowobfuscation interface * extends retrofit2.Call
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Keep Kotlinx Serialization and Navigation routes
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep @"kotlinx.serialization.Serializable" class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep Room Database entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.Dao *;
}

# Keep Data Models
-keep class com.example.data.** { *; }
-keep class com.example.model.** { *; }

# Ignore warnings for optional dependencies in Tink / Google APIs / Joda Time
-dontwarn com.google.api.client.**
-dontwarn com.google.crypto.tink.**
-dontwarn org.joda.time.**

