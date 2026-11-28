# F-Droid

F-Droid builds Square Chess from the tagged source and, because the build is reproducible, publishes the APK signed with the standalone release key (the same file as on GitHub Releases). Users can then move between F-Droid and GitHub without reinstalling.

## Recipe

`.fdroid.yml` (repository root) is a copy of the published metadata file in [fdroiddata](https://gitlab.com/fdroid/fdroiddata) (`metadata/com.dataespresso.squarechess.yml` there). The store text, images and changelogs come from `fastlane/metadata/android/en-US/` in this repository.

- F-Droid builds `app` with Gradle (release, unsigned) and NDK 28.2.13676358.
- `Binaries` points to the GitHub release APK; F-Droid compares it with its own build and, when they match apart from the signature, publishes our signed APK. `AllowedAPKSigningKeys` is the standalone key's certificate SHA-256 (`docs/releases/signing.md`).
- `UpdateCheckMode: Tags` picks up new `v*` tags automatically.

## Reproducible builds

Tested 29 September 2026: two clean checkouts in different folders produced byte-identical unsigned APKs. What made the difference:

- `native/CMakeLists.txt` maps the source, build and NDK folders to fixed names (`-ffile-prefix-map`). Without this, only the 20-byte GNU build ID of `libsquarefish.so` differed, because the linker hashes the unstripped library, which contains the folder paths.
- Pinned toolchain: Gradle 8.13 (wrapper), AGP 8.13.0, Kotlin 2.2.10, JDK 21 (the F-Droid build server default), NDK 28.2.13676358, CMake 3.22.1, SDK 36.

- `native/fairy/src/misc.cpp` no longer embeds the compile date (`__DATE__`), which made every build day different.
- `.gitattributes` keeps packaged assets LF on every OS, so a Windows build matches a Linux build.

- The JDK must be the same as F-Droid's. The F-Droid build server (Debian 13) uses JDK 21 and has no JDK 17 package. Built with JDK 17, 1.0.6 differed from F-Droid's build in `classes2.dex` (extra `MethodParameters` annotations on some AndroidX classes) and so in `assets/dexopt/baseline.prof`. A local JDK 21 build of the same tag matched F-Droid's APK in every file except the native library, which differs only because it was built on Windows (tested 30 September 2026).

The first version with all fixes is 1.0.7; 1.0.6 is reproducible only with JDK 17, and earlier tags cannot reproduce on another day or machine.

## Submitting

1. Create a GitLab account and fork `fdroid/fdroiddata`.
2. Add the recipe as `metadata/com.dataespresso.squarechess.yml` on a new branch.
3. Open a merge request titled "New app: Square Chess". The fdroiddata CI runs `fdroid lint` and a test build; reviewers then check the app by hand.

## 1.7.0: Internet permission and sign-in

1.7.0 adds `android.permission.INTERNET` for the optional squarechess.org sign-in and sync. Nothing
is sent until the player signs in. F-Droid picks up the `v1.7.0` tag by itself
(`AutoUpdateMode: Version`), so no merge request is needed for the version.

- **Anti-feature `NonFreeNet`:** F-Droid adds it to apps that rely on a network service whose code
  is not free software. The squarechess.org server is free software: its code is public at
  https://github.com/chriotte/squarechess.org under AGPL-3.0-or-later (published 28 November 2026),
  so the app should not need the label. If a reviewer asks, point them to that repository.
- The changelog (`fastlane/.../changelogs/17.txt`) names the new permission and why.
