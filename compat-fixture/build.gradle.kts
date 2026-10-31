plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Local-only representative apps, never included in DK App Blocker's APK.
android {
    namespace = "com.dk.compatfixture"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dk.compatfixture"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    flavorDimensions += "fixture"
    productFlavors {
        create("financeOne") { dimension = "fixture"; applicationIdSuffix = ".financeone"; manifestPlaceholders["fixtureLabel"] = "Finance fixture A" }
        create("financeTwo") { dimension = "fixture"; applicationIdSuffix = ".financetwo"; manifestPlaceholders["fixtureLabel"] = "Finance fixture B" }
        create("social") { dimension = "fixture"; applicationIdSuffix = ".social"; manifestPlaceholders["fixtureLabel"] = "Distraction fixture" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
