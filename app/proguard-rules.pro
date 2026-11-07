# Gson 反射用到的模型类不能被裁剪/重命名
-keep class com.ly.toolbox.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
