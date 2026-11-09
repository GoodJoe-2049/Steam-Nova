plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "steam.nova.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "steam.nova.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 57
        versionName = "1.6.2"

        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        // Miuix Navigation exposes inline functions compiled for JVM 21.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            // Rhino needs its Java resource bundles at runtime.
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val abis = android.defaultConfig.ndk.abiFilters.sorted().joinToString("+")
        variant.outputs.forEach { output ->
            @Suppress("UnstableApiUsage")
            output.outputFileName.set(
                output.versionName.map { version -> "SteamNova-v$version-$abis.apk" },
            )
        }
    }
}

dependencies {
    // The room-effect adapter parses public Steam JS in a bounded, Java-free interpreter.
    implementation(libs.rhino)
    implementation(libs.okhttp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.lottie)
    implementation(libs.material)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.zxing.core)
    implementation(libs.shizuku.api)
    implementation(libs.dhizuku.api)
    implementation(libs.hiddenapibypass)
    compileOnly(libs.libxposed.api)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
