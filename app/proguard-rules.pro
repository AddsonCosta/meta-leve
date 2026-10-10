# kotlinx.serialization: mantém os serializers gerados das classes de backup e assets.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.addsoncosta.evolui.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.addsoncosta.evolui.** {
    kotlinx.serialization.KSerializer serializer(...);
}
