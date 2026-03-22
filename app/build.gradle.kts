plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.highlightcam.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.highlightcam.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
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

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.configureEach {
        if (buildType.name == "debug") {
            outputs.configureEach {
                (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                    ?.outputFileName = "highlightcam-debug.apk"
            }
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

tasks.register("copyModelAsset") {
    val src = rootProject.file("assets/yolov8n_float16.tflite")
    val dst = file("src/main/assets/yolov8n_float16.tflite")
    onlyIf { src.exists() && (!dst.exists() || dst.length() != src.length()) }
    doLast {
        dst.parentFile.mkdirs()
        src.copyTo(dst, overwrite = true)
        println("Model asset copied: ${dst.absolutePath}")
    }
}

tasks.configureEach {
    if (name == "mergeDebugAssets" || name == "mergeReleaseAssets") {
        dependsOn("copyModelAsset")
    }
}

tasks.register<Copy>("copyApkToDist") {
    from("build/outputs/apk/debug/highlightcam-debug.apk")
    into(rootProject.file("dist"))
}

tasks.configureEach {
    if (name == "assembleDebug") {
        finalizedBy("copyApkToDist")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.windowsizeclass)
    implementation(libs.compose.ui.text.google.fonts)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.camerax.video)

    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.support)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.service)

    implementation(libs.coroutines.android)
    implementation(libs.accompanist.permissions)
    implementation(libs.datastore.preferences)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.datastore.preferences)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}
