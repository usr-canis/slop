# Car App Library uses reflection-free bundling; keep model classes used across the host boundary.
-keep class androidx.car.app.** { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.telltale.** { *** Companion; }
-keepclasseswithmembers class app.telltale.** { kotlinx.serialization.KSerializer serializer(...); }
