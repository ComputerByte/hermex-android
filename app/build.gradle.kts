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
        versionCode = 31
        versionName = "0.12.6-preview"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Local signing.properties is gitignored and points at a keystore stored OUTSIDE this repo
    // (see SIGNING.md). `storeFile` may be an absolute path -- rootProject.file() passes
    // absolute paths through unchanged, so keeping the keystore off-disk-under-the-repo entirely
    // is the default expectation, not just a convention.
    val signingPropsFile = rootProject.file("signing.properties")
    val hasSigningProps = signingPropsFile.exists()

    // Accept both spec field names (storeFile/storePassword/keyAlias/keyPassword) and the
    // existing convention (keystore/keystore.password/key.alias/key.password).
    val signingProps: Properties? = if (hasSigningProps) {
        Properties().apply { signingPropsFile.inputStream().use { stream -> load(stream) } }
    } else null
    val storeFilePath: String = signingProps?.getProperty("storeFile") ?: signingProps?.getProperty("keystore") ?: ""
    val storePasswordValue: String = signingProps?.getProperty("storePassword") ?: signingProps?.getProperty("keystore.password") ?: ""
    val keyAliasName: String = signingProps?.getProperty("keyAlias") ?: signingProps?.getProperty("key.alias") ?: ""
    val keyPasswordValue: String = signingProps?.getProperty("keyPassword") ?: signingProps?.getProperty("key.password") ?: ""

    // Every field present and non-blank, *and* the keystore file it points at actually exists --
    // a signing.properties with a typo'd or moved storeFile path is exactly as broken as a
    // missing signing.properties, and must fail the same way rather than passing a bogus
    // File() through to AGP and failing later with a more confusing error.
    val signingConfigComplete = hasSigningProps &&
        storeFilePath.isNotBlank() && storePasswordValue.isNotBlank() &&
        keyAliasName.isNotBlank() && keyPasswordValue.isNotBlank() &&
        rootProject.file(storeFilePath).exists()

    if (signingConfigComplete) {
        val releaseSigning = signingConfigs.create("release")
        releaseSigning.storeFile = rootProject.file(storeFilePath)
        releaseSigning.storePassword = storePasswordValue
        releaseSigning.keyAlias = keyAliasName
        releaseSigning.keyPassword = keyPasswordValue
    }

    // Release artifacts (assemble/bundle) MUST be signed -- a release build with incomplete or
    // missing signing config fails loudly here instead of silently producing an unsigned APK
    // (which Android would refuse to install anyway, but only after a confusing failure far from
    // the actual cause) or falling back to any other signing identity. Debug builds, tests, and
    // every other task are unaffected -- this only fires when a release artifact is actually
    // requested.
    gradle.taskGraph.whenReady {
        val buildingReleaseArtifact = allTasks.any { task ->
            task.path.contains(":app:") &&
                (task.name.startsWith("assembleRelease") || task.name.startsWith("bundleRelease") ||
                    task.name.startsWith("packageRelease") || task.name.contains("ReleaseBundle"))
        }
        if (buildingReleaseArtifact && !signingConfigComplete) {
            throw GradleException(
                "Refusing to build a release artifact: signing.properties is missing, incomplete, " +
                "or its storeFile does not exist at the resolved path. Release artifacts must be " +
                "signed with the real release/upload keystore -- see SIGNING.md for local " +
                "signing setup. This build type never falls back to an unsigned or debug-signed " +
                "output.",
            )
        }
    }

    buildTypes {
        release {
            // R8 minification is always on; signing is only applied if signing.properties
            // resolves to a complete, existing keystore -- see the taskGraph check above for
            // what happens when it doesn't.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigComplete) {
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
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

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
