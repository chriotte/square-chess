# Sign in to sync

Optional. Square Chess works fully offline and without an account. Settings → **Sign in to
sync** lets a player link the app to a free squarechess.org account, so games, puzzle progress,
the training level and the buddy stay the same on every device and on the website. The same
screen saves and restores a full backup file, which needs no account.

Nothing touches the network until the player signs in. A failed sync is only shown on the
account screen and never stops play.

The screen lives in Settings, away from Home's buddy card and the Playroom screens, which
children use.

## Files

| File | What it does |
| --- | --- |
| `SignIn.kt` | OAuth settings, PKCE (S256), the authorize URL, reading the redirect |
| `SyncApi.kt` | The website's API with `HttpURLConnection` and `org.json` (no network library) |
| `SyncRecords.kt` | Mapping between Room/settings and the API's records, both ways |
| `SyncEngine.kt` | Merge rules (last write wins), local change detection, the sync loop |
| `SyncManager.kt` | Token storage (`AccountStore`), Room/settings access (`AppSyncStore`), background sync, sign-out, delete |
| `SignInRedirectActivity.kt` | Receives `org.squarechess.app:/oauth?code=…&state=…` and returns to the app |
| `AccountScreen.kt` | The Settings row and the account/backup screen |
| `Backup.kt` | Full backup export and import in the same record shape |

## Sign-in

OAuth 2.0 authorization code with PKCE, as the Lichess apps do. The app never handles the
password during sign-in.

1. `SyncManager.startSignIn()` makes a random 43-character `code_verifier` and a random
   `state`, saves both (Android may end the app while the browser is open), and opens
   `https://squarechess.org/oauth/authorize?response_type=code&client_id=square-chess-android&redirect_uri=org.squarechess.app:/oauth&code_challenge=<S256>&code_challenge_method=S256&state=<state>`
   with a plain `ACTION_VIEW` intent (no Custom Tabs library).
2. The website handles login, sign-up and the "Allow Square Chess on this device?" screen.
3. The browser opens `org.squarechess.app:/oauth?code=…&state=…`. `SignInRedirectActivity`
   is the only activity with that scheme. Android ignores a `path` in an intent filter that
   has no host, so the filter is the scheme alone; `parseRedirect` checks the scheme, the
   state (constant-time comparison), the code's format and that the sign-in is less than 30
   minutes old. `error=access_denied` (Cancel on the website) shows "Sign-in was cancelled".
