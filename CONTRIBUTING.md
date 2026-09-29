# Contributing to Square Chess

Thank you for your interest. Square Chess is a small, focused app: offline chess for square screens and keyboard phones. Changes that keep it simple, private and fast are the most welcome.

## Before you start

- For a bug, open an issue with the steps to reproduce it, the device and the Android version.
- For a feature, open an issue first so we can agree on the scope.
- Square Chess must keep working without Google Play Services and without the Internet permission. `./gradlew verifyReleasePolicy` fails if a change adds a proprietary Google library or a new permission.

## Development

See [docs/development/building.md](docs/development/building.md). In short:

```sh
./gradlew check                 # unit tests, lint, release policy
./gradlew -Pdev=true assembleDebug assembleDebugAndroidTest   # device tests run only in the dev app
```

Guidelines:

- Keep the board as large as possible; a UI change must not make the board smaller on square screens (`docs/architecture/overview.md`, "Layout rule").
- Every move goes through Chesslib's legal-move check. Do not add move-selection logic to the engine bridge; difficulty is set with the engine's own options.
- Add or update unit tests with each change, and keep the code style of the file you edit.
- One logical change per pull request, with a clear description.

## Licence

Square Chess is licensed under the GNU GPL v3.0 or later. By contributing, you agree that your contribution is licensed under the same terms.
