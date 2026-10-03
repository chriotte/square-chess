# JNI exports in native/fairy_bridge.cpp use this exact class and method names.
-keepclasseswithmembers,includedescriptorclasses class com.dataespresso.squarechess.NativeEngine {
    native <methods>;
}

# Room supplies consumer rules for its generated database implementation.
# Chesslib is called directly and does not need a package-wide keep rule.
