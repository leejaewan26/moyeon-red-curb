import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 서명 키는 저장소 밖(../keystore/keystore.properties)에서 읽는다. 파일이 없으면 서명 없이 빌드한다.
val keystorePropsFile = rootProject.file("../keystore/keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.reader(Charsets.UTF_8).use { load(it) } // 경로에 한글이 있어 UTF-8 로 읽는다
}
val hasReleaseKey = keystorePropsFile.exists() && keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.moyeon.redcurb"
    compileSdk = 35
    // 기어: AAOS 표준 CarPropertyManager(GEAR_SELECTION). android.car 는 플랫폼 공용 라이브러리라 컴파일에만 쓴다.
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.moyeon.redcurb"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "VEHICLE_SDK_VERSION", "\"${libs.versions.vehicleSdk.get()}\"")
        buildConfigField("String", "FUSED_LOCATION_SDK_VERSION", "\"${libs.versions.fusedLocationSdk.get()}\"")
        buildConfigField("String", "TTS_SDK_VERSION", "\"${libs.versions.ttsSdk.get()}\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                val f = keystorePropsFile.parentFile.resolve(keystoreProps.getProperty("storeFile"))
                storeFile = if (f.exists()) f else file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.vehicle.sdk)
    implementation(libs.fused.location.sdk)
    implementation(libs.tts.sdk)
    implementation(libs.kotlin.reflect)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
