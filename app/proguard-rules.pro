# ProGuard rules for CallReport
-keep class com.callreport.app.** { *; }
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}
-keep class * extends android.app.Service {
   <init>(...);
}
-keep class * extends android.telecom.CallScreeningService {
   <init>(...);
}
-dontwarn java.lang.invoke.**
