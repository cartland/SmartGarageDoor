# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve source info for readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations (@Serializable, @SerialName, etc.) at runtime.
-keepattributes *Annotation*, InnerClasses

# ----------------------------------------------------------------
# kotlinx.serialization
# ----------------------------------------------------------------
# The runtime jar ships consumer rules that cover the common cases, but these
# add belt-and-suspenders keeps for the Companion + $$serializer that the
# generated code relies on. Missing these produces silent deserialization
# failures (all @Serializable Nullable fields parse as null → default),
# which is exactly the failure mode we hit with the snooze POST response
# before this file existed.

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keep class <1>$$serializer { *; }

-if @kotlinx.serialization.Serializable class **
-keep class <1>$Companion { *; }

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ----------------------------------------------------------------
# Ktor client data classes
# ----------------------------------------------------------------
# Ktor's `response.body<T>()` relies on T's generated serializer. Be explicit
# about our Ktor response types so R8 can't strip fields or rename in ways
# that confuse the generated serializer.
-keep class com.chriscartland.garage.data.ktor.** { *; }
-keepclassmembers class com.chriscartland.garage.data.ktor.** { *; }