4. The app posts `grant_type`, `code`, `code_verifier`, `client_id`, `redirect_uri` and
   `device` (manufacturer and model, shown on the website's list of signed-in devices) as a
   form to `POST /api/oauth/token`, then reads the username from `GET /api/me`.
5. Every later call sends `Authorization: Bearer <token>`. The app also sends
   `X-Square-Chess: 1`, which Bearer calls do not need and which does no harm.

### Token storage

The token is in private SharedPreferences (`account`). The app has `allowBackup="false"`, so
it is not copied to cloud backups or device transfers. EncryptedSharedPreferences was not used:
`androidx.security:security-crypto` is deprecated and pulls in Tink. Tink is open source, but
for this threat model (a rooted device or a full device compromise) it adds little, since the
key would live on the same device. The token can be revoked from the website's account screen
at any time.

Sign out deletes the token, cursor, tombstones and server copies (`sync_base`) locally first,
then calls `POST /api/logout` to revoke it. If the server cannot be reached, the device is
still signed out.

A `401` (or `"error":"signed_out"`) from the API signs the device out the same way and shows
"You were signed out". A `401` without a JSON body (for example a password-protected preview
page or a captive portal) is treated as a server error and does not sign anyone out.

## Records

`POST /api/sync` takes `{cursor, records}` and returns `{cursor, records, more}`. A record is
`{kind, id, updated, deleted, data}`; `updated` is milliseconds since 1970.

| Room / settings | kind | id | data |
| --- | --- | --- | --- |
| `games` row | `game` | game UUID | `id, mode, initialFen, moves[] (UCI), white, black, humanWhite, level, result, resultReason, updated, orientationFlipped, clock, hintsEnabled, evaluationEnabled, evaluations` |
| deleted game | `game` | game UUID | none: `deleted: true` (tombstone) |
| `puzzle_progress` row | `puzzle` | puzzle id | `puzzleId, attempts, solved, firstTrySolved, mistakes, hints, lastResult, lastAttemptAt, reviewStep, nextReviewAt` |
| `puzzles` prefs `trainingLevel` | `kv` | `trainingLevel` | `{"value": 1100}` |
| settings `world, buddy, outfit, unlockAll, seenStickers` | `kv` | `playroom` | `{"value": {world, buddy, outfit, unlockAll, seenStickers}}` |
| `puzzles` prefs `puzzleGoal` | `kv` | `puzzleGoal` | `{"value": 10}` (0 = off) |
| `puzzles` prefs `puzzleDays` | `kv` | `puzzleDays` | `{"value": {"2026-11-28": 7}}` |
| `puzzles` prefs `gameRating` | `kv` | `gameRating` | `{"value": {rating, games, wins, draws, losses}}` |
| `puzzles` prefs `ratingDays` | `kv` | `ratingDays` | `{"value": {"2026-11-28": {"p": [1243, ms], "g": [1180, ms]}}}` |
| `puzzles` prefs `repeatSet` | `kv` | `repeatSet` | `{"value": {id, created, source, ids, cycles, current}}`, or `{"value": false}` once cleared |

- `moves` is a space-separated string in Room and a JSON array in the record.
- `clock` is the web client's snapshot: `{config: {baseMs, incrementMs, delayMs}, whiteMs,
  blackMs, active, phase, anchorMs: 0, interrupted, delayRemainingMs}`, built from the
  `clock*` columns. A clock that arrives `RUNNING` is stored paused and interrupted, as after
  a restart.
- `outfit` is the app's text form, for example `CROWN,skin:MEDIUM_DARK,hair:BLACK`.
- The record's `updated` is the game's `updated`, or the puzzle's `lastAttemptAt`. For the two
  `kv` records the app now stores when they last changed: `trainingLevel.updated` in the
  `puzzles` prefs and `playroom.updated` in the `settings` prefs (`SettingsStore.save` sets it
  when a synced Playroom field changes).
- Game records also carry `unrated` and, once rated, `rating: {before, after, change, opponent,
  provisional}`. The server caps the game rating leaderboard with `rating`, so the app always
  writes it back. `trainingLevel` is the puzzle rating.
- `gameRating` keeps the copy with more games, then the higher rating; `ratingDays` keeps, per day
  and rating, the later value (then the higher). Both are merged like `puzzleDays`.
- `puzzleDays` is merged, not replaced: the larger count for each day wins, whichever copy is
  newer. When the merged counts differ from the server's, the app sends them back in the same
  sync. `puzzleGoal` and `repeatSet` follow the newest copy, like `trainingLevel`; their change
  times are `<key>.updated` in the `puzzles` prefs. The stored repeat set keeps fields this
  version does not know. Course chapters are not synced: both sides work them out from the
  `puzzle` records and the shared puzzle pack.
- Device choices stay on each device and are not synced: sound, vibration, e-ink, board
  colours, coordinates, language, Playful extras on/off.

### Unknown fields

Each record's last server copy is kept in the Room table `sync_base` (database version 9,
migration 8→9 adds this table; version 8 added the games' rating columns). When a record goes back up, the app writes its own
fields over a copy of that JSON, so fields added by a newer app or the website survive. For
`playroom`, names this version does not know (a world, buddy, outfit item, colour or sticker)
are kept when saving, as the format asks.

Records that cannot be shown safely are not stored locally (their server copy is still kept
as the base): a game with an unknown `mode` or with moves that are not legal from its
`initialFen`. Levels and review steps out of range are clamped, and an unreadable clock is
dropped.

## Merge rules

Last write wins per record, as in the website's `account.js`:

- **Local changes** are records newer than their base: a game whose `updated` is greater than
  its base's, a puzzle whose `lastAttemptAt` is, a `kv` value whose stored change time is.
  A record with no base (never synced) counts as changed. No per-save hooks are needed.
  Like the website, changes that keep `updated` (orientation, evaluations, clock checkpoints)
  are not synced on their own.
- **Tombstones**: deleting a game while signed in records `{id: time}`. It is sent as
  `deleted: true` until the server echoes it back.
- **Remote records** win when their `updated` is greater than the local copy's (or the
  tombstone's time). A remote game newer than a local tombstone brings the game back.
- **Cursor**: the reply's `cursor` is saved after each round. The loop repeats while local
  changes remain (batches of 400; the server accepts 500) or the server says `more`.
- **First sign-in**: the cursor starts at 0, so everything comes down and every local record
  goes up. As on the website, the account's saved Playroom wins over this device's
  (`playroom.updated` is reset at sign-in); if the account has no Playroom or training level,
  this device's values go up.

### When sync runs

- On sign-in, on "Sync now", and when the app comes to the front (at most once a minute).
- After changes: a Room invalidation observer on `games` and `puzzle_progress`, and a
  preferences listener for the two change times, ask for a sync 5 seconds later. The sync
  goes ahead only if `localChanges` is not empty, so the app's own writes during a sync do
  not cause more requests.
- After a sync, open screens reload what changed: the game screen reloads a newer copy of
  the open game (or the next game after a remote deletion), Puzzles reloads progress and the
  level, and Settings reloads the Playroom. This prevents a stale in-memory copy from being
  saved over newer data.

## Account deletion

"Delete account…" opens an in-app confirmation (an `AppAlertDialog`, so it works in e-ink
mode) that explains what is deleted, asks for the account password and calls
`POST /api/delete`. The server requires the password even with a Bearer token (see below).
The dialog also links to `https://squarechess.org/account/delete`. After deletion the device is
signed out; games on the device stay.

