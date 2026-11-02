# ---- Firestore: documents are (de)serialised by reflection into these classes ----
-keepclassmembers class com.asphalt.app.model.*FirebaseDoc { *; }
-keep class com.asphalt.app.model.*FirebaseDoc { <init>(...); }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# ---- Room (generated code is kept by the Room consumer rules; entities kept for safety) ----
-keep class com.asphalt.app.data.*Entity { *; }

# ---- Enums used with valueOf() on persisted strings ----
-keepclassmembers enum com.asphalt.app.model.** { public static **[] values(); public static ** valueOf(java.lang.String); }

# ---- Accessibility service is instantiated by the system ----
-keep class com.asphalt.app.service.AsphaltAccessibilityService { *; }
-keep class com.asphalt.app.service.AsphaltForegroundService { *; }

# Keep readable stack traces for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
