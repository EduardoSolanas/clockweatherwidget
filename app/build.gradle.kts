import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/**
 * Resolves a build secret from the environment, then local.properties, then [default].
 *
 * Blank counts as absent at every level. A GitHub secret that was never created still reaches
 * Gradle: the workflow expands it to an empty string rather than leaving the variable unset, so
 * a plain `?:` keeps that empty value and ships it. An empty AdMob id is fatal -- the SDK
 * rejects it while content providers are installed, which is before Application.onCreate, so
 * the app, the widget provider and the worker all die on every process start.
 */
fun resolveBuildSecret(name: String, default: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: default

val googleWeatherApiKey: String = resolveBuildSecret("GOOGLE_WEATHER_API_KEY", "")

val defaultWeatherProvider: String = resolveBuildSecret("DEFAULT_WEATHER_PROVIDER", "GOOGLE")

// Google's sample ids are valid, show test ads, and earn nothing. They keep a misconfigured
// build runnable instead of crash-on-launch; AdMobConfigurationTest is what keeps a blank id
// from reaching the Play Store.
val admobAppId: String =
    resolveBuildSecret("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")

val admobInterstitialAdUnitId: String =
    resolveBuildSecret("ADMOB_INTERSTITIAL_AD_UNIT_ID", "ca-app-pub-3940256099942544/1033173712")

android {
    namespace = "com.clockweather.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.clockweather.app"
        minSdk = 26
        targetSdk = 36
        
        val vMajor = project.property("VERSION_MAJOR").toString()
        val vMinor = project.property("VERSION_MINOR").toString()
        val vPatch = project.property("VERSION_PATCH").toString()

        versionCode = (vMajor.toInt() * 10000) + (vMinor.toInt() * 100) + vPatch.toInt()
        versionName = "$vMajor.$vMinor.$vPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "GOOGLE_WEATHER_API_KEY", "\"$googleWeatherApiKey\"")
        buildConfigField("String", "DEFAULT_WEATHER_PROVIDER", "\"$defaultWeatherProvider\"")
        buildConfigField("String", "ADMOB_APP_ID", "\"$admobAppId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"$admobInterstitialAdUnitId\"")
    }

    buildTypes {
        debug {
            // Tester builds never serve ads: no SDK init, no consent form, no impressions.
            buildConfigField("Boolean", "ADS_ENABLED", "false")
        }
        release {
            buildConfigField("Boolean", "ADS_ENABLED", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk { debugSymbolLevel = "NONE" }
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = false  // Don't abort on errors, just warn
        warningsAsErrors = false  // Keep warnings as warnings
        checkReleaseBuilds = true
        baseline = file("lint-baseline.xml")
        disable += listOf(
            "GradleDependency",      // Allow gradle dependency warnings
            "Instantiatable",        // Allow abstract classes in dagger
            "BatteryLife"            // Widget needs battery exemption for reliability
        )
        // Enable strict checks for compose and code quality
        enable += listOf(
            "UnrememberedMutableState",       // Catch mutableState without remember
            "ObsoleteSdkInt",                 // Flag old SDK checks
            "MissingPermission"               // Ensure permissions are checked
        )
    }
}

dependencies {
    // Kotlin & Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Retrofit + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Location
    implementation(libs.play.services.location)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Google Mobile Ads (AdMob) & Consent (UMP)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation("androidx.work:work-testing:2.10.0")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
