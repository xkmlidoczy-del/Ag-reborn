# Classic commands and source lifecycle, m23

Baseline: supplied AcademyCraft1.0.7 (`00d19ec0cf538f61c1095c9292f5ee6863db4521`) and LambdaLib1.2.3. Original command, ability, preset, CP and DataPart bytes remain unchanged. This increment adds `/aim`, `/aimp` and `/acach` to the production NeoForge command registration. It does not grant natural progression completion or full visual/audio parity.

`/aim` targets its player sender. The original `PlayerPersisted.aim_cheats` marker, `cheats_on` warning and `cheats_off` are retained. A commands-enabled world automatically reactivates the marker on the next command, as in the source. `/aimp PLAYER SUBCOMMAND` targets an exact-case online player name; console `/aim` has no player target. `/acach ID [PLAYER]` uses the native name lookup and defaults to the sender. These three roots require permission level4, reflecting their inherited CommandBase administrator surface; no ordinary survival acquisition depends on them.

The classic shared subcommands are `help`/`?`, `cat`, `catlist`, `learn`, `unlearn`, `learn_all`, `reset`, `learned`, `skills`, `level`, `fullcp`, `exp`, `cd_clear` and `maxout`. Category-local integer skill IDs keep the original catalog order and include disabled registrations. Administrator learning bypasses level/developer/dependency requirements. This remains distinct from physical developer learning.

Important source behavior is preserved:

- Same-category and same-level commands are noops; a direct category change keeps a positive level, raw level progress, trained growth and activation, while clearing skill/preset/cooldown identities. Removing a category sets level0 and deactivates. Ordinary developer acquisition/reset still uses its existing separate path
- Unlearning clears a learned bit and retains its raw mastery. Relearning restores it. Unlearning does not fire a SkillLearnEvent or recalculate cached CP maxima. Raw preset identities remain stored after unlearning or lowering a level; authenticated cast ingress still rejects ineligible skills
- `learn_all` sets learned identities directly and does not invent individual learning events, refill CP or recalculate passive maxima. The original omitted `unlearn` help entry and first-name omission in `learned` formatting are retained
- `fullcp` directly sets CP to the maximum and overload to0 while retaining the overload-fine flag and both recovery delays. `maxout` sets raw level experience to100. The experience setter uses source float precision, cannot learn a missing skill, and preserves the customized radiation getter
- A known `/acach` ID reports command success even if its immediate parent or repeat gate prevents a new award. Unknown IDs report failure. Actual backing advancements still enforce source parent/event ordering

Modern safety adaptations: malformed/missing arguments and nonfinite mastery fail without throwing or poisoning state; command reentry during a consumption callback is rejected; authoritative category/level/unlearning mutations cancel existing held input/development. Retained training from a direct category reset is bounded by the greatest configured finite capacity across all six levels, rather than incorrectly discarding it at level0. Unknown/foreign/passive saved controls remain filtered. These bounds do not add a natural recipe or acquisition shortcut.

## Cooldown correction

`CooldownData` calls `setTick()` and `setClearOnDeath()` but **never** calls `setNBTStorage()`. LambdaLib `DataPart.needNBTStorage` defaults false, and `EntityData.saveNBTData`/`_constructPart` conditionally write/read only opted-in parts. The previous port persisted cooldowns to player NBT and retained them on death; its earlier m06 cold-persistence test demonstrated that port behavior, not source equivalence.

Production disk serialization now omits both cooldown ledgers, and loading an older port save ignores their fields. The live network snapshot still carries remaining/maximum values for the HUD. A nondeath native clone transfers the live cooldown part; genuine player death clears it. Category change and `cd_clear` still clear it; unlearning alone leaves a live cooldown intact. Raw mastery, trained capacities, presets, finite item energy and other genuinely persistent state remain saved.

## Evidence and qualifications

- `m23-native-command-dispatcher.log`: first5 production-dispatcher fixtures passed
- `m23-native-command-lifecycle-fixed.log`:6 production-dispatcher/lifecycle fixtures and26 standalone state/real compressed-NBT contracts passed. The first lifecycle attempt incorrectly passed native `restoreFrom(..., false)`, which means a death clone; its retained-cooldown expectation failed. The fixture now uses the actual nondeath `true` flag, and genuine death is tested separately
- `m23-command-source-disk-restart.log`: two distinct real Minecraft JVMs passed actual PlayerDataStorage save/load, graceful escrow refund, fixed unlearned mastery/preset identity, opt-in marker, empty cooldown restart, finite energy, native Anvil devices and five bounded world ticks
- `m23-command-native-player-proof.json`: independently reads the actual closed player.dat and certificates: seedPID204, verifyPID481, Arc unlearned with0.375 retained mastery and raw presets, three learned skills, opt-in true, no disk cooldown fields
- `m23-all-content-source-lifecycle-fixed.log`: all354 default native tests and the188-task combined native/check/JAR build passed. The first full native run found two earlier tests whose asserted cooldown persistence/death behavior contradicted the unchanged original DataPart witness; their expected genuinely persistent state and transient cooldown reset were corrected, retaining full other-field equality and the failure log

Fixtures deliberately provide levels, mastery, devices and materials to isolate command/persistence behavior. They do not prove natural collection or rendering. The dedicated server public-key fetch remains unavailable in this restricted cloud; local admitted GameTest players do not use an external authentication handshake. Optional IC2, full natural survival, exact remaining audiovisual parity, broader foreign-mod/network compatibility and legacy-world import remain separate open boundaries.
