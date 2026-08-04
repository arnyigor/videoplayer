# androidTest includes Mockito/ByteBuddy artifacts that reference JVM-only APIs.
# They are not used on device during these integration tests, but R8 still sees them.
-keep class androidx.test.** { *; }
-keep class org.junit.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn java.lang.instrument.ClassDefinition
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn java.lang.instrument.IllegalClassFormatException
-dontwarn java.lang.instrument.Instrumentation
-dontwarn java.lang.instrument.UnmodifiableClassException
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn javax.tools.JavaCompiler
-dontwarn javax.tools.ToolProvider
-dontwarn org.mockito.internal.creation.bytebuddy.inject.MockMethodDispatcher
-dontwarn org.newsclub.net.unix.AFUNIXSocket
-dontwarn org.newsclub.net.unix.AFUNIXSocketAddress
-dontwarn org.opentest4j.AssertionFailedError
