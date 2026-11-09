# Actual classic HUD and preset client verification (m06)

**Passed bounded real-client shader/widget/editor/persistence checks,2026-10-01 UTC.** Two genuine client processes on the dot cloud desktop, Minecraft1.21.1/NeoForge21.1.252, llvmpipe software rendering. No user computer or existing user save. The m05 disposable QA save was copied to `run-client-m06`; fresh options verified exact V/C/N/LMB/RMB/R/F defaults.

## Actually observed

- Real layered diagonal CP widget and category-alpha cutout, source mouse/keyboard/skill icons, and full-screen category mask replaced the two old placeholder panels
- V1200ms hold then release left activation ON; a native F2 frame captured source-style AWT CP10523/10523 and OL0/602 labels during release fade
- N opened the actual preset editor. Selecting Arc for LMB, Charging for R and Railgun for F populated source icons/labels. Selector omitted already-bound skills. Esc returned to world with corresponding skill/key hints
- Save and Quit then Quit Game ended with successful Gradle/zero client exit. A truly new client process restored the same three mappings; the reopened editor and HUD visibly matched. Independently parsed native saved NBT matches all four mappings and inventory across the processes
- C switched to an empty page, hid the skill hints and displayed the selected four-page strip. Four accepted switches returned to page1; live150ms C input was used because near-zero-duration synthetic taps can fall between20Hz polls
- V150ms press/release hid the HUD, and another restored it. Buffered CP fill animation was observed during reactivation
- Both client processes loaded the three new shaders with no Academy shader-load warning, no Academy missing-resource warning and no crash. Both integrated-server stops saved all dimensions and both clients quit normally

The second run disabled only vanilla onboarding hints in this disposable QA client's options so they no longer obscured the top-right CP widget. The QA camera looks at the ground; native screenshots were not edited. An attempted vanilla teleport-camera command was rejected and is not a tested success.

## Evidence

- `runtime-evidence/m06-classic-hud-client.log` and `m06-classic-hud-client-restart.log`: real runs and zero-exit markers
- `runtime-evidence/classic-hud-client/`: inspected game-native HUD, persisted editor and empty-page screenshots with hashes
- `m06-client-save-before-cold-reopen.json` and `m06-client-save-after-cold-reopen.json`: source mappings and inventory exactly preserved; revision advanced3→7 because four actual preset switches were subsequently made

## Explicitly unverified

This is one GUI scale/English/default resource-pack observation. Overload-warning/interference visuals, alternate scale, full shader-pack interoperability, sustained input under stress, GroundShock live particles/camera, coin QTE icons, full original GUI/font/pixel parity and1.7.10 matched-camera comparison remain unverified. Native server tests cover bounded logic but do not render. Audible audio remains unavailable; public profile/key services failed in the offline cloud environment. These were integrated-server clients, not external/socket multiplayer or packaged production installation.
