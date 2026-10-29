# kotlinx.serialization: keep generated serializers for backup models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.mymoney.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.mymoney.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mymoney.app.**$$serializer { *; }

# Room entities are referenced reflectively by generated code only through names.
-keep class com.mymoney.app.data.db.** { *; }
