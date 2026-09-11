plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val wallcrawlVersionCode = providers.gradleProperty("wallcrawl.versionCode")
    .orElse("1")
    .map { it.toInt() }
    .get()
val wallcrawlVersionName = providers.gradleProperty("wallcrawl.versionName")
    .orElse("0.1.0-dev")
    .get()

require(wallcrawlVersionCode > 0) {
    "wallcrawl.versionCode must be a positive integer"
}
require(wallcrawlVersionName.isNotBlank()) {
    "wallcrawl.versionName must not be empty"
}

android {
    namespace = "wallcrawl.elopenmike.com"
    compileSdk = 37

    defaultConfig {
        applicationId = "wallcrawl.elopenmike.com"
        minSdk = 26
        targetSdk = 35
        versionCode = wallcrawlVersionCode
        versionName = wallcrawlVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Generates res/xml/locale_config.xml from the values-* directories that actually
        // ship, and injects android:localeConfig. That is what makes WallCrawl appear in
        // the system per-app language screen, and it cannot drift from the resources the
        // way a hand-maintained list would. The default locale comes from
        // src/main/res/resources.properties.
        generateLocaleConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets {
        getByName("test") {
            resources.srcDir("src/main/assets")
            resources.srcDir("src/androidTest/assets")
        }
    }
}

dependencies {
    // Carries the per-app language APIs back to minSdk 26. On API 33+ AppCompatDelegate
    // delegates to the platform LocaleManager, so the app selector and the system per-app
    // language screen always report the same choice.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.json)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.truth)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// SafetyCopyTest and the reviewed-coverage suite read shipped copy and authored data straight
// from disk with `File(...)`, outside Gradle's tracked inputs. Without declaring them, a
// data-only edit leaves the test task UP-TO-DATE and those checks silently do not run — so a
// claim they exist to catch could ship. CI restores the Gradle cache, so this is not
// local-only. Only genuinely untracked paths belong here: `src/main/assets` is already a test
// resources root above, and sources read as text (WallCrawlApplication.kt) recompile, which
// invalidates the task on their own. Add a glob when a test starts reading a new outside path.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree("src/main/res") { include("**/strings.xml") },
        fileTree(rootProject.file("docs/research")) { include("**/*.json") },
        rootProject.file("tools/workout-guide/review-schema.json")
    ).withPropertyName("shippedCopyAndAuthoredContracts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
