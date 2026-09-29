# Google Play Console

Package `com.dataespresso.squarechess`, developer DataEspresso. Free, all countries.

## Store listing

The title, descriptions, icon, feature graphic and screenshots are in `fastlane/metadata/android/en-US/` — the same files F-Droid uses. `scripts/play/play_listing.py` uploads them to Play (listing language en-GB). Edit the files, not Play Console, so both stores stay the same. After an edit in Play Console, `scripts/play/play_pull_listing.py` copies the listing text back into these files.

- Category: Game › Board. Contact: support@dataespresso.com, https://dataespresso.com/
- Privacy policy: https://dataespresso.com/en/square-chess-privacy/ (text: `PRIVACY_POLICY.md`)
- Do not name third-party phone brands in the listing (Play metadata policy); describe the screen shape instead.

## App content answers

- **Ads:** none. **App access:** everything without login.
- **Advertising ID:** not used (the app has no advertising or analytics libraries).
- **Content rating (IARC):** game, board/strategy; no violence, sexuality, language, drugs, gambling, user-generated content or location sharing.
- **Target audience:** 13 and older (under-13 adds Families policy requirements).
- **News, government, financial features, health:** no.
- **Data safety:** no data collected or shared. The app has no Internet permission; games and settings stay on the device. Exports and bug reports leave the device only when the user sends them through an app the user chooses.

## Releases

- Google Play App Signing holds the app-signing key; Play uploads are signed with the upload key (`docs/releases/signing.md`).
- `scripts/play/play_upload.py <aab> <track[:status],...> "<notes>"` uploads a bundle; `play_readiness.py` checks the listing and tracks read-only.
- A production release starts in Play Console; the API can prepare it as a draft (`production:draft`).
- Release notes per version code: `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
