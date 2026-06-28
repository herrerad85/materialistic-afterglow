# 1.0 and F-Droid Readiness

Status: version files bumped to v1.0.0 (versionName `1.0.0`, versionCode `10000`) on
`development`; NOT yet promoted, tagged, released, or submitted to F-Droid. This is a
tracking checklist. See `CONTRIBUTING.md` for the versionName/versionCode mapping and the
development to main promotion checklist.

## 1.0 readiness checklist

Done in the v1.0.0 prep commit on `development`:

- [x] Owner directed dropping the pre-1.0 framing (this bump).
- [x] `app/build.gradle.kts`: versionName `1.0.0`, versionCode `10000`.
- [x] Removed the pre-1.0 disclaimers in `README.md` and
  `fastlane/metadata/android/en-US/full_description.txt`.
- [x] Added `fastlane/metadata/android/en-US/changelogs/10000.txt` and updated the in-app
  `release_notes` string in `res/values/non_translatable.xml` for 1.0.
- [x] Gates green (ktfmtCheck, testDebugUnitTest, lintDebug, assembleDebug,
  `generateLicenseNotices --no-configuration-cache` no-diff).

Still open before promoting/tagging v1.0.0:

- [ ] In-app What's New: still dormant. `Preferences.isReleaseNotesSeen` /
  `BuildConfig.LATEST_RELEASE` (=77) exist, but `isReleaseNotesSeen` has no production
  caller, so the notes screen only opens manually from Settings. `LATEST_RELEASE` was left
  unchanged (bumping it has no user-visible effect today). Decide whether to wire
  auto-show-on-upgrade; if yes, add the launch call and bump `LATEST_RELEASE` every release.
- [ ] Refresh screenshots if the UI no longer matches (`fastlane/.../phoneScreenshots/` and
  `assets/`); the new launcher icon and the removed footer band postdate the current set.
- [ ] Device QA the v1.0.0 candidate build on a real device/emulator.
- [ ] Decide the GitHub release type for v1.0.0 (the v0.1.0/v0.2.0 releases were marked
  pre-release; 1.0 may be a full release). Promotion-time choice, out of this prep.
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
