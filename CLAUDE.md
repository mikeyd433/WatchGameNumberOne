# Bezel Town

Wear OS incremental game. See README.md for layout and docs/SPEC.md for the design.

## Game design: ask first

- Before building a new building, microgame, setting, or other gameplay feature,
  describe the proposal to the owner (what it is, how it plays, its input and
  rough tuning) and wait for approval. This applies even when the idea comes
  straight from docs/SPEC.md: the spec is a draft, not a sign-off.
- Changing how an existing game plays (rules, goals, controls, scoring) needs
  the same check-in. Bug fixes, performance work and tooling don't.

## Git workflow

- Default to landing work on `main`. When a change is done and verified, push it
  to `main` directly (merge any session branch into `main` and push), rather than
  stopping at a feature branch or opening a pull request. The owner has approved
  this as the standing default.
- Before pushing to `main`, run `./gradlew :core:test :app:assembleRelease` and
  make sure it passes. The release APK is the one to send to the owner: it is much
  faster on the watch than the debug build.

## Building

- The SessionStart hook (`.claude/hooks/session-start.sh`) installs the Android SDK
  into `/opt/android-sdk` in cloud sessions.
- `core/` is pure Kotlin and testable on any JVM; game logic belongs there.
- Recompile `app/src/main/materials/*.mat` with `tools/build-materials.sh` using
  `matc` from the same Filament release as `gradle/libs.versions.toml`.
- The Play Store build is `./gradlew :app:bundlePlay`, signed with a private upload
  key the owner holds (env `BEZEL_UPLOAD_KEYSTORE` / `BEZEL_UPLOAD_PASSWORD`; CI
  reads it from repo secrets). Never commit an upload key or its password.
- Compose skips work aggressively here (strong skipping). Two rules, each learned
  from a bug: pass composables plain values, not a game object whose contents
  change in place (it gets skipped and freezes); and give long-lived callbacks
  such as gesture detectors live state through `rememberUpdatedState` holders,
  not function references that capture it (they get cached and go stale).
