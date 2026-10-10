# kotlinx.serialization: mantém os serializers gerados das classes de backup e assets.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class app.metaleve.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.metaleve.** {
    kotlinx.serialization.KSerializer serializer(...);
}
