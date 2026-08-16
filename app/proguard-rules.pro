-keeppackagenames org.jsoup.nodes

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses

-keepclassmembers class com.RobinNotBad.BiliClient.model.** {
    <fields>;
    <init>(...);
}

-keep class master.flame.danmaku.** { *; }
-keep class tv.danmaku.ijk.media.** { *; }
-keep class com.netease.hearttouch.brotlij.** { *; }

-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}

# Retrofit
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class kotlin.coroutines.Continuation

# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.RobinNotBad.BiliClient.network.model.**$$serializer { *; }
-keepclassmembers class com.RobinNotBad.BiliClient.network.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.RobinNotBad.BiliClient.network.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# PhotoView
-keep class com.github.chrisbanes.photoview.** { *; }

# Protobuf
-keep class com.RobinNotBad.BiliClient.model.DmSegMobileReply { *; }
-keep class com.RobinNotBad.BiliClient.model.DanmakuElem { *; }

# Custom Views (used in XML layouts via reflection)
-keep class com.RobinNotBad.BiliClient.ui.widget.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep class com.RobinNotBad.BiliClient.BiliTerminalApp
-keep class com.RobinNotBad.BiliClient.BiliTerminal
-keep class com.RobinNotBad.BiliClient.ErrorCatch

# Activities, Services, and other components (keep names for Class.forName)
-keep class com.RobinNotBad.BiliClient.activity.** { *; }
-keep class com.RobinNotBad.BiliClient.service.** { *; }
-keep class com.RobinNotBad.BiliClient.ui.** { *; }

# Geetest (极验) 安全验证 SDK 混淆规则
-keep class com.geetest.sdk.** { *; }
-dontwarn com.geetest.sdk.**
-keep class com.geetest.sensebot.** { *; }
-dontwarn com.geetest.sensebot.**

# R8 optimization passes
-optimizationpasses 5
# repackageclasses 与 mergeinterfacesaggressively 可能破坏运行时反射（EventBus/Hilt/Glide），
# 首次开启 R8 暂禁用以降低风险，确认稳定后可恢复。
#-repackageclasses
#-allowaccessmodification
#-mergeinterfacesaggressively