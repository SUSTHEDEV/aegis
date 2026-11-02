# LSPosed entrypoints are looked up reflectively - never strip them
-keep class com.susthedev.aegis.XposedMain { *; }
-keep class com.susthedev.aegis.XposedHelper { *; }
-keep class com.susthedev.aegis.Config { *; }
-keep class com.susthedev.aegis.RulesStore { *; }
-keep class com.susthedev.aegis.hook.** { *; }
