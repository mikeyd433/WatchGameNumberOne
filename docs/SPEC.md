# Bezel Incremental Game — Design Spec

Working title: TBD

A standalone incremental game for the Samsung Galaxy Watch 4 Classic (Wear OS), built around the physical rotating bezel. The player walks a tiny character through a series of settings, plays WarioWare-style microgames inside buildings to upgrade them, and lets those buildings earn money idly. Money unlocks buildings and pays a gatekeeper to move on to the next setting.

---

## 1. Platform & Tech Stack

**Target device:** Galaxy Watch 4 Classic (Wear OS), round display, rotating bezel, gyroscope/accelerometer, haptics. Runs entirely on the watch; no phone companion required.

**Decision: Kotlin + Jetpack Compose for Wear OS + Filament**

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin | Native Wear OS language, first-class tooling |
| UI / menus / HUD | Jetpack Compose for Wear OS | Round-screen components, curved text, swipe-to-dismiss built in |
| 3D rendering | Filament (Google) | Lightweight PBR/unlit renderer for Android, good fit for low-poly scenes |
| Particles | Custom, instanced quads | One draw call, simple 2D screen-space sim |
| Physics | Hand-written | Needs are small: rotating bodies, damping, circular-edge collision |
| Persistence | DataStore (Proto or JSON) | Small save file, atomic writes |
| Input | Rotary input + touch + sensors | `onRotaryScrollEvent`, gravity, gyroscope |

**Why not Godot:** Wear OS support is unofficial, rotary input and round-screen layouts need workarounds, and the runtime is heavier on a small battery. The physics and 3D needs here are modest enough that native wins on integration, battery, and polish.

**Deployment:** Android Studio, sideloaded via wireless ADB during development.

**First technical milestone:** a low-poly cube rendered with Filament that spins in response to the bezel, with a haptic tick per detent.

---

## 2. Core Loop

```
Play microgame  →  earn that building's Credits
Spend Credits   →  upgrade that building (Skill / Automation / Storage)
Upgraded building  →  generates Money idly
Money  →  unlock new buildings  →  pay the gatekeeper  →  next setting
Money past the threshold  →  production bonus + Tokens
Tokens  →  permanent global upgrades
```

Playing is how you grow. Idling is how you earn.

---

## 3. Currencies

| Currency | Earned by | Spent on | Scope |
|---|---|---|---|
| **Credits** | Playing a building's microgame | That building's upgrades only | Per building, per setting |
| **Money** | Idle production from buildings | Unlocking buildings, paying the gatekeeper | Per setting |
| **Tokens** | Overflow money when leaving a setting | Global upgrades | Permanent |

- Playing a microgame never earns money directly.
- Credits are tied to each building. Bakery credits only upgrade the bakery.
- Credits and money reset when entering a new setting. Tokens persist.

---

## 4. Overworld

- Horizontal side-scrolling strip through the middle band of the round screen.
- The player's sprite is very small. **Turning the bezel walks the character** left/right.
- The character snaps to each doorway with a haptic click.
- **Tap** to enter the building/object you're standing at. **Swipe right** to exit (standard Wear OS dismiss).

**Layout of each setting, left to right:**

```
[Arcade Cabinet] [Building 1] [Building 2] [Building 3] [Building 4] [Building 5] [Gatekeeper + Portal]
```

