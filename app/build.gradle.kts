// The Kleidi Tenant Android app. Product flavors mirror the staff app and the tenant iOS build
// configurations: dev (local server), stage, prod — one application id each, so all three can
// sit side by side on a phone.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "one.lab5.kleidi.tenant"
    compileSdk = 37

    defaultConfig {
        applicationId = "one.lab5.kleidi.tenant"
        minSdk = 26
        targetSdk = 36
        // Bump on every Play upload; the server's TENANT_ANDROID_MIN_SUPPORTED_BUILD compares against it
        // (sent as x-client-build).
        versionCode = 1
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            // The emulator reaches the host's localhost at 10.0.2.2.
            buildConfigField("String", "API_BASE_URL", quoted("http://10.0.2.2:3000"))
            buildConfigField("String", "ENVIRONMENT", quoted("dev"))
            resValue("string", "app_name", "Kleidi Tenant Dev")
        }
        create("stage") {
            dimension = "environment"
            applicationIdSuffix = ".stage"
            // TODO: the real stage URL (a placeholder in the staff apps too).
            buildConfigField("String", "API_BASE_URL", quoted("https://stage.kleidi.example.com"))
            buildConfigField("String", "ENVIRONMENT", quoted("stage"))
            resValue("string", "app_name", "Kleidi Tenant Stage")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", quoted("https://kleidi.seamphony.io"))
            buildConfigField("String", "ENVIRONMENT", quoted("prod"))
            resValue("string", "app_name", "Kleidi")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the upload key in CI / by the release manager; unsigned locally.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
