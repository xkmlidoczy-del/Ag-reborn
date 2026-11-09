# TEST0.70 NEW441 矢量囚笼

Source: original design workbook NEW441, SHA256 10f86d8734a237fbf590d153206fda86f6f3c9b7758adc30f989f9a0685fe871. Workbook defines four opposing pressure walls, whole periodic 2.5D, crouched lower-damage slowing mode, 56 m / 8 m area, no permanent player lock. This implementation does not affect players or unsupported entities.

## Explicit provisional implementation tuning

First short release marks a first-visible supported location; second short release confirms. Crouch is frozen at the first mark. A hold of at least six native server ticks clears, and a fresh short press while active cancels. Preview expires after 200 ticks. Empty or invalid confirmation is free. No target reacquisition after payment.

The field is an axis-aligned 8×8 m square, 3 m tall, checked clear and loaded throughout. Its four finite wall faces move inward over six ticks at ages 6..11, 26..31, 46..51, 66..71; lifetime 80 ticks is immutable. Continuous face/AABB intersection qualifies each attempt. This is neither an instant filled-area hit nor a physical block cage. Context-sensitive scaffolding and powder-snow cells are conservatively unsupported. Terrain changes terminate the field; no blocks are modified and no missing chunks are loaded. Field/contact points stay within 56 m of original and current caster.

At most eight frozen certified native hostiles are selected from a bounded local 64-candidate scan; overflow refuses. Four pulses share one exact 2.5D total, or 0.5D total in low mode, globally capped at 1e20. Shares are fixed at confirmation, with the final original slot receiving decimal remainder. Missed, canceled, dead, late, or blocked slots are discarded, never reassigned. Per-target actual-attempt 20-tick and shared same-tick fences remain independent of damage and CP.

Whole-cast continuous CP anchors at q1..5 are 24/12/6/2/.1. Cooldown anchors 40/40/40/40/4 native ticks; remaining real-player-tick debt persists across save and reconnect. These are tuning, not new classic XP denominators. No damage or mastery is awarded for empty preparation. Paid later misses remain spent.

## Native compatibility

A closed permit uses the existing native PLAYER_ATTACK chain and real swept-contact source position. Armor, resistance, shields, hurt cancellation and iframes remain authoritative. Only the same native hurt knockback direction may turn inward; its original strength, resistance, cancellation and unrelated velocity remain native. Any authenticated handheld-shield branch suppresses additional inward control and slowing, even when an ordinary partially blocked native hit retains residual damage. Exact high-power partial shields keep their prior conservative certification boundary.

Low mode may add one native Slowness II 20-tick effect after actual positive accepted HP or absorption loss and revalidation. Existing Slowness wins. No foreign effect is merged, refreshed, removed, replaced or restored. If later ownership cannot be proven, native expiry handles it. Canceling stops new applications; an already applied effect can last up to 20 ticks after cancel (external extensions remain external). No permanent restraint, teleports, player locking or forced movement flags.

## Visual contract and evidence

Receipt-driven transparent white-gray pressure faces, sparse inward marks and true-contact contractions use original assets. Preview, warmup, compression, rest and terminal states are distinct. No contact cue for a rejected/zero hit. Low mode uses open rails. Global decoration budget 2048 vertices, at most four controllers, no particles; whole-group fallback preserves a sparse real-geometry outline. All coordinates subtract camera in double before float submission, with depth occlusion and bounded lifetime.

Native and actual20TPS results are recorded separately under verification/v78-vector-cage: 1970 native tests pass; the full451-task check retains only the three historical guards. Actual Advanced learning7040IF, normal binding, both modes, blocked refusal, free preview clear, resource reload and save/rejoin passed in a declared NoAI single-player fixture. The normal full4-pulse sequence is recorded at20TPS. Surviving-target Slowness duration and exact active-cancel timing remain native-test evidence only. The improved gray-white outlines are functional geometry, not final polished spectacle. Exact HP/S/q/Q/child balances and inventory matched the previous save; no mastery was awarded. Existing three historical hash-guard failures are retained; the separate v10 Charging companion proves only the reviewed 441 registry append and current Charging semantics. Prior .69 source/JAR and originalM41 remain immutable fallbacks.
