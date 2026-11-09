# NEW253 远置散射中继 — TEST0.77 implementation contract

Status: implementation in progress; this document is not test acceptance.

## Provenance and scope

ACA_LV1-5_10的20次方上限设计_v1.1.xlsx, 原子崩坏 row19 and 全技能特效卡 row34.
Workbook SHA256: 10f86d8734a237fbf590d153206fda86f6f3c9b7758adc30f989f9a0685fe871.
The workbook specifies up to four relay plates, manually selected beam paths, visible targets around cover, harmless measurement, 32m per segment and one fixed 2.4D multiplier. It does not specify this implementation's controls, lifetime or CP/CD anchors.

Approved interpretation: four relay nodes give up to five actual segments (hand→nodes→fixed endpoint), each at most 32m and total at most 160m. This is a current implementation interpretation of “每段32米”, not a claim about original ACA behavior. It is distinct from NEW221's two specular reflections and one cumulative 40m budget. NEW221 is unchanged.

## Controls and identity

LV5, learned NEW221 prerequisite. Crouch and input identity are captured on the authenticated press; dispatch occurs after native movement/sneak input.

- Crouch short release: append one currently visible node, in explicit route order, maximum four
- Normal short release with no endpoint: select one currently visible fixed world endpoint
- Next normal short release: confirm the selected route and attempt one terminal ray
- Normal hold at least 12 server ticks: inspect harmless measurement, no shot
- Crouch hold at least 12 server ticks: clear the route freely

Appending a node clears the old endpoint selection. Node numbers, per-leg distances and selection state are visible. No automatic routing, enemy search, target following, node skipping or endpoint substitution. After selecting the endpoint the caster may walk to connect to the first node, while all actual route bounds still apply. The selected point is not a hidden target tracker.

The first successful node starts a fixed 400-server-tick lifetime. Editing, firing and measurement never extend it. All paid nodes clear on genuine input/session/world/category/preset invalidity, death, disconnect or expiry. No saved active route or refund exists. CP/CD and 400 ticks are provisional tuning.

## Costs and native commit

Continuous q1→q5 anchors, matching the existing NEW251 tuning family:

- Per-node CP: 4 / 2 / 1 / .2 / .01
- Per-shot CP: 80 / 40 / 20 / 4 / .2
- Shot cooldown ticks: 80 / 50 / 30 / 15 / 1

A deployment validates the full finite plate, visible position, prior connection, identity and ledger before its callback-free debit/publication. Failed deployment is free.

A shot freezes one 2.4D allocation, actual victim, all segments, final-segment arrival direction, CP ledger identity/balance, q and cost. It enters native hurt once. The final pinned commit seam follows Incoming/Pre and every ABSORPTION modifier, before native HP or absorption subtraction. Payment occurs there once, after final native and local identity/resource validation. No second damage call or direct health edit is used.

Canceled, amplified, geometry-mutated, iframe-rejected, zero-input or fully held-shield-blocked attempts are free. Genuine positive damage wholly consumed by absorption pays once. NEW212/242/252 use explicit certified final-segment intake, existing precedence and their own finite contact/gap rules. Armor, directional handheld shields, resistance, native absorption and hurt cooldown remain native.

CP and target HP/shield are ordered commits, not a cross-entity atomic transaction. A callback throwing after native HP/absorption changes but before the genuine Post may leave those native changes with paid CP and no later shield settlement. No speculative refund is issued after an unknown native/Post callback exception: retire the emission and route, preserve any committed payment/native effects, show no success. An internal payment-function fault retires that single emission and reports refusal; its closed callback-free ledger operation is not claimed to provide arbitrary-fault rollback or automatic route removal. Ordinary native durability or shield effects that occurred before a late veto are not rolled back. This is not a guarantee against arbitrary third-party callback side effects.

## Collision and information boundaries

Every complete segment is checked through loaded short native prefixes, including actual hand connection, finite plate clearance, world border/build limits and interaction permissions. All intermediate hittable bodies block the whole route, including unsupported, allied and returning-caster bodies. Solid contacts win exact body ties. Only the final segment can produce one native damage transaction. Zero-length, nonfinite, excess range, unloaded or overloaded queries refuse. No chunk loading or terrain damage is performed.

The native query has at most five legs, at most 128 quarter-meter prefixes per leg and 64 distinct candidate bodies per leg. Route revalidation may repeat this bounded work at closed native guards. No unbounded world-wide entity search is added.

## Visual contract

Original green-white MD identity and existing original mdshield texture. Dotted harmless previews follow only validated prefixes and stop at blockers. Solid emission uses an actual admitted path, with node convergence. Damage/absorption feedback requires the corresponding native result; cancellation, miss and retained cosmetic light do not imply a hit. No particles. Shared 4096-vertex scene admission includes decoration/text; overflow falls back as a complete owner group, never hides the classic useful geometry. Double-relative camera coordinates, native depth and near-eye clipping remain required.

## Required evidence before delivery

Pure boundaries/continuous costs; native authenticated controls and learning; 1–4 nodes and five-leg range; frozen endpoint after walking; wall/body/loading/permission refusal; callback CP/position/session mutation; cancellation/zero/amplification; ordinary and exact damage; final-side shield/212/242/252; replay and synthetic Post; immutable expiry and save/reconnect clear; real 20TPS selected route, around-cover attempt, measurement, blocker refusal and reload. Historical guard failures remain reported independently.

## Actual delivery evidence (2026-10-15)

The final retail JAR was used in an isolated cloned singleplayer world at normal 20 TPS. NEW221 prerequisite and NEW253 were genuinely learned through developer GUIs, and NEW253 was bound to R through the normal preset page. For NEW253 only, the Advanced machine was test-prepared from 2960 IF to exactly 9600 IF; real GUI development consumed it to zero. This preparation is not natural power supply or player-resource conservation evidence. No player CP/HP/S/XP was edited for learning.

Actual input evidence covers one deployed node, ordinary walking, manually selected fixed endpoint, terminal native Husk damage/death, an already-existing wall refusal, crouch-hold clear, resource reload, normal save/rejoin and empty route cleanup. Rejoin comparisons preserve HP/S including child shares, CP, inventory, skills, presets and the stored relay cooldown. CP naturally recovered to its cap during these observations, so exact individual live debit accounting is established by native tests, not inferred from the video.

The target is a declared NoAI/NoGravity/PersistenceRequired Husk fixture. Initial recordings with missing or uncontrolled targets were retained as failed prerequisites. A bounded around-cover input attempt did not establish a valid route and is not a success. Complete four-node and around-cover client operation remains unverified; this is a usability/acceptance gap, not a claim of full NEW253 acceptance. Native tests cover the geometry and transaction cases.

The 20fps Kdenlive recordings are continuous game-window crops, without manufactured effects or speed changes. The small node and thin beam are functional visuals; full visual polish, multiplayer and default-hardware performance are not accepted. HUD overlap and menu/short-chord usability remain follow-up work.

2114 unique native tests across 19 isolated namespaces passed. The full check ran 484 tasks with 10 failures; 48 targeted follow-up tasks passed, including the two corrected current UI/registration tests. Eight original contract/identity failures remain, including the three historical geometry guards. Current companion proofs do not erase those failures. This is not an all-green aggregate.
