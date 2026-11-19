# NewPipeExtractor (rules from its README)
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# Keep line numbers in crash reports so they stay readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile