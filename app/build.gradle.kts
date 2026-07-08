import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.hermex.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hermex.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "0.12.1-preview"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Local signing.properties is gitignored. When present, it enables signed release builds.
    // When absent, release builds still R8-minify but produce an unsigned APK that Android
    // will refuse to install — a clear warning is logged.
    val signingPropsFile = rootProject.file("signing.properties")
    val hasSigningProps = signingPropsFile.exists()

    if (hasSigningProps) {
        val props = Properties()
        signingPropsFile.inputStream().use { stream -> props.load(stream) }
        // Accept both spec field names (storeFile/storePassword/keyAlias/keyPassword) and
        // the existing convention (keystore/keystore.password/key.alias/key.password).
        val storeFilePath: String = props.getProperty("storeFile") ?: props.getProperty("keystore") ?: ""
        val storePassword: String = props.getProperty("storePassword") ?: props.getProperty("keystore.password") ?: ""
        val keyAliasName: String = props.getProperty("keyAlias") ?: props.getProperty("key.alias") ?: ""
        val keyPasswordValue: String = props.getProperty("keyPassword") ?: props.getProperty("key.password") ?: ""

        val releaseSigning = signingConfigs.create("release")
        releaseSigning.storeFile = rootProject.file(storeFilePath)
        releaseSigning.storePassword = storePassword
        releaseSigning.keyAlias = keyAliasName
        releaseSigning.keyPassword = keyPasswordValue
    } else {
        logger.warn(
            "signing.properties not found at repo root. Release builds will be UNSIGNED " +
            "and Android will refuse to install them. Create signing.properties (gitignored) " +
            "to enable signed release APK output.",
        )
    }

    buildTypes {
        release {
            // R8 minification is always on; signing is only applied if signing.properties exists.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningProps) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // android.util.Log throws "not mocked" by default in JVM unit tests; HermexLog is
            // called from code under test (ChatViewModel, AuthRepository, SseClient), so make
            // stubbed Android methods no-op instead of throwing rather than mocking Log everywhere.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Local storage
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Offline cache -- Room, for structured/queryable local data (session lists, message
    // history) that DataStore's key-value model isn't suited for.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Security Crypto / Tink — needs error prone annotations for R8
    implementation("com.google.errorprone:error_prone_annotations:2.36.0")
    implementation("com.google.http-client:google-http-client-gson:1.44.2")

    // Markdown rendering
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
    testImplementation("app.cash.turbine:turbine:1.2.1")

    // Instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
