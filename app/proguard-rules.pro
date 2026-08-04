# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

-dontusemixedcaseclassnames
-verbose
-dontoptimize

# Глобальные базовые правила
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Для нативных методов
-keepclasseswithmembernames class * {
    native <methods>;
}

# View setters (чтобы работали анимации)
-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

# Activity onClick (из XML)
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# Перечисления (Enums)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# R классы ресурсов
-keepclassmembers class **.R$* {
    public static <fields>;
}
-dontwarn android.support.**

# androidx Fragments
-keepnames class androidx.navigation.fragment.NavHostFragment
-keep class * extends androidx.fragment.app.Fragment{}

# ==========================================
# Модели приложения (Gson/Ktor ломаются без этого)
# ==========================================
# (Заменил твое слишком широкое правило на domain.**, чтобы обфускация реально работала)
-dontwarn com.arny.mobilecinema.domain.**
-keep class com.arny.mobilecinema.domain.models.** { *; }
-keep class com.arny.mobilecinema.data.db.models.** { *; }
-keep class com.arny.mobilecinema.data.models.** { *; }

# Если используете Kotlinx.Serialization
-keepclassmembers class com.arny.mobilecinema.** {
    @kotlinx.serialization.Serializable <fields>;
}

# ==========================================
# Ktor & Coroutines
# ==========================================
-keep class kotlinx.serialization.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.internal.**
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.** { *; }

# ==========================================
# Room (полные правила)
# ==========================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ==========================================
# FFmpegKit
# ==========================================
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.antonkarpenko.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-dontwarn com.antonkarpenko.ffmpegkit.**

# ==========================================
# ExoPlayer
# ==========================================
-keep class com.google.android.exoplayer2.** { *; }
-keep class com.google.android.exoplayer2.ext.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ==========================================
# Сторонние библиотеки
# ==========================================
# protobuf
-dontwarn com.google.protobuf.**
-keep class com.google.protobuf.** { *; }

# slf4j
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# Retrofit
-dontwarn retrofit.**
-keep class retrofit.** { *; }

# OkHttp3
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontnote okhttp3.**

# Gson / Unsafe
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# Okio
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Joda Time
-keep class net.danlew.android.joda.R$raw { *; }
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString
-keepnames class org.joda.** implements java.io.Serializable
-keepclassmembers class org.joda.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}