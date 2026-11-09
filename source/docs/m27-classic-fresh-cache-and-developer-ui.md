# M27 classic fresh resource/cache and developer display checkpoint

Minecraft1.21.1, NeoForge21.1.252, Java21; private development candidate. Faithful full port acceptance remains incomplete.

## Resource behavior

Fresh CPData now reaches actual native storage with CP0 and raw CP/overload100, matching AcademyCraft1.0.7 field initialization. DataPart.wake binds the data configuration without recalculation; getters, storage snapshots and absent-category ticks retain cached raw maxima. Only existing explicit category/skill/level handlers calculate maxima and refill. Saved raw maxima remain exact; older or malformed port raw maxima use an explicit bounded migration without current-resource refill. Schema3 and disk/live cooldown distinction remain intact.

The captured data subtree supplies initial CP/O vectors, recovery, delays and training. Current skill/global progression settings retain their live supplier. Cold restoration captures new data settings and restores valid saved maxima; live player clone preserves the original captured snapshot. M26 category event priority/context-only disposal/completing developer controller and menu session behavior is preserved.

Original references: CPData field initialization67–109, wake116–118, tick127–177, getters219–256, recovery353–365, recalc413–425, event handlers545–565; LambdaLib1.2.3 EntityData construction166–181 and live clone243–247; ACConfig replacement60–66. Original source files are unchanged. Platform hosts support differential tests; an original1.7.10 runtime was not launched.

## Developer display

Every displayed learned-skill prerequisite now uses original float comparison, including true .7f boundaries accepted by the server. Successful development suppresses the contradictory red unlearned label. That suppression is an intentional small clarity improvement over a quirk present in original1.0.7, rather than a fidelity defect. Original frozen learned/title branch, requirements, glow, progress and dismissal/rebuild timing remain.

## Verified boundaries

Full serial check/jar/native suite passed:358 required native tests, including actual fresh storage/binding/no-category ticks/save/clone and authenticated acquisition without an invented level-change event. Build193tasks,3m19s. Original differential1566checks zero differences, bit-exact numeric oracle1862070checks, invalid capacity/session40265checks, native-NBT fresh/cache/cold/clone/migration28checks. Focused actual-screen recording-host checks1762 and unchanged UI769passed; GPU acceptance of the new display change is separately pending.

The real M26 authentic-material fixture continued through ordinary GUI random Teleporter/root learning, preset binding and first real item transfer, then separate-client-JVM cold restore with exact progress/inventory equality. It remains a fixture-assisted authentic acquisition chain. Supplied vanilla tools/poses, explicit residency, instant breaking, accelerated native clocks and separate generated Nether prevent describing it as complete unassisted survival.

Additional6command and3configuration native tests passed. A distinct-JVM disk probe initially exposed an invalid old test seed (CP321.25 above cached raw100+growth125.75). Only the fixture now explicitly seeds independently known level4/BrainCourse raw6800/overload350 and asserts those exact values. Both new seed/verify JVMs pass; all66failed-run files remain unchanged. Production decode safety was not altered. Fresh actual singleplayer GUI acceptance is recorded separately as it finishes. Existing source skill/level event pre-handler order, generic XP auto-learning/events, client event transport, broader worldgen/natural acquisition and audiovisual/optional integration fidelity remain open;35active paths and content registration counts are not full completion.
