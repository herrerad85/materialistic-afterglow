## Contributing

**Translations**

Please translate only string resources specified in [`strings.xml`](app/src/main/res/values/strings.xml). Others have been marked as non-translatable. It is recommended that you use [Android Studio Translations Editor](http://tools.android.com/recent/androidstudio087released) to check for missing translations.

**Lint checking**

Lint is turned on and has been set to be very strict, failing build for either warnings or errors. Please make sure your changes do not violate any Lint checks. Exceptions (ignore/severity override) can be made on case by case basis.

**Code style**

Though not strictly enforced, it is strongly recommended that you follow [Android code style](https://source.android.com/source/code-style.html), or at the very least follow the existing code style where you make changes for consistency. If needed, please separate commits for code style and business logic changes.

**Third-party license notices**

`THIRD-PARTY-NOTICES.txt` and `docs/licenses.html` are generated from the resolved release dependencies, not edited by hand. After changing dependencies, regenerate them:

    ./gradlew generateLicenseNotices --no-configuration-cache

This rewrites both files from the release runtime classpath and strips the report's "generated at" line, so re-running produces no diff. The `--no-configuration-cache` flag is required because the underlying license-report task does not support the configuration cache.

The generator only sees Maven artifacts on the classpath. Bundled binaries and external data (PDF.js, the bundled fonts, the material design icons, the ad-servers list, and the search API reference) are not Maven dependencies, so they are listed by hand in a fixed block at the top of both files; edit `bundledAssetsNotice` in `app/build.gradle.kts` if a bundled asset is added or removed. The in-app license screen (`app/src/main/res/values/license.xml`) is the short, human-readable list of notable libraries and bundled assets and is maintained by hand. Commit the regenerated files together with your dependency change.

**Hosted legal pages**

The full third-party license text (`docs/licenses.html`) and the privacy policy (`docs/privacy.html`) are published with GitHub Pages from this repository's `docs/` folder, at `https://herrerad85.github.io/materialistic-afterglow/licenses.html` and `https://herrerad85.github.io/materialistic-afterglow/privacy.html`. The in-app "View full text" links in `app/src/main/res/values/license.xml` point to these pages, so GitHub Pages should be enabled for the `docs/` folder to keep those links live.

**AI assistance**

AI tools are allowed when contributing. If you use them, disclose it in your pull request: whether AI was used, which tool or model, and how extensively. Disclosure gives reviewers context; it is not a ban. You remain responsible for the change and should review and understand it before submitting. Do not add AI or agent attribution trailers to commit messages.