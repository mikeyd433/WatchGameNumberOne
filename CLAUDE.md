# Bezel Town

Wear OS incremental game. See README.md for layout and docs/SPEC.md for the design.

## Git workflow

- Default to landing work on `main`. When a change is done and verified, push it
  to `main` directly (merge any session branch into `main` and push), rather than
  stopping at a feature branch or opening a pull request. The owner has approved
  this as the standing default.
- Before pushing to `main`, run `./gradlew :core:test :app:assembleDebug` and make
  sure it passes.

## Building

- The SessionStart hook (`.claude/hooks/session-start.sh`) installs the Android SDK
  into `/opt/android-sdk` in cloud sessions.
- `core/` is pure Kotlin and testable on any JVM; game logic belongs there.
- Recompile `app/src/main/materials/*.mat` with `tools/build-materials.sh` using
  `matc` from the same Filament release as `gradle/libs.versions.toml`.