## Backup file

Account screen → **Save backup file** / **Restore from backup**, using the system document
picker (`CreateDocument` / `OpenDocument`), so no storage permission is needed.

```json
{"format": "square-chess-backup", "version": 1, "exported": "2026-01-01T12:00:00Z",
 "records": [{"kind": "game", "id": "…", "updated": 0, "deleted": false, "data": {…}}, …]}
```

Records use the sync shape. One extra `kv` record, `deviceSettings`, holds the device
choices (sound, e-ink and so on) and appears only in backups. Restoring merges with the same
newest-wins rule, so it never replaces a newer game with an older one. The website's account
export (`GET /api/export`) has the same `records` list and can be restored too. PGN export is
unchanged.

## Tests

- JVM: `SignInTest.kt` (PKCE with the RFC 7636 example, authorize URL, redirect state checks,
  token storage and sign-out), `SyncRecordsTest.kt` (mapping both ways, unknown fields, merge
  rules, tombstones), `SyncServerTest.kt` (the API client, the sync loop and `SyncManager`
  against `FakeSyncServer`, a small HTTP server in the test JVM that follows the PHP API, plus
  backup round trips). The only test dependency added is `org.json:json` (Android's `org.json`
  is a stub off-device).
- Device: the existing suite covers the database migration to version 9 through
  `openChessDatabase`.
- Not automated: the real browser round trip. To try it on a device, sign in to a test account
  on squarechess.org in the device's browser, then in the app tap Settings → Sign in to sync →
  Sign in with squarechess.org → Allow. Check that the app shows the username, that a game
  played on the website appears after "Sync now", that a game deleted in the app disappears on
  the website, and that the website's account screen lists the device and can sign it out.
  Both the release and dev builds claim the `org.squarechess.app` scheme, so with both
  installed Android asks which app to open.

## Release checklist

**Google Play**
- Data safety form: the app now collects data, but only when the player signs in.
  User IDs (username, email on the website), app activity / other user-generated content
  (games, puzzle progress, settings), device model name. Encrypted in transit (HTTPS). Users
  can request deletion. Not shared with third parties. Optional, not required to use the app.
- Account deletion: give `https://squarechess.org/account/delete` in the Play Console's
  account deletion section; in-app deletion is on the account screen.
- Privacy policy: update the published policy (`SupportConfig.privacyPolicyUrl` and
  `PRIVACY_POLICY.md`) to cover the optional account and sync.
- Check the content rating / target audience answers still hold, since the app is used by
  children: sign-in is behind Settings, and nothing is sent until someone signs in.

**F-Droid**
- Changelog: mention the new `INTERNET` permission and why (optional sign-in and sync).
- Anti-features: `NonFreeNet` applies to apps that depend on a non-free network service. To
  avoid it, publish the server code (`site/api/index.php`) under a free licence. Note the
  feature in the metadata if the reviewers ask.
- No new runtime libraries: the release policy check still lists no proprietary
  dependencies. Its permission allow-list now includes `android.permission.INTERNET`.

**Both**
- `CHANGELOG.md` and the store descriptions: say that accounts are optional and the app works
  without one.

## Mismatches between the server docs and code

- `docs/accounts.md` lists `POST /api/delete` as a Bearer call, but `index.php` also requires
  the account password (`sc_check_password`) and returns `403 wrong_password` without it.
  The app follows the code and asks for the password in the confirmation dialog.
- `GET /api/me` with an invalid or expired token returns `200 {"user": null}`, not `401`. The
  app treats a null user as signed out.
- The authorize endpoint and redirect URI in the docs match the code (`SC_OAUTH_CLIENTS`).
