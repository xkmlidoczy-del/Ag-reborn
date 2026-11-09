# M26 classic category lifecycle checkpoint

Minecraft1.21.1 / NeoForge21.1.252 / Java21 development candidate, 2026-10-02.

Ordinary developer acquisition now changes category once. It promotes an uncategorized player to level1 without the extra level-zero recalculation or the port's false level-one achievement event. Advanced category reset retains activation, previous-level trained capacity and level progress during the category phase, then performs the original decreased-level reset and consumes the entire coil/factor slots in source order.

Category effects run synchronously at normal event priority. The verified classic order is cooldown clearing, CP recalculation/refill and preset clearing. High-priority observers see the primitive category/skill-slot mutation with the old resource/cache state; low-priority observers see the completed category effects. Selected preset identity, recovery delays and overload lock follow the source behavior. Skill contexts end through the existing context-only cleanup while the completing development process and its machine session remain owned.

Verified:

-1,176 comparisons against full unchanged classic developer actions and data/achievement handlers, with zero differences. The previous implementation exposed42 disagreements
- Actual native authenticated portable acquisition emits one category event and no level event or false acquisition achievement
- Actual native advanced reset preserves activation and the expected category/level calculation phases; its old VecDeviation context ends while the same process completes
- Actual physical advanced-machine reset preserves its original sender-bound nonce/session after context disposal
- Same-category no-op and category removal preserve the source event/state contracts
- Complete `check jar runGameTestServer`:192 tasks, all357 native world tests passed, successful in3m22s. Evidence: `runtime-evidence/m26-category-lifecycle-main-check-native.log`
- Six actual classic command/death/nondeath-clone tests passed after this event change. Evidence: `runtime-evidence/m26-category-classic-command-native.log`

The developer/category source oracle includes calculated resource bits, event phases, activation, cached maxima, growth/progress, learned/raw slots, cooldowns, all mappings/selected preset, inventory phases and achievement-event delivery. Immutable originals and existing numeric/invalid-state tests remain in place.

The M25 generated-material fixture separately completed genuine witnessed Overworld and native Nether harvesting,37 coal-fuelled smelts,50 consumed native crafting operations, empty portable/Solar creation and10,002IF Solar generation. Its directly saved pre-development state has10,000IF portable power,2IF in Solar, no category and no skills. It uses declared vanilla-tool, position, residency, instant-break, clock/daylight and separate seed0 Nether fixtures. It does not establish full ordinary unassisted survival.

An actual singleplayer GUI continuation using that directly earned inventory, explicitly bound to a separate QA identity, is the next client gate. No fresh GUI acquisition/skill effect or recording is claimed by this checkpoint.

Fresh pre-category defaults/cache timing, generic award auto-learning/experience events, existing level/skill pre-handler ordering, client event transport, full unassisted survival progression and remaining audiovisual/optional-integration fidelity remain separate work. These changes preserve the modern public codecs and command/input guards; they do not claim complete classic API compatibility or a finished full port.
