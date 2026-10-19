# Bezel Town

A standalone incremental game for the Galaxy Watch 4 Classic (Wear OS), built
around the rotating bezel. Walk a tiny character down the street by turning the
bezel, play WarioWare-style microgames inside buildings to earn that building's
credits, upgrade it, and let it earn money while you idle.

Design spec: [docs/SPEC.md](docs/SPEC.md) (referenced below as §N).

## What's playable now

Small Town (§6), first slice:

- **Overworld** (§4): a low-poly 3D street rendered with Filament. Turn the bezel
  to walk; 4 detents per building, a tick per detent, a click when you reach a
  doorway, and it snaps to the nearest door when you stop. Tap to enter or buy.
  Money and income curve along the top; portal progress along the bottom.
- **Bakery** (touch, swipe): knead the dough with 15 swipes in 7 seconds. It
  turns golden as it gets springy and puffs flour.
- **Water Tower** (bezel, spin): crank the pump to fill the tank in 8 seconds.
  Crank faster than ~9 detents/s and the pipe sprays, wasting water.
- **All three upgrade tracks** per building, paid in that building's credits:
  Skill (credits per play), Automation (money per second), Storage (hours banked).
- **Locked buildings 3–5** (Barber, Radio, Clock Tower) as silhouettes with prices,
  and the **gatekeeper** dressed as a tour guide next to the portal. These aren't
  enterable yet.
- Save/load with DataStore. Money only accrues while the game is on screen.

Not yet: offline payout on resume (Storage is tracked but nothing pays out from
it yet), particles, the arcade cabinet, the gatekeeper's crank payment, tokens.

## Layout

- `core/` – pure Kotlin, no Android: game state, content and tuning
  (`content/Settings.kt`), the economy layer, microgame rules, bezel walking.
  Microgames only report a 0–100 score; `Economy` decides what it's worth (§11).
- `app/` – Wear OS app: Compose for Wear OS UI, Filament scenes, input, haptics.
  - `render/` – Filament engine, procedural low-poly mesh builder, scene base.
  - `render/scenes/` – the overworld street and each building interior.
  - `app/src/main/materials/` – Filament materials, compiled into
    `app/src/main/assets/materials/`. `flat.mat` draws everything (unlit, vertex
    colors with lighting baked in on the CPU, plus vertex snapping and fog);
    `retro_blit.mat` puts the low-resolution frame on screen.

## Look: PS1 retro

Scenes render at a third of the screen's resolution into an offscreen texture,
then get drawn full-screen with hard pixels, 15-bit color and ordered dithering.
Vertices snap to a coarse screen grid (the PS1 wobble), distant things fade into
fog, faces get a little random brightness grime, and drawing is capped at 30 fps.
All the knobs are in `render/RetroLook.kt`.

## Building

Open in Android Studio, or:

```
./gradlew :core:test          # game logic tests, any JVM
./gradlew :app:assembleDebug  # needs the Android SDK
adb install app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions builds the debug APK on every push. Every build of `main` is also
published as the `latest` release, so this link always serves the newest APK:

https://github.com/mikeyd433/WatchGameNumberOne/releases/download/latest/bezel-town.apk

## Installing on the watch from an Android phone

1. On the watch: Settings → About watch → Software information → tap Software
   version ~5 times. Then Developer options → turn on ADB debugging and Wireless
   debugging (watch and phone on the same Wi-Fi).
2. On the phone, install Wear Installer 2 (or Bugjaeger) and pair it with the
   watch using the code from Wireless debugging → Pair new device. Once only.
3. Download the APK from the link above and install it with the app.

All builds are signed with the committed `app/debug.keystore`, and `versionCode`
is the commit count, so each new build installs over the last and keeps your save.

If you edit `flat.mat`, recompile it with `tools/build-materials.sh` using `matc`
from the **same** Filament release as `filament` in `gradle/libs.versions.toml`.

## Tuning on the watch

Things most likely to need adjusting once it's on a real wrist:

- `BezelDetents.DIRECTION` if clockwise walks the wrong way.
- `Walker` (`stepsPerSlot`, snap delay, speed) for how walking feels.
- `KneadGame` / `PumpGame` companion constants for difficulty.
- Per-building costs and rewards in `core/.../content/Settings.kt`.
