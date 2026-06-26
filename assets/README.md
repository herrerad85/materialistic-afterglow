# Assets

## Current

- `screenshot-1.png` to `screenshot-4.png`: app screenshots captured from a current debug build using the fixed Light theme (the orange palette). Used by the project README and mirrored to the store listing phone screenshots under `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
- `afterglow-icon.png`: the master Afterglow icon artwork (1254x1254), provided by the maintainer. It is the source for the derived icon assets: the 512x512 store icon at `fastlane/metadata/android/en-US/images/icon.png`, and the Android launcher icons in `app/src/main/res` (the adaptive icon, its solid background and `ic_launcher_foreground` mark, and the legacy density `ic_launcher` / `ic_launcher_round` mipmaps). The launcher icon uses a flat orange field with the white "ag" mark extracted from the master so it stays legible under square and round launcher masks; the store icon keeps the full rendered artwork.
- `afterglow-ag-mark.svg`: the vector master of the Afterglow "ag" mark (white on transparent), provided by the maintainer. It is the source for the mark in the feature graphic, rasterized from the vector so the edges stay crisp at large preview sizes.
- `afterglow-feature-graphic.png`: the Play Store feature graphic (1024x500), Afterglow-branded, the white "ag" mark rasterized from the vector `afterglow-ag-mark.svg` and the "Afterglow" wordmark on a warm orange gradient. The same image is mirrored to `fastlane/metadata/android/en-US/images/featureGraphic.png` for store uploads (the two PNGs are byte-identical). It replaces the old `materialistic-feature-graphic.png`, and the mark is now vector-derived rather than taken from the raster icon render.

## Secondary branding surfaces

A second branding pass dispositioned the remaining surfaces:

- Notification small (status bar) icon (`app/src/main/res/drawable-*/ic_notification.png`): refreshed to the Afterglow "ag" mark as a white monochrome/alpha silhouette on transparent, the correct status-bar treatment (the system tints it). It replaces the earlier generic monochrome mark.
- Feature graphic: refreshed, see `afterglow-feature-graphic.png` above.
- Shortcut icons (`sc_trending`, `sc_new_releases`, `sc_bookmark`, `sc_add`): intentionally unchanged. These are action-specific glyphs (top, new, bookmark, submit). Replacing them with the app logo would make the four launcher shortcuts indistinguishable and reduce usability, so the glyphs are kept.
- Widget preview image (`app/src/main/res/drawable-*/appwidget_preview.png`): intentionally unchanged. It depicts the widget's own UI with the orange accent and carries no Materialistic wordmark or logo; only the sample story text is dated, which is cosmetic rather than branding.
- Navigation drawer header background (`bg_drawer_light` / `bg_drawer_dark`): intentionally unchanged. It is an abstract warm-toned geometric background with no logo or wordmark; the header shows account controls and the app name comes from resources (already "Afterglow"), and the warm palette is compatible with the brand.

## Historical / pre-revival

These files predate the current design and are kept for reference only. They are not the source of the current screenshots or icon and should not be treated as up to date:

- `materialistic-drawable.sketch`
- `playstore-screenshots.sketch`
- `shortcuts.sketch`

To refresh the icon, edit or replace `afterglow-icon.png` and regenerate the derived store, launcher, notification, and feature-graphic assets from it. To refresh screenshots, capture from a current build. Avoid editing the legacy files above.
