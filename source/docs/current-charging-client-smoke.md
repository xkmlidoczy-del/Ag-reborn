# Actual Current Charging client smoke (m05 continuation)

**PASS for bounded actual item charging, finite capacity, GUI gating, release/repeat and graceful close.** Executed2026-10-01 UTC in the dot cloud desktop, real Minecraft1.21.1 NeoForge21.1.252 client, Mesa llvmpipe; no user's computer or credentials. Existing isolated Creative QA save `Cloud QA20261001` was reopened in a genuinely new client process. The saved Electromaster level5/OFF display, developer and iron inventory were visibly restored. This visible reload is separate from the exact native two-JVM persistence probe.

## Actual observations

- The existing explicitly granted test mastery includes Current Charging. No normal survival acquisition claim
- `/academy dev charge0` set the finite portable developer test battery to0IF; real right-click developer UI displayed0/10000
- Done closed the GUI; Z activated abilities; a physical3000ms H hold followed by release charged the held developer to2100IF
- A physical1000ms H press while the developer GUI was open left2100IF unchanged
- Done plus a new1000ms H hold advanced the battery to2765IF; no automatic hold resumed when the GUI closed
- A subsequent15000ms held H filled the same battery to exactly10000/10000IF. Its final native saved item component independently contains10000IF
- Empty-hand block-mode H was also held, released and repeated in first/third person without a client crash. Available screenshots did not establish visible charging arcs; do not call this a visual pass
- Save and Quit to Title logged player/world/dimension saves. Quit Game removed the Minecraft window; launcher ended BUILD SUCCESSFUL and CLIENT_GRADLE_EXIT=0

The physical key durations are observations, not independently measured native tick counts. Synthetic native world tests independently cover exact ordering/transfer values and item/block FE providers. This Creative test does not demonstrate survival CP exhaustion, all machines, energy-network integration or multiplayer.

## Evidence and limits

- `runtime-evidence/m05-current-charging-client.log`: actual startup/world/screenshot/save/clean-exit output
- `runtime-evidence/current-charging-client/`: four game-native inspected battery-state PNGs and byte hashes
- `runtime-evidence/m05-charging-client-save.json`: independently parsed final native player NBT, including finite10000IF developer component

Default OpenAL again had no audio device, so charging sound is not audibly verified. Public-key services are unavailable in this offline test environment; this is integrated-server local transport, not authenticated external multiplayer. Block-target surround, real supported-machine charging, pixel/animation parity and sound comparison against classic1.0.7 remain unverified. The developer GUI and ability HUD in this run were still development placeholders.
