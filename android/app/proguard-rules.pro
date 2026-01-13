# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Room entities
-keep class com.browserselector.android.model.** { *; }

# Keep Room database
-keep class * extends androidx.room.RoomDatabase

# Keep Room DAOs
-keep interface com.browserselector.android.data.*Dao { *; }
