# Release process

One source tree, one build. Every channel gets the same code; only the signer differs (`docs/releases/signing.md`).

## 1. Prepare

1. Increase `versionCode` and set `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (500 characters maximum) and update `CHANGELOG.md`.
3. `./gradlew check` passes; run the device tests on the dev app (`docs/testing/test-plan.md`).
4. Commit and push; CI must be green.

## 2. GitHub release (standalone APK)

1. `git tag -a v<versionName> -m "Square Chess <versionName>"` and `git push origin v<versionName>`.
2. The **Release** workflow checks the tag against `versionName`, runs the tests and the release policy, builds the APK, signs it with the standalone release key, verifies the certificate, and creates a **draft** release with `Square-Chess-v<versionName>.apk` and `SHA256SUMS`.
3. Review the draft on GitHub and publish it.

One-time setup: add the four signing secrets as repository secrets (Settings → Secrets and variables → Actions → New repository secret) with `scripts/release/copy-signing-secrets.ps1`. GitHub Free offers environments only for public repositories; after publication, move the secrets to an environment `production-release` with yourself as required reviewer, and enable the `environment:` line in `release.yml`.

## 3. Google Play

1. `./gradlew bundleRelease` (signed with the upload key).
2. `python scripts/play/play_upload.py app/build/outputs/bundle/release/app-release.aab internal "<notes>"`, test on a phone, then promote in Play Console or with `production` as the track.
3. Listing changes: edit `fastlane/metadata/android/en-US/` and run `python scripts/play/play_listing.py`.

## 4. F-Droid (after the repository is public)

F-Droid builds the tagged source with `assembleRelease` and reads `fastlane/metadata/`. The goal is a reproducible build, so that F-Droid can ship the APK signed with the standalone release key.
