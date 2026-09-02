# Add project specific ProGuard rules here.

# Keep Room entity and DAO implementations
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.example.data.model.** { *; }
-keep class com.example.browser.** { *; }

# WebKit / JavaScript Interfaces
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve Line Numbers for Crash Reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

