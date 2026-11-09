# Serial integration instructions

Do not overwrite fresh production files wholesale if another worker has changed them. `presets-core.patch` contains only owned deltas relative to the production snapshot taken at task start; review conflicts manually. New source files and tests are also available under `.staging/presets/src`.

## New owned files to copy

- `src/main/java/cn/academy/port/core/SkillPresets.java`
- `src/main/java/cn/academy/port/preset/PresetSkills.java`
- `src/main/java/cn/academy/port/client/ClassicActivationKey.java`
- `src/main/java/cn/academy/port/client/ClassicPresetEditor.java`
- `src/main/java/cn/academy/port/client/PresetEditScreen.java`
- `src/main/java/cn/academy/port/gametest/AcademyPresetRuntimeTests.java`
- `src/test/java/cn/academy/port/PresetRegressionTest.java`
- `docs/PRESET_PORT.md`

## Owned copied-file deltas

- `AbilityProgress`: preset field/category clear; independent max-cooldown map, setter/accessor/tick cleanup
- `AbilityStorage`: schema 2 sparse preset encoding/recovery and cooldown maxima
- `AcademyGameplay`: authoritative `requestFromClient`, slot resolution and preset validation/cancellation; Arc/Railgun use the max-aware cooldown setter; trusted low-level fixture entry point remains
- `AcademyClient`: source V/C/N and LMB/RMB/R/F defaults; physical latch semantics, true short-release activation, mapped delegates, local mapping prediction and server replacement, public HUD bridge, vanilla-key suppression; placeholder HUD rendering remains for HUD implementation integration
- `DeveloperScreen`: add GroundShock to the supported learning list
- `ClassicCoinEffects`: replace fixed `AcademyClient.RAILGUN` label with `skillKey("railgun")` lookup; HUD implementation should remove this old standalone simple coin bar and render coin state through KeyHintUI instead
- `skill/DirectedShock`, `skill/ThreateningTeleport`, `skill/ElectronBomb`: three narrow `cooldowns.put(...)` to `setCooldown(...)` replacements, so max ticks are captured before decrement

Merge only added language keys from `docs/PRESET_LANGUAGE_ADDITIONS.json` into each language JSON; do not overwrite translations another worker has changed. Existing preset strings and textures are already migrated.

## Required production integration (not changed by this staged implementation)

1. In `AcademyNetwork.register`, change the server callback call from `AcademyGameplay.request(player,request)` to `AcademyGameplay.requestFromClient(player,request)`. This is mandatory: otherwise trusted skill-name fixtures remain exposed over the wire.
2. In `CoinTosses.canUseRailgun`, require `state.presets.currentContains("railgun")` together with category, canUse and catalog gates. This reproduces source coin QTE eligibility. Add a Railgun mapping in the existing native coin-ready fixture and verify an unbound learned Railgun offers no QTE.
3. Add a `presetTest` JavaExec task using `cn.academy.port.PresetRegressionTest`, depending on `testClasses`, `sourceSets.test.runtimeClasspath`, assertions enabled; make `check` depend on it. No build.gradle changes were made in staging.
4. Merge HUD staged sources. Replace AcademyClient's old `hud` body with `ClassicAbilityHud.render(...)` using its Inputs/KeyHint API; remove the old independent ClassicCoinEffects.onRenderGui bar. Use the bridge below.
5. Extend cold/disk restart fixtures with explicit mappings and selected index assertions. Normal acquisition/learning remains empty. If `/academy dev <category>` receives an auto-filled convenience preset, clearly document that it is operator-only test initialization; never use it as normal learning behavior.

## HUD bridge

- Current index: `AcademyClient.state.presets.current()`
- Slot skill: `state.presets.currentSkill(slot)`
- Slot binding: `AcademyClient.SLOTS[slot]`
- Include delegate: `AcademyClient.delegatePresent(slot)` (correctly handles physical-key collisions)
- Logical active state: `AcademyClient.delegateActive(slot)`
- Activation physical hold: `AcademyClient.activationHeld()`; HUD supplies its own 200ms number delay
- Slot cooldown remaining: `state.cooldowns.getOrDefault(skill,0)`
- Captured max: `state.cooldownMaximum(skill)`
- Railgun key lookup: `AcademyClient.skillKey("railgun")`
- Railgun visual state: coin pending uses `ClassicCoinEffects.ready()` for ACTIVE/CHARGE; iron-ammo held charge is CHARGE; no pending/hold is IDLE
- V special hint: when any delegate is active, binding label + `ac.activate_key.endskill.desc`
- Consumption hint for the original six active skills is 0; GroundShock supplies `ClassicGroundShockEffects.consumptionHint()` and readiness via `localPrepareTicks()` (-1 none, 0–4 CHARGE, ≥5 ACTIVE)

## GroundShock integration already applied to owner copies

At the integration owner’s request, these hooks are already applied in staged AcademyGameplay, AcademyClient, PresetSkills and DeveloperScreen. GroundShock runtime/effect classes were copied into the isolated compile tree to verify them together, but artifact ownership remains with the GroundShock implementation. Copy that worker’s new runtime/effect/test files alongside the preset owner deltas; do not enable the allowlist without them. In GroundShockWave, use `state.setCooldown(ID,p.cooldown())` for the true HUD denominator. Client physical press/startLocal, release/abortLocal, session clear, receive cases, server held dispatch and lifecycle cancellation are integrated.

## Verification status

Cached-API standalone compilation passed. PresetRegressionTest: 131 assertions passed. Five native GameTests compiled, not run. Source UI layout was inspected against original XML, textures and tutorial screenshot; no live MC editor screenshot was captured, so full pixel parity remains unverified. Integration checks cover aggregate check, GameTest run, disk restart and client smoke testing.
