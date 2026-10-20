# Add project specific ProGuard rules here.

# jlatexmath-android loads fonts, symbol tables and macros by reflection and from assets.
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }

# JSch instantiates its algorithm implementations by class name.
-keep class com.jcraft.jsch.** { *; }
-keep class org.starfall.generative.extservices.remote.AndroidSignatureEd25519 { *; }
-keep class org.starfall.generative.extservices.remote.AndroidKeyPairGenEdDSA { *; }
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn sun.security.x509.**

# JSch includes optional desktop integrations that are not packaged on Android.
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
