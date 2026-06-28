# 1.0 and F-Droid Readiness

Status as of v0.2.0 (versionCode 200): pre-store, pre-1.0. This is a tracking
checklist. Nothing here promotes, tags, or submits anything; it records what must
happen first. See `CONTRIBUTING.md` for the versionName/versionCode mapping and the
development to main promotion checklist.

## 1.0 readiness checklist

Before declaring v1.0.0 (or a higher store-facing version, per CONTRIBUTING):

- [ ] Owner judges the app feature-complete and stable enough to drop the pre-1.0 framing.
- [ ] `app/build.gradle.kts`: versionName `1.0.0`, versionCode `10000`.
- [ ] Remove the pre-1.0 disclaimers (identify, do not change before the bump):
  - `README.md`: "Afterglow is under active development and has not yet reached a stable 1.0 release."
  - `fastlane/metadata/android/en-US/full_description.txt`: same sentence.
- [ ] Add `fastlane/metadata/android/en-US/changelogs/10000.txt` and update the in-app
  `release_notes` string in `res/values/non_translatable.xml`.
- [ ] In-app What's New: currently dormant. `Preferences.isReleaseNotesSeen` /
  `BuildConfig.LATEST_RELEASE` (=77) exist, but `isReleaseNotesSeen` has no production
  caller, so the notes screen only opens manually from Settings. Bumping `LATEST_RELEASE`
  has no user-visible effect today. Decide whether to wire auto-show-on-upgrade; if yes,
  add the launch call and bump `LATEST_RELEASE` every release.
- [ ] Refresh screenshots if the UI changed (`fastlane/.../phoneScreenshots/` and `assets/`).
- [ ] All gates green (ktfmtCheck, testDebugUnitTest, lintDebug, assembleDebug,
  `generateLicenseNotices --no-configuration-cache` no-diff) plus device QA.
- [ ] Promote development to main (fast-forward), tag `v1.0.0`, let the release workflow build.

## F-Droid readiness

Bottom line: no hard blocker. No non-free dependency and no unlicensed bundled code. The
only required metadata flag is `NonFreeNet` for the off-by-default BYO-key AI summaries.

### Dependencies
All FOSS: AndroidX, Material, Dagger/Hilt, Kotlin coroutines, OkHttp/Retrofit/Gson,
Tink (Apache-2.0). Zero Firebase / Google Play Services / Crashlytics / Ads / analytics.
`dependenciesInfo { includeInApk = false; includeInBundle = false }` so no Google-signed
dependency blob ships. (`hacker-news.firebaseio.com` is the public HN REST host, not the
Firebase SDK.)

### Bundled assets
All FOSS-licensed: pdf.js (Apache-2.0), DroidSans/DroidSerif/RobotoSlab fonts (Apache-2.0),
LibreBaskerville (SIL OFL 1.1). `THIRD-PARTY-NOTICES.txt` and `docs/licenses.html` are
generated from the release classpath plus a bundled-assets block.
- Loose end: the `pgl.yoyo.org` ad-host blocklist (used by the default-on ad blocker) is
  attributed by source URL but with no explicit license name. Add a license/usage note.

### Network disclosure
Default (no opt-in): HN API `hacker-news.firebaseio.com/v0/`, `news.ycombinator.com`
(links, login, vote, comment, submit), Algolia HN search `hn.algolia.com/api/v1/`, the
linked article in the reader WebView, and unauthenticated GitHub feedback `api.github.com`.
No tracking or analytics endpoints; `INTERNET` is the only permission. The old hosted
Mercury/Readability reader service is removed (reader mode is local).

Optional AI summaries: off by default, one-time consent, BYO-key stored encrypted (Tink /
Android Keystore, non-persistent pref). Endpoints only when opted in: `api.anthropic.com`
and `generativelanguage.googleapis.com`. No keyless request leaves the device. This is why
F-Droid needs the `NonFreeNet` anti-feature. (Algolia is also a proprietary hosted backend;
a strict reviewer may consider it covered by the same flag.)

### fdroiddata metadata draft (fields determinable from the repo)
- AppId / Package: `com.herrerad85.afterglow` (note: code namespace differs,
  `com.growse.android.io.github.hidroh.materialistic`; metadata uses the applicationId)
- License: `Apache-2.0` (`LICENSE.txt`)
- Categories: `Internet` (optionally `Reading`)
- AntiFeatures: `NonFreeNet`
- SourceCode / Repo / IssueTracker: `https://github.com/herrerad85/materialistic-afterglow`(`/issues`)
- UpdateCheckMode: `Tags ^v`; AutoUpdateMode: `Version v%v` (releases fire on `v*` tags)
- Build: `gradle assembleRelease`; signing is env-driven, so F-Droid signs with its own key.

### F-Droid pre-submit checklist
- [ ] Add a license/usage note for the `pgl.yoyo.org` blocklist in the notices.
- [ ] Author the `fdroiddata` metadata YAML with the fields above (include `NonFreeNet`).
- [ ] Do not submit until the owner authorizes.
