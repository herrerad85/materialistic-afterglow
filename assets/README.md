# Assets

## Current

- `screenshot-1.png` to `screenshot-4.png`: app screenshots captured from a current debug build using the fixed Light theme (the orange palette). Used by the project README and mirrored to the store listing phone screenshots under `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- `afterglow-icon.png`: the master Afterglow icon artwork (1254x1254), provided by the maintainer. It is the source for the derived icon assets: the 512x512 store icon at `fastlane/metadata/android/en-US/images/icon.png`, and the Android launcher icons in `app/src/main/res` (the adaptive icon, its solid background and `ic_launcher_foreground` mark, and the legacy density `ic_launcher` / `ic_launcher_round` mipmaps). The launcher icon uses a flat orange field with the white "ag" mark extracted from the master so it stays legible under square and round launcher masks; the store icon keeps the full rendered artwork.

## Icon surfaces not yet refreshed

This first branding pass refreshed only the primary launcher, store, and repo icon. The surfaces below still use earlier or generic artwork and are intentionally left for a separate branding pass, because they need purpose-specific artwork or screenshots rather than a resize of the master:

- the notification small (status bar) icon, which needs a simple monochrome/alpha mark
- shortcut icons
- the widget preview image
- the navigation drawer header
- the store feature graphic (`materialistic-feature-graphic.png`)

## Historical / pre-revival

These files predate the current design and are kept for reference only. They are not the source of the current screenshots or icon and should not be treated as up to date:

- `materialistic-drawable.sketch`
- `playstore-screenshots.sketch`
- `shortcuts.sketch`
- `materialistic-feature-graphic.png`

To refresh the icon, edit or replace `afterglow-icon.png` and regenerate the derived store and launcher assets from it. To refresh screenshots, capture from a current build. Avoid editing the legacy files above.
