plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.ktfmt)
}

android {
  compileSdk = 36

  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  defaultConfig {
    applicationId = "com.herrerad85.afterglow"
    minSdk = 23
    targetSdk = 36
    versionCode = 4003
    versionName = "v4.0.3"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField("int", "LATEST_RELEASE", "77")
    buildConfigField("String", "GITHUB_TOKEN", "\"\"")
    resourceConfigurations += setOf("en", "zh-rCN", "es")
  }

  buildFeatures { buildConfig = true }

  signingConfigs {
    create("release") {
      storeFile = System.getenv("KEYSTORE_PATH")?.let { file(it) }
      storePassword = System.getenv("KEYSTORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS")
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    debug { isMinifyEnabled = false }
    release {
      signingConfig = signingConfigs.getByName("release")
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro",
          "proguard-square.pro",
          "proguard-support.pro",
          "proguard-rx.pro",
      )
    }
  }

  // Robolectric reads the merged app resource table only when AGP packages it for the
  // unit-test variant. Without this the Preferences/auth characterization tests throw
  // Resources$NotFoundException on R.string.* lookups. (S0-01: required Stage-1 setup.)
  testOptions { unitTests { isIncludeAndroidResources = true } }

  lint {
    htmlReport = false
    xmlReport = false
    textReport = true
    lintConfig = file("${rootProject.rootDir}/lint.xml")
    abortOnError = true
    explainIssues = false
    absolutePaths = false
  }

  namespace = "com.growse.android.io.github.hidroh.materialistic"
}

kotlin { jvmToolchain(21) }

// S0-06: export the Room schema so DB migrations are verifiable.
// The generated app/schemas/ JSON is committed by a later stage.
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }

dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.cardview)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.material)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.browser)
  implementation(libs.dagger)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.gson)
  implementation(libs.retrofit.adapter.rxjava)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging.interceptor)
  implementation(libs.rxandroid)
  implementation(libs.rxjava)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.compiler)

  kapt(libs.androidx.room.compiler)
  kapt(libs.dagger.compiler)
  kaptTest(libs.androidx.room.compiler)
  kaptTest(libs.dagger.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)

  androidTestImplementation(libs.kaspresso)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
}
