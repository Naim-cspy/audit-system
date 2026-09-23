# Proguard rules for Room and Coroutines
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
