import com.github.jk1.license.render.TextReportRenderer

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.license.report)
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
    // Pre-store versioning (see CONTRIBUTING.md). versionCode = major*10000 + minor*100 + patch.
    versionCode = 10100
    versionName = "1.1.0"
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
    // No baseline: the 6 GestureBackNavigation onBackPressed errors were migrated to
    // OnBackPressedDispatcher in Slice 9 G3, so lint now runs clean and gates every new error.
    // Lint gates production code only. Test sources are excluded because AGP 9.2.1's analyzer
    // (Kotlin FIR) crashes resolving supertypes of Kotlin activity tests whose activity-under-test
    // extends the migrated back-handling base (RAW_FIR -> SUPER_TYPES). Test issues were never
    // gated (checkTestSources defaults off), so this changes no gating, only sidesteps the bug.
    ignoreTestSources = true
    abortOnError = true
    explainIssues = false
    absolutePaths = false
  }

  namespace = "com.growse.android.io.github.hidroh.materialistic"
}

kotlin { jvmToolchain(21) }

// S0-06: export the Room schema so DB migrations are verifiable.
// The generated app/schemas/ JSON is committed by a later stage.
//
// dagger.hilt.android.internal.disableAndroidSuperclassValidation: under AGP 9's new task
// model the Hilt Gradle plugin's bytecode transform runs after kapt, so the Hilt annotation
// processor never sees the @AndroidEntryPoint/@HiltAndroidApp superclass value it normally
// injects and fails with "Expected @AndroidEntryPoint to have a value." This is the same
// processor option the Hilt Gradle plugin contributes; setting it explicitly lets the
// processor defer superclass validation to the (still-applied) plugin transform.
kapt {
  arguments {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("dagger.hilt.android.internal.disableAndroidSuperclassValidation", "true")
  }
}

dependencies {
  implementation(libs.androidx.activity)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.localbroadcastmanager)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.cardview)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.material)
  implementation(libs.androidx.preference)
  implementation(libs.androidx.browser)
  implementation(libs.hilt.android)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.gson)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging.interceptor)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.compiler)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.hilt.work)
  implementation(libs.tink.android)

  kapt(libs.androidx.room.compiler)
  kapt(libs.hilt.compiler)
  kapt(libs.androidx.hilt.compiler)
  kaptTest(libs.androidx.room.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.mockwebserver)

  androidTestImplementation(libs.kaspresso)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.ext.junit)
}

// THIRD-PARTY-NOTICES.txt and docs/licenses.html are generated from the resolved release
// classpath, not hand-maintained, so they cannot drift from the real dependencies. The plugin is
// build-time only and is never packaged into the app. See CONTRIBUTING.md to regenerate.
licenseReport {
  // Only what ships in the release APK. Excludes test, androidTest, kapt, and lint classpaths so
  // notices list runtime dependencies, not build/test tooling.
  configurations = arrayOf("releaseRuntimeClasspath")
  renderers = arrayOf(TextReportRenderer("THIRD-PARTY-NOTICES.txt"))
  outputDir = layout.buildDirectory.dir("reports/dependency-license").get().asFile.path
}

// Bundled binaries and external data ship in the app (or are part of its attribution surface) but
// are not Maven artifacts, so the classpath generator cannot discover them. They are listed here by
// hand and prepended to both notice files. Update this block when a bundled asset is added or
// removed.
val bundledAssetsNotice =
    """
    |Bundled assets and external data
    |================================================================================
    |These ship in the app or are part of its attribution surface but are not Maven
    |dependencies, so the dependency-classpath generator below cannot discover them.
    |They are listed here by hand (see CONTRIBUTING.md).
    |
    |  - PDF.js - Apache License v2.0 - https://github.com/mozilla/pdf.js
    |  - Material design icons - Creative Commons Attribution 4.0 International - https://github.com/google/material-design-icons
    |  - Droid Sans - Apache License v2.0 - https://github.com/google/fonts
    |  - Droid Serif - Apache License v2.0 - https://github.com/google/fonts
    |  - Libre Baskerville - SIL Open Font License v1.1 - https://github.com/google/fonts
    |  - Roboto Slab - Apache License v2.0 - https://github.com/google/fonts
    |  - AdAway default blocklist (ad-host list) - Creative Commons Attribution 3.0 - https://adaway.org/
    |  - algolia/hn-search (Hacker News search API reference) - MIT License - https://github.com/algolia/hn-search
    |
    |Maven and runtime dependencies (generated from the release classpath)
    |================================================================================
    |
    |"""
        .trimMargin()

// Copies the generated report into the two committed files. Strips the renderer's "generated at"
// line so re-running produces no diff, and wraps the same text into the HTML page so the .txt and
// .html cannot disagree. The report task is not configuration-cache safe; run with
// --no-configuration-cache (see CONTRIBUTING.md).
tasks.register("generateLicenseNotices") {
  group = "documentation"
  description =
      "Regenerate THIRD-PARTY-NOTICES.txt and docs/licenses.html from the release dependencies."
  dependsOn("generateLicenseReport")
  notCompatibleWithConfigurationCache(
      "Regenerates committed source files from the dependency report."
  )
  val generated =
      layout.buildDirectory.file("reports/dependency-license/THIRD-PARTY-NOTICES.txt").get().asFile
  val noticesTxt = File(rootProject.rootDir, "THIRD-PARTY-NOTICES.txt")
  val licensesHtml = File(rootProject.rootDir, "docs/licenses.html")
  val bundled = bundledAssetsNotice
  inputs.file(generated)
  outputs.files(noticesTxt, licensesHtml)
  doLast {
    val generatedBody =
        generated
            .readText()
            .lineSequence()
            .filterNot { it.startsWith("This report was generated at") }
            .map { it.trimEnd() }
            .joinToString("\n")
            .trim()
    val body = bundled + "\n" + generatedBody + "\n"
    noticesTxt.writeText(body)

    val escaped = body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    licensesHtml.writeText(
        """
        |<!DOCTYPE html>
        |<html lang="en">
        |  <head>
        |    <meta charset="utf-8">
        |    <title>Afterglow - 3rd Party Licenses</title>
        |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
        |    <style type="text/css">
        |      body {font-family: sans-serif;}
        |    </style>
        |  </head>
        |  <body>
        |    <header>
        |      <h1>3rd Party Licenses</h1>
        |    </header>
        |    <article>
        |      <pre>
        |$escaped
        |      </pre>
        |    </article>
        |  </body>
        |</html>
        |"""
            .trimMargin()
    )
  }
}
