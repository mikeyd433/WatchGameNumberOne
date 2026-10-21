# Bezel Town

A standalone incremental game for the Galaxy Watch 4 Classic (Wear OS), built
around the rotating bezel. Walk a tiny character down the street by turning the
bezel, play WarioWare-style microgames inside buildings to earn that building's
credits, upgrade it, and let it earn money while you idle.

Design spec: [docs/SPEC.md](docs/SPEC.md) (referenced below as §N).

## What's playable now

Small Town (§6):

- **Overworld** (§4): a low-poly 3D street in a PS1 style. Turn the bezel to
  walk (6 detents per building, a tick per detent, a click at doors); you can
  stop anywhere, and stopping one detent from a door finishes the walk to it.
  The name of the door you're at floats above it. Tap to enter or buy. Money
  and income curve along the top; portal progress along the bottom.
- **Bakery** (touch): 3D arrows pop up over the dough; swipe each way to knead.
  Clear 10 in 8 seconds; full marks at a 4.5 s par.
- **Water Tower** (bezel spin): crank the pump to fill the tank in 8 seconds.
  A pressure gauge shows when you're cranking too fast and spraying.
- **Barber Shop** (bezel precision): dial the swivel chair to the gold mark on
  the floor and hold still to snip. Four snips in 10 seconds; par 5 s.
- **Radio Station** (bezel precision): tune to the station being called out,
  by the number, the clearing static and the feel of the detents (buzzy in the
  static, a clean click on the station); hold to lock it in. Three stations in
  15 seconds; par 10 s.
- **Clock Tower** (both, the finale): crank the bezel to wind the mainspring and
  stop to lock it in (crank too far past full and it goes sproing), then drag
  the hands to the time shown; the bell rings when both are right. 12 seconds;
  par 7 s.
- **Upgrades**: Skill, Automation and Storage per building, paid in that
  building's credits.
- **Offline earnings** (§13): buildings earn while the game is closed, up to
  their Storage, with a "While you were away" card on return.
- **Gatekeeper and portal** (§7–§10): with all five buildings open and $500K,
  crank the bezel to feed him every coin, then step through. Saving past the
  price earns a permanent production bonus (5 × √(overflow ÷ price) %) and
  tokens (3 × √(overflow ÷ price)). Money, credits and buildings reset. Until
  the Carnival exists the portal loops back to Small Town at 10× the price.
- **Gatekeeper's stall**: spend tokens on Bonus Rate, Head Start, Cheaper Gate
  and Offline Storage. Rules live in `core/.../economy/Portal.kt`.

Not yet: the Carnival, the arcade cabinet, particles, a fancier stall.

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
./gradlew :core:test            # game logic tests, any JVM
./gradlew :app:assembleRelease  # needs the Android SDK
adb install -r app/build/outputs/apk/release/app-release.apk
```

GitHub Actions builds the release APK on every push. Use release builds on the
watch: they're optimized and much faster than debug builds, and they're signed
with the same shared key so they install over each other. Every build of `main` is also
published as the `latest` release, so this link always serves the newest APK:

https://github.com/mikeyd433/WatchGameNumberOne/releases/download/latest/bezel-town.apk

## Google Play (internal testing)

`./gradlew :app:bundlePlay` builds `app/build/outputs/bundle/play/app-play.aab`:
the same optimized build, signed with the private Play upload key. The key is
never committed. Point the build at it with `BEZEL_UPLOAD_KEYSTORE` (path to the
`.p12`) and `BEZEL_UPLOAD_PASSWORD`. In CI, the repo secrets
`BEZEL_UPLOAD_KEYSTORE_BASE64` and `BEZEL_UPLOAD_PASSWORD` do the same, and each
`main` build attaches `bezel-town.aab` to the `latest` release:

https://github.com/mikeyd433/WatchGameNumberOne/releases/download/latest/bezel-town.aab

Play re-signs apps with its own key, so Play installs and sideloaded installs
can't update each other. Uninstall when switching between them.

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
