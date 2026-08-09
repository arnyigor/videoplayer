import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.secrets)
    alias(libs.plugins.kotlin.serialization)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.arny.mobilecinema"
    testBuildType = "release"

    signingConfigs {
        create("release")
    }

    compileSdk = libs.versions.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.buildTools.get()

    val versionPropsFile = rootProject.file("version.properties")
    val versionProps = Properties()
    if (versionPropsFile.canRead()) {
        versionPropsFile.inputStream().use(versionProps::load)
    } else {
        versionProps.setProperty("VERSION_MAJOR", "1")
        versionProps.setProperty("VERSION_MINOR", "0")
        versionProps.setProperty("VERSION_BUILD", "0")
    }
    val vMajor = versionProps["VERSION_MAJOR"].toString().toInt()
    val vMinor = versionProps["VERSION_MINOR"].toString().toInt()
    val vBuild = versionProps["VERSION_BUILD"].toString().toInt()

    defaultConfig {
        applicationId = "com.arny.mobilecinema"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = vMajor * 100 + vMinor * 10 + vBuild
        versionName = "$vMajor.$vMinor.$vBuild"
        setProperty("archivesBaseName", "$applicationId-v$versionName($versionCode)")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        javaCompileOptions {
            annotationProcessorOptions {
                argument("dagger.experimentalDaggerErrorMessages", "enabled")
                argument("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/ASL2.0",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/LICENSE.txt",
                "META-INF/MANIFEST.MF",
            )
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("src/main/kotlin")
        }
        getByName("test") {
            java.srcDir("src/test/kotlin")
            resources.srcDir("src/unitTests/resources")
        }
        getByName("androidTest") {
            java.srcDir("src/androidTest/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            resValue("string", "app_name", "Anwap Movies")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            testProguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguardTest-rules.pro")
        }

        debug {
            resValue("string", "app_name", "[DEBUG]Anwap Movies")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isDebuggable = true
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            testProguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguardTest-rules.pro")
        }
    }
}

android.applicationVariants.all {
    outputs.all {
        val output = this as BaseVariantOutputImpl
        if (output.outputFileName.endsWith(".apk")) {
            output.outputFileName = "mobilecinema-${versionName}-${buildType.name}.apk"
        }
    }
}

val signingPropertyNames = listOf(
    "ANDROID_KEYSTORE_PATH",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)
val legacySigningPropertyNames = listOf("STORE_FILE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
val signingProperties = Properties()
val signingPropertiesFile = rootProject.file("signing.properties")
if (signingPropertiesFile.canRead()) {
    signingPropertiesFile.inputStream().use(signingProperties::load)
}

var signingValues = signingPropertyNames.associateWith { propertyName ->
    val environmentValue = System.getenv(propertyName)
    val fileValue = signingProperties.getProperty(propertyName)
    if (!environmentValue.isNullOrBlank()) environmentValue else fileValue
}.toMutableMap()
val configuredSigningProperties = signingValues.filterValues { !it.isNullOrBlank() }.keys
val configuredModernFileProperties = signingPropertyNames.filter { propertyName ->
    !signingProperties.getProperty(propertyName).isNullOrBlank()
}
val configuredLegacySigningProperties = legacySigningPropertyNames.filter { propertyName ->
    !signingProperties.getProperty(propertyName).isNullOrBlank()
}
if (configuredSigningProperties.isNotEmpty() && configuredSigningProperties.size != signingPropertyNames.size) {
    val missingProperties = signingPropertyNames - configuredSigningProperties
    throw GradleException("Incomplete release signing configuration. Missing: ${missingProperties.joinToString(", ")}")
}
if (configuredSigningProperties.isEmpty() && configuredLegacySigningProperties.isNotEmpty() &&
    configuredLegacySigningProperties.size != legacySigningPropertyNames.size
) {
    val missingProperties = legacySigningPropertyNames - configuredLegacySigningProperties
    throw GradleException("Incomplete legacy release signing configuration. Missing: ${missingProperties.joinToString(", ")}")
}
if (configuredModernFileProperties.isNotEmpty() && configuredLegacySigningProperties.isNotEmpty()) {
    throw GradleException("Mixed release signing schemas are not supported")
}

val legacySigningConfigured = configuredSigningProperties.isEmpty() &&
    configuredLegacySigningProperties.size == legacySigningPropertyNames.size
if (legacySigningConfigured) {
    signingValues = mutableMapOf(
        "ANDROID_KEYSTORE_PATH" to signingProperties.getProperty("STORE_FILE"),
        "ANDROID_KEYSTORE_PASSWORD" to signingProperties.getProperty("STORE_PASSWORD"),
        "ANDROID_KEY_ALIAS" to signingProperties.getProperty("KEY_ALIAS"),
        "ANDROID_KEY_PASSWORD" to signingProperties.getProperty("KEY_PASSWORD"),
    )
}

val releaseSigningConfigured = configuredSigningProperties.size == signingPropertyNames.size || legacySigningConfigured
if (releaseSigningConfigured) {
    val configuredKeystore = file(signingValues.getValue("ANDROID_KEYSTORE_PATH")!!)
    val resolvedKeystore = if (configuredKeystore.isAbsolute) {
        configuredKeystore
    } else {
        rootProject.file(signingValues.getValue("ANDROID_KEYSTORE_PATH")!!)
    }
    android.signingConfigs.getByName("release").storeFile = resolvedKeystore
    android.signingConfigs.getByName("release").storePassword = signingValues.getValue("ANDROID_KEYSTORE_PASSWORD")
    android.signingConfigs.getByName("release").keyAlias = signingValues.getValue("ANDROID_KEY_ALIAS")
    android.signingConfigs.getByName("release").keyPassword = signingValues.getValue("ANDROID_KEY_PASSWORD")
} else {
    android.buildTypes.getByName("release").signingConfig = null
}

gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { task ->
        task.project == project && task.name.lowercase().contains("release")
    }
    if (releaseRequested && !releaseSigningConfigured) {
        throw GradleException("Release signing is not configured. Provide all ANDROID_* values via the environment or ignored signing.properties.")
    }
}

secrets {
    propertiesFileName = "secrets.properties"
    ignoreList.add("keyToIgnore")
    ignoreList.add("sdk.*")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.coordinatorlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.koin.android)
    implementation(libs.koin.core)
    implementation(libs.material)
    implementation(libs.glide)
    ksp(libs.glide.ksp)
    implementation(libs.glide.okhttp3.integration) {
        exclude(group = "glide-parent")
    }
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.joda.time)
    implementation(libs.androidx.appcompat)
    implementation(libs.material.dialogs.core)
    implementation(libs.material.dialogs.input)
    implementation(libs.material.dialogs.color)
    implementation(libs.material.dialogs.bottomsheets)
    implementation(libs.material.dialogs.files)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.exoplayer)
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.common)
    implementation(libs.exoplayer.dash)
    implementation(libs.exoplayer.hls)
    implementation(libs.exoplayer.smoothstreaming)
    implementation(libs.exoplayer.rtsp)
    implementation(libs.exoplayer.extension.cronet)
    implementation(libs.exoplayer.extension.ima)
    implementation(libs.exoplayer.datasource)
    implementation(libs.exoplayer.ui)
    implementation(libs.exoplayer.extension.mediasession)
    implementation(libs.double.tap.player.view)
    implementation(libs.timber)
    implementation(libs.commons.codec)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.jsoup)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.room.paging)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.ffmpeg.kit.min)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.leanback.paging)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testCompileOnly(libs.junit.jupiter.engine)
    testImplementation(libs.guava.android)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.androidx.test.ext.junit.ktx)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
