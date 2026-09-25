# Proguard rules for Room, Coroutines, and Data Models
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Room entity classes and fields
-keep class com.example.data.model.** { *; }

# Keep DAOs
-keep interface com.example.data.dao.** { *; }
