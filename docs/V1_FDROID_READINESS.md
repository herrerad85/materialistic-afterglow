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
LibreBaskerville (SIL OFL 1.1), and the AdAway ad-host blocklist (CC BY 3.0).
`THIRD-PARTY-NOTICES.txt` and `docs/licenses.html` are generated from the release classpath
plus a bundled-assets block.
- RESOLVED 2026-06-28: the unlicensed `pgl.yoyo.org` ad-host blocklist (no formal license, no
  SPDX, only an informal "feel free to combine this list" note) was replaced with AdAway's
  default blocklist, which carries an explicit FOSS license in its file header: Creative
  Commons Attribution 3.0 (`https://adaway.org/`, source
  `https://github.com/AdAway/adaway.github.io`). The asset
  `app/src/main/assets/adaway-hosts.txt` keeps AdAway's header/license intact, and the
  attribution is recorded in `license.xml`, the `bundledAssetsNotice` block, and the generated
  notices. The simple internal-browser ad blocker (`AdBlocker` + `AdBlockWebViewClient` + the
  `Block ads` preference) is intact; `AdBlocker.parseHostLine` reads the hosts format
  (`0.0.0.0 host` / `127.0.0.1 host`, bare hostnames, `#` comments, blank/malformed lines).
  No pgl.yoyo.org asset remains. The repo bundles no asset that lacks a valid redistributable
  license.

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
- [x] Resolve the `pgl.yoyo.org` blocklist license: RESOLVED by replacing it with AdAway's
  default blocklist (CC BY 3.0, explicitly licensed; see Bundled assets). The ad blocker is
  retained. No bundled asset now lacks a valid redistributable license.
- [ ] Author the `fdroiddata` metadata YAML with the fields above (include `NonFreeNet`).
- [ ] Do not submit until the owner authorizes.

### Screenshot freshness (checked 2026-06-28)
Device-compared the v1.0.0 candidate UI against the three fastlane phone screenshots and the
`assets/` set: story list, comment thread, and article views still match the current layout.
NOT refreshed, and this does NOT block F-Droid inclusion (F-Droid builds from source;
screenshots are listing content that only needs to exist, which it does). Reasons the current
set is still representative: the new launcher icon does not appear in any in-app screenshot
(it is a launcher-only asset), and the removed footer band was at the very bottom of lists,
not captured in these frames. The one minor drift is that comment threads now open collapsed
by default (the comments screenshot shows an expanded thread), which is still a valid feature
view. Recommend (non-blocking) a future refresh that showcases the new icon and the
collapsed-comments state before any Play Store / F-Droid listing polish.

## F-Droid Quick Start Guide mapping

Maps this repo to the official guide
(https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/). Status as of
`development` v1.0.0 prep.

| Guide requirement | This repo | Status |
| --- | --- | --- |
| Public source repo + FOSS license file | `github.com/herrerad85/materialistic-afterglow`, `LICENSE.txt` = Apache-2.0 | MET |
| FOSS dependencies only (no Firebase/GMS) | All deps FOSS; zero Firebase/GMS/Crashlytics/Ads | MET |
| FOSS build tools (no proprietary IDE) | Gradle + AGP/Kotlin/Hilt/ktfmt, all FOSS | MET |
| Author notified / does not oppose | Owner (`herrerad85`) is the fork maintainer and approves | MET |
| Fastlane/Triple-T metadata present | `fastlane/metadata/android/en-US/` with short/full description, icon, 3 phoneScreenshots | MET |
| Changelog matching the build's versionCode | `changelogs/10000.txt` matches versionCode `10000` (also `100.txt`, `200.txt`) | MET |
| Each release commit is tagged (`vX.Y.Z`) | v0.1.0/v0.2.0 tagged; v1.0.0 tag is created during promotion, NOT in this prep | PENDING (promotion-time) |
| fdroiddata metadata uses the applicationId | `metadata/com.herrerad85.afterglow.yml` (NOT the code namespace `com.growse...`) | TO AUTHOR |
| versionName/versionCode in extractable Gradle locations | `app/build.gradle.kts` `defaultConfig` literals `versionName = "1.0.0"`, `versionCode = 10000` (no dynamic compute) | MET |
| Auto-update from `v*` tags | `UpdateCheckMode: Tags ^v`, `AutoUpdateMode: Version v%v`; release workflow fires on `v*` | MET (config) |
| Reproducible builds | Best practice, not required; `dependenciesInfo` blob disabled, env-driven signing so F-Droid signs with its own key | OPTIONAL |
| Reviewer risks: binary blobs / non-free resources / AntiFeatures / buildability | No binary blobs or non-free deps; no unlicensed bundled assets (ad-host list is AdAway, CC BY 3.0); one AntiFeature `NonFreeNet` (BYO-key AI summaries); builds via `gradle assembleRelease` | MET |

Remaining before an F-Droid submission: tag `v1.0.0` (promotion) and author the
`metadata/com.herrerad85.afterglow.yml` in a fork of `fdroiddata` (License `Apache-2.0`,
Categories `Internet`, AntiFeatures `NonFreeNet`, RepoType `git`, UpdateCheckMode `Tags ^v`,
AutoUpdateMode `Version v%v`). The pgl.yoyo.org asset-license blocker is resolved (list
removed, ad blocker retired). Do not submit until the owner authorizes.
