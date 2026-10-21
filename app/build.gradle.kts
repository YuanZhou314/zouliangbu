import java.util.Properties
import java.io.FileInputStream

// 版本自增：读 version.properties 的 buildNumber，打包后 +1 回写。
// 每次打包 versionName = 0.1.0.<buildNumber>（0.1.0.000001、0.1.0.000002...），
// versionCode = 10000 + buildNumber，保证覆盖安装时新包版本更高。
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        FileInputStream(versionPropsFile).use { load(it) }
    }
}
val buildNumber = versionProps.getProperty("buildNumber", "0").toInt() + 1

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // kotlinx-serialization 编译插件：没有它 @Serializable 只是空注解，运行期抛
    // "Serializer not found"，Remote*Repository 静默回退 Mock（P1 修复的关键）。
    // 版本必须与 Kotlin 1.9.24 严格一致。
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
    id("com.google.devtools.ksp")
}

// Room schema 导出（P2.1）：每版表结构存档到 app/schemas/，写 Migration 与迁移测试都靠它。
// 必须在 Room 注解处理之前以 KSP 参数形式生效，所以放在 plugins 之后、android 之前。
// 关掉 exportSchema 的话，历史 schema 一旦错过就再也补不回来。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 读取 local.properties 中的签名配置
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}

android {
    namespace = "com.zouliangbu.footprint"
    // compileSdk 必须 >= 34：Room 2.6.1 的传递依赖（androidx.sqlite:2.4.0、
    // annotation-experimental:1.4.0 等）AAR metadata 要求 API 34。
    // compileSdk 只决定编译期可用 API，与运行设备/模拟器的 API 级别无关，
    // 所以要跑 android-33 模拟器并不需要把 compileSdk 降到 33。
    compileSdk = 34

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("zlb-debug.keystore")
            storePassword = localProperties.getProperty("ZLB_STORE_PASSWORD", "zlb123456")
            keyAlias = localProperties.getProperty("ZLB_KEY_ALIAS", "zlb-debug")
            keyPassword = localProperties.getProperty("ZLB_KEY_PASSWORD", "zlb123456")
        }
    }

    defaultConfig {
        applicationId = "com.zouliangbu.footprint"
        minSdk = 26
        // targetSdk=33 保持在 android-33 模拟器上的运行时行为与之前一致
        targetSdk = 33
        versionCode = 10000 + buildNumber
        versionName = "0.1.0.${buildNumber.toString().padStart(6, '0')}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
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
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// 每次 assembleDebug 成功后把 buildNumber 回写，下次打包自动 +1。
val bumpBuildNumber = tasks.register("bumpBuildNumber") {
    doLast {
        versionProps["buildNumber"] = buildNumber.toString()
        versionPropsFile.writeBytes(
            "buildNumber=${buildNumber}\n".toByteArray(Charsets.UTF_8)
        )
        println("buildNumber -> $buildNumber")
    }
}
// 挂到 assemble 系列任务（assembleDebug/assembleRelease 都会触发）
tasks.matching { it.name.startsWith("assemble") }.configureEach {
    finalizedBy(bumpBuildNumber)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 导航：锁 2.7.7。禁止升 2.8+——其传递依赖会把 Compose 抬到 1.7.x，
    // 与 Kotlin 1.9.24 / compose compiler 1.5.14 组合未验证，有编译风险。
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // AMap (高德) Android SDKs.
// 3dmap 10.x 起已经把 AMapLocation/AMapLocationClient 等类内嵌在自身 jar 中（无 POM 传递依赖），
// 所以不再单独声明 com.amap.api:location，否则会触发 Duplicate class。
    implementation("com.amap.api:3dmap:10.0.600")

    // osmdroid：未配置高德 Key 时的过渡地图（OpenStreetMap 瓦片，无需 token）。
    // 与高德并存：MapViewWrapper 按 AmapKey.isConfigured() 路由，有 key 自动切回高德。
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Retrofit + OkHttp + kotlinx-serialization：内容接口对接（ROADMAP P6 S4）。
    // 仅在 Remote*Repository 里用，Mock 数据不受影响。
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coil 2.x：封面网络图加载（coverUrl 打通，P1）。不要用 coil3——API 不同。
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}