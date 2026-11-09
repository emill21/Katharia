import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The sweep engine is shared with the phone app: these files are copied in at build time,
// so a fix on the phone side reaches the watch too.
val phoneShared = listOf("SweepService.kt", "Sweep.kt", "AppRepo.kt", "Prefs.kt", "SettingsText.kt", "AccessKeeper.kt", "SweepLauncher.kt", "Changelog.kt")
val syncPhoneShared by tasks.registering(Sync::class) {
    from(rootProject.file("app/src/main/java")) {
        phoneShared.forEach { include("com/katharia/app/$it") }
    }
    into(layout.buildDirectory.dir("generated/phoneShared"))
}

android {
    namespace = "com.katharia.app.wear"
    compileSdk = 35

    defaultConfig {
        // Same id as the phone app, so the two are recognised as one app.
        applicationId = "com.katharia.app"
        minSdk = 30
        targetSdk = 35
        versionCode = 16
        versionName = "1.0"
    }

    sourceSets["main"].java.srcDir(layout.buildDirectory.dir("generated/phoneShared"))

    val signing = Properties().apply {
        rootProject.file("keystore/signing.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        create("katharia") {
            storeFile = rootProject.file(signing.getProperty("storeFile", "keystore/katharia.jks"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("katharia")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

tasks.named("preBuild") { dependsOn(syncPhoneShared) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")

    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("com.google.guava:guava:33.3.1-android")
}