**HUD (uses the round screen's shape):**
- Top curve: money, in curved text
- Bottom curve: progress toward the portal threshold

**Building states:**
- Locked: scaffolding/silhouette with its price floating above
- Unlocked: animated to show idle activity (smoke, workers, particles); this is the idle dashboard, so no separate income screen is needed

---

## 5. Buildings & Microgames

Each setting has 5 buildings. Each building is a short, absurd, single-action microgame driven by the bezel, the touchscreen, or both.

### Input rule
Each game uses whichever input fits it best (not a player toggle):
- **Bezel:** anything rotational or detent-based
- **Touch:** direct manipulation, aiming, and tapping targets
- **Both (in sequence):** reserved for each setting's finale, combining what the setting taught

Target mix per setting: 2 touch-led games, 2–3 bezel-led games, and a finale that uses both. The bezel stays the star.

**Bezel motion types:**
- **Spin:** keep something rotating, often within a speed sweet spot
- **Back-and-forth:** reverse direction rhythmically
- **Precision:** stop on or dial to an exact target, using detents and haptics

**Touch motion types:**
- **Swipe:** repeated strokes across an object
- **Drag:** move something directly to a target
- **Flick:** drag back and release to throw
- **Tap:** hit targets as they need attention

Buildings within a setting are ordered to introduce inputs progressively.

### Building screen
- The microgame itself
- That building's credit balance (shown here, not in the overworld HUD)
- The three upgrade tracks

### Upgrade tracks (every building)

| Track | Effect |
|---|---|
| **Skill** | More credits earned per play |
| **Automation** | Higher idle money production |
| **Storage** | More money banked while the player is away |

### Balancing
- Costs and rewards are tuned **per microgame**, not globally. Harder games award more credits per play or have cheaper upgrades, so no building feels like a chore.

---

## 6. Settings

### Setting 1: Small Town (draft)

| # | Building | Microgame | Input | Motion |
|---|---|---|---|---|
| 1 | Bakery | Swipe across the dough to knead it until springy; flour puffs | Touch | Swipe |
| 2 | Water Tower | Crank a pump to fill the tank; overfill sprays | Bezel | Spin |
| 3 | Barber Shop | Turn the customer's chair to the right angle | Bezel | Precision |
| 4 | Radio Station | Tune through static to a frequency, feeling detents | Bezel | Precision |
| 5 | Clock Tower | Wind the clock with the bezel, then drag the hands to the time before the bell | Both (finale) | Spin, then drag |

Gatekeeper disguise: cheesy town tour guide.

### Setting 2: Carnival (draft)

| # | Building | Microgame | Input | Motion |
|---|---|---|---|---|
| 1 | Cotton Candy | Spin steadily to wrap floss without it toppling | Bezel | Spin |
| 2 | Ring Toss | Drag back and flick rings onto the pegs | Touch | Flick |
| 3 | Plate Spinning | Tap whichever plate is wobbling to boost it | Touch | Tap |
| 4 | Merry-Go-Round | Crank to the right speed for riders | Bezel | Spin |
| 5 | Strongman Bell | Pump the bezel back and forth to build power, then tap to swing the hammer | Both (finale) | Back-and-forth, then tap |

Gatekeeper disguise: carnival barker.

### Later settings
TBD.

---

## 7. The Gatekeeper & Portal

- Located at the right edge of every setting, beyond building 5.
- Visible from the start, standing by the locked portal, so the player knows where they're headed.
- **Personality: overly enthusiastic salesman.** Pitches the portal like a timeshare, hypes the player's bonus, acts wounded at a bare-minimum payment.
- Wears a new disguise each setting. Clearly the same guy; nobody acknowledges it.
- Visibly gets richer each setting from everything the player has paid him.
- Also runs the **global upgrade shop** (a stall beside the portal that gets fancier over time).

### Requirements to leave a setting
1. All 5 buildings unlocked
2. Money ≥ the setting's threshold

### Paying
- **Crank the bezel** to feed coins into his hand, one haptic tick per coin.
- A **tap shortcut** is added later.

### Portal visuals
- Size, color, and swirl speed scale with the current overflow bonus.
- Orbiting particles speed up as the bonus grows.
- Small curved-text readout of the bonus, e.g. "Leave now: +5%".

### Transition
Character steps in, screen swirls, gatekeeper waves goodbye, then reappears at the far right of the next setting.

---

## 8. Overflow Bonus

Money banked past the threshold when leaving becomes a production bonus.

```
bonus % = 5 × √(overflow ÷ threshold)
```

Example with a 10,000 threshold:

| Banked | Overflow | Bonus |
|---|---|---|
| 15,000 | 5,000 | +3.5% |
| 20,000 | 10,000 | +5% |
| 50,000 | 40,000 | +10% |
| 110,000 | 100,000 | +16% |

- Bonuses **add together** into one running total.
- All production is multiplied by `1 + totalBonus`.
- The coefficient (5) is a tunable constant and can be raised by a global upgrade.

---

## 9. Tokens

- Earned only from overflow when leaving a setting. Paying the bare minimum earns little or nothing.
- Use a diminishing curve, similar to the bonus.
- **Target pace: about one global upgrade per setting.** Tune so doubling the threshold earns roughly the cost of one upgrade; a minimal overflow earns a fraction, a large overflow around two.
- Global upgrade costs rise gently to hold that pace.

---

## 10. Global Upgrades (permanent, bought with tokens)

| Upgrade | Effect |
|---|---|
| **Bonus Rate** | Raises the overflow coefficient (5 → 5.5 → 6 …). Steep cost scaling, since it compounds with everything. |
| **Head Start** | Start each new setting with some money, or the first building pre-unlocked |
| **Cheaper Gate** | Lowers each setting's portal threshold by a few percent per tier |
| **Offline Storage** | Increases how much every building banks while away |

Non-economy upgrades (microgame mastery, combo meter, faster walking, auto-collect, notifications) are out of scope for now.

---

## 11. Arcade Cabinet

- Sits at the left end of every setting, entered like a building.
- Replay any microgame from any setting visited so far, as **free play with no effect on the economy**.
- Tracks a **personal best** per microgame.

**Architecture requirement:** microgames only report a score when they end. A separate economy layer decides what that score is worth. In arcade mode, the economy layer ignores it.

---

## 12. Particles

- 2D screen-space particles layered over the low-poly 3D scene.
- Rendered as instanced quads / point sprites in a single draw call. Target: a few hundred, up to ~1,000.
- Fixed-timestep integration: gravity + forces + damping, then move.
- **Round-screen container:** particles past the screen radius are pushed back inside and bounce with energy loss, so they pile against the edge.
- Optional coarse spatial grid for particle-particle collisions where piling matters.

**Inputs:**
- **Gravity sensor (`TYPE_GRAVITY`):** which way is down; tilt makes particles slide
- **Gyroscope:** quick flicks toss particles
- **Linear acceleration:** hard shakes scatter piles
- **Bezel:** spinning adds a tangential swirl force

**Uses:** coin jar, flour puffs, water spray, confetti on milestones, portal sparks.

**First particle test:** a coin jar that fills, tilts with the wrist, and swirls with the bezel.

---

## 13. Offline Progress & Battery

- No background game loop.
- Save one global `lastSeen` timestamp. On resume, compute elapsed time once and pay out every building, capped by each building's Storage.
- Register sensors only while the game is on screen (`SENSOR_DELAY_GAME`); unregister in `onPause`.
- Always-on display: static, dimmed view; particles frozen or hidden.
- Cap particle counts.

---

## 14. Data Model (sketch)

```kotlin
data class GameState(
    val currentSettingIndex: Int,
    val totalBonus: Double,          // running additive bonus
    val tokens: Long,
    val globalUpgrades: Map<GlobalUpgrade, Int>,  // tier per upgrade
    val personalBests: Map<MicrogameId, Long>,
    val lastSeen: Long,              // epoch millis
    val setting: SettingState
)

data class SettingState(
    val money: Double,
    val buildings: List<BuildingState>  // 5 per setting
)

data class BuildingState(
    val id: BuildingId,
    val unlocked: Boolean,
    val credits: Long,
    val skillLevel: Int,
    val automationLevel: Int,
    val storageLevel: Int,
    val banked: Double
)
```

---

## 15. Visual Direction

- Low-poly 3D scenes inside buildings; chunky, readable objects.
- Tiny sprite character in the overworld.
- WarioWare tone: absurd, quick, slightly gross, funny.
- Big-number formatting from day one (e.g. 1.2K, 3.4M, 1.2e15).
- Haptics on every meaningful bezel interaction.

---

## 16. Build Plan

### Phase 0: Tech spike
- Filament cube spinning with the bezel, haptic per detent, on the actual watch.

### Phase 1: Vertical slice
- Small Town overworld with bezel walking and doorway snapping
- 2 buildings (Bakery, Water Tower) with playable microgames
- Credits, money, and one upgrade track
- Offline payout on resume
- Coin jar particle test

### Phase 2: Full first setting
- All 5 Small Town buildings and all 3 upgrade tracks
- Gatekeeper, crank payment, portal, overflow bonus, tokens
- Global upgrade shop
- Arcade cabinet and personal bests

### Phase 3: Second setting
- Carnival, gatekeeper barker disguise, transition sequence

### Later
- Tap shortcut for paying the gatekeeper
- More settings
- Balance passes on per-game costs, token pace, and the bonus coefficient

---

## 17. Open Questions

- Working title
- Settings after the Carnival
- Exact token curve and global upgrade cost scaling
- Per-building cost and reward numbers
