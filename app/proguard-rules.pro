# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android-optimize.txt
# You can edit the include path by changing the proguardFiles setting in build.gradle.kts.

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hermex.android.**$$serializer { *; }
-keepclassmembers class com.hermex.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.hermex.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Room entities, DAOs, and database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Coil
-dontwarn coil3.**
-keep class coil3.** { *; }

# Keep Markwon
-keep class io.noties.markwon.** { *; }
-keep class io.noties.markwon.ext.** { *; }

# Keep Security Crypto / Tink
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn org.joda.time.**
-dontwarn com.google.api.client.**
-dontwarn com.google.http.client.**

# Keep reflection-based SpeechRecognizer callbacks
-keep class com.hermex.android.chat.VoiceInputHandler { *; }
-keepclassmembers class * {
    @android.speech.SpeechRecognizer *;
}

# Keep widget receivers (manifest-declared AppWidgetProvider -- R8 already keeps manifest
# components automatically, but this makes the intent explicit).
#
# Notification classes (com.hermex.android.core.notifications.**) previously had a keep rule here
# under a nonexistent com.hermex.android.notification.** package (note: singular, missing the
# "core." prefix -- it matched nothing). They don't need one: none of them are manifest-declared,
# none are looked up via reflection/Class.forName, and they're all plainly referenced from Kotlin
# call sites R8 already traces (AppContainer, ChatViewModel, etc.), so normal reachability keeps
# what's used and correctly shrinks what isn't.
-keep class com.hermex.android.widget.** { *; }

# Keep Hermex data classes
-keep class com.hermex.android.core.network.dto.** { *; }
-keep class com.hermex.android.chat.** { *; }
-keep class com.hermex.android.sessions.** { *; }

# General Android
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepnames class * implements android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Logging: strip verbose/debug/info logs in release, keep warning/error
# HermexLog uses android.util.Log internally
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}