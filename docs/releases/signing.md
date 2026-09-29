# Signing keys

Square Chess uses three signing keys. Only certificate fingerprints are recorded here; the keystores and passwords are never in this repository.

| Key | Used for | Holder | Certificate SHA-256 |
|---|---|---|---|
| Play app signing key | APKs that Google Play delivers to users | Google (Play App Signing) | `AF:B6:32:75:89:F2:BA:8D:B8:2D:2A:AC:9A:CE:3A:5F:E9:A5:83:FC:D7:06:39:A9:98:F9:40:AD:B1:FD:FA:01` |
| Upload key | Signing the AAB uploaded to Play | DataEspresso (`square-chess-upload.jks`, alias `upload`) | `4A:03:9B:C1:67:32:18:50:73:10:EC:BD:39:92:1F:D0:CD:20:83:BB:90:D6:FC:14:4C:DE:54:E2:95:50:E0:4D` |
| Standalone release key | GitHub Releases APK and (planned) F-Droid reproducible builds | DataEspresso (`square-chess-standalone-release.jks`, alias `squarechess`) | `E6:30:2C:95:F0:DA:65:D8:F2:7C:80:C8:2D:4B:82:C9:79:AF:75:BB:C1:EA:38:35:47:CD:07:9E:C1:78:CF:44` |

All three use the same application ID, `com.dataespresso.squarechess`. Because Google holds the Play app signing key, a Play installation and a GitHub/F-Droid installation always have different signatures: Android cannot update one with the other. To move between them, export games (Game history → Export), uninstall, install from the other source, and import the file (Game history → Import).

## Local builds

The keystores live in `~/SquareChessSigning/` (outside the repository), each with a properties file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`):

- Play AAB: `./gradlew bundleRelease` reads `~/SquareChessSigning/keystore.properties` by default.
- Standalone APK: `./gradlew -PsigningProperties=$HOME/SquareChessSigning/standalone.properties assembleRelease`.
- Check the result: `apksigner verify --print-certs <apk>` must show the fingerprint above.

## Backup

Keep an offline copy of `~/SquareChessSigning/` (keystores and properties files) and of this table. Losing the upload key is recoverable through Play support; losing the standalone key means GitHub/F-Droid users cannot update without reinstalling.
