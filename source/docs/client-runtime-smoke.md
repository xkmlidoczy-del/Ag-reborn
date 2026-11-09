# Actual cloud client smoke test

## Result and scope

**PASS for the bounded startup/resource/input/save-reopen smoke increment**, executed on 2026-10-01 UTC using Minecraft 1.21.1, NeoForge 21.1.252 and the AcademyCraft development classpath. The launcher ended with `BUILD SUCCESSFUL` and `CLIENT_GRADLE_EXIT=0`; the Minecraft window closed through Quit Game. This is not a complete-port, packaged-installation, sound, multiplayer or fidelity pass.

The isolated world was `run-client/saves/Cloud QA 20261001`: Creative, commands enabled, Superflat, structures disabled, seed 20261001. The test identity was the development launcher profile. No user computer, credentials or existing user save was used.

## What was actually observed

- The real client loaded AcademyCraft, displayed its welcome/menu screens and entered the new world using the cloud desktop's Mesa llvmpipe rendering
- `/academy dev electromaster` supplied explicitly marked test progression. `/academy status` returned authoritative state, and the live HUD displayed Electromaster Lv.5, CP and ON/OFF
- An initial real resource load exposed missing classic item sprites. After the atlas-source fix, actual F3+T reload completed with **zero new AcademyCraft missing-texture warnings**. The real Tools & Utilities tab visibly showed empty/full portable developer, needle, coin, magnetic coil and all four induction-factor colors
- A real portable developer charged through the test command opened the DeveloperScreen with 10000/10000 IF, learned-skill labels, IDLE state, Abort and Done. R/G/Z presses while that screen was open left the visible screen/state unchanged. Done returned to the world; the next Z press toggled OFF, and another toggled ON
- A 1300 ms held G with a main-hand iron ingot fired a real railgun. A saved F2 frame contains the rendered bright beam/blue-white arcs; the client remained running
- After world reopen and explicit activation, a 150 ms R press produced an actual entity damage flash and visible arc in an F2 frame. Another R press/release completed without a crash. No exact animation-timing or damage-value measurement is claimed
- Save and Quit to Title stopped the integrated server, saved players, and logged all dimensions saved. Reopening **the same world within the existing client JVM** restored the OFF state, progression, inventory and developer energy
- Before/after `/academy status` strings matched: `electromaster level 5 CP 10501/10501 overload 0 progress 0.0075075075075075074`
- Independent reads of the freshly saved player NBT before and after reopen matched every recorded ability-progress field, inventory/component value and selected slot. This includes CP 10501.125, extra CP 1.125, extra overload .696, level XP .005, active=false, empty cooldown map, the charged developer's 10000 IF and four iron ingots. The post-reopen file was freshly written after pause/save; it was not just a reread of the earlier timestamp

## Evidence

- `runtime-evidence/client-launch.log`: complete launcher output and zero exit marker
- `runtime-evidence/client-before-atlas-fix.log`: original missing-texture finding
- `runtime-evidence/client-smoke/client-latest.log`: actual game/resource reload, two integrated-server sessions, status, screenshots and clean shutdown
- `runtime-evidence/client-save-before-reopen.json`, `client-save-after-reopen.json`, `client-save-comparison.json`: selected saved-NBT comparison
- Six inspected, game-native PNGs under `runtime-evidence/client-smoke/`: classic-item-icons, railgun-live, arc-live, state-before-save, state-after-reopen and developer-after-reopen. `manifest.json` records dimensions and SHA-256 checksums

Minecraft's local log/screenshot names use 2026-09-30; the actual session date above is UTC.

## Remaining limits

- Default OpenAL failed to open an audio device. Minecraft turned off sounds/music and continued; no null fallback was applied to this actual session. Audible sound is **not verified**
- Public-key/profile service requests were connection-refused in this cloud/offline development environment. The session was a local development-profile test, not authenticated external multiplayer
- This tested a genuine integrated-server stop/reopen, not a cold client-JVM or standalone dedicated-server restart
- Half-charge icon thresholds, factor tooltips/invalid data, normal survival acquisition, timed development completion, coin QTE, the other three categories, death/dimension flows, extended holds/overlaps, frame rate and packaged production installation were not covered by this increment
- Original 1.7.10 matched-camera captures and sound comparison remain necessary for audiovisual parity. The development UI itself still labels original GUI/machines as pending
