# M25 source float progression checkpoint

Minecraft1.21.1 / NeoForge21.1.252 / Java21 development candidate, 2026-10-02.

This increment restores classic float arithmetic at resource and progression mutation boundaries. A skill can now pass the same affordability boundary as unchanged AcademyCraft1.0.7, and exactly earned `0.7f` Charging mastery satisfies the original prerequisites for Magnetic Movement and Thunder Bolt. Both learning entry points compare source float operands while retaining category, level, developer tier, learned-bit and captured configuration-graph gates.

CP, overload, trained capacities, raw mastery and level progress retain each original float intermediate. Recovery computes its original raw speed before calculation callbacks can mutate resources. Customized Radiation Intensity display mastery stays separate from its raw award slot. Public modern event request/configuration types and storage codecs remain unchanged. Invalid raw maxima/growth continue to fail closed; finite float overflow has an explicit bounded modern adaptation.

Verified on the actual current implementation:

- Complete `check jar`: 187 tasks, successful in1m43s. The final log is `runtime-evidence/m25-source-float-prerequisite-final-accepted-check.log`
-354 native world tests passed after the universal prerequisite fix. The three developer ingress tests exercise every advertised skill with exact source-float prerequisites, retained unlearned raw XP rejection, previous representable float rejection, original tier limits and conserved finite power
-1,862,070 bit comparisons against unchanged original CPData/AbilityData arithmetic;40,265 invalid-capacity checks
-509 prerequisite/learned-bit comparisons with zero differences, dynamically compiling44 hashed original/platform/harness fixtures and loading the actual modern classes
- Six native classic command/death/nondeath-clone tests and three actual configuration loading/reloading tests passed
- Two distinct Minecraft JVMs passed native player/Anvil reload, cooldown disk exclusion, graceful escrow refund, bounded tick evolution, Solar/device power and cold Fusor conservation
- Existing learning/resource test migrations preserve the old negative behavior assertions. Inputs now use the previous representable float; double-only neighbors that round to the exact source threshold are separately checked as positive controls. Original witnesses and tolerances were not loosened
- Final JAR ZIP CRC, QA/test-resource exclusion and the three changed production bytecode entries matched the actual built classes

These checks establish the stated source arithmetic and admission behavior. They do not establish full original API, visual or audio equivalence.

The generated-material chain is still incomplete. In an owned copy of the closed M24 world, native survival destruction and actual item pickup harvested69 witnessed Overworld blocks and conserved all required Overworld raw materials. Supplied vanilla tools, positions, invulnerability, chunk residency and instant destruction are declared fixtures. Native GameTest seed0 Nether generation is separate from the original seed1 Overworld; the original save contains no Nether region files.

The first bounded Nether run found and destroyed an actual generated glowstone block and saved four real dust items, but its immediate visible-entity query failed. A second fresh copy observed native entity residency for64 additional real ticks and stopped without destroying glowstone when promotion did not finish. Neither run reached furnace processing, crafting, device charging or ability acquisition. Nine refers to the scanned/explicitly forced Nether chunks; native ticket dependencies generate additional surroundings. Further diagnosis and a new guarded input copy are required. Planned50 crafts,37 smelts and Solar charge are not passed runtime results.

The ordinary unassisted M24 singleplayer attempt remains a partial result: one oak log, one sapling and the legitimate first tutorial, followed by natural zombie death and ordinary respawn. No tool or device was completed in that run. No material or ability grants were used there.

Remaining work includes source category acquisition/reset event ordering and activation, fresh pre-category maxima/cache lifecycle, broader source award API semantics, the complete ordinary survival progression route, remaining audiovisual comparisons and genuine optional integration dependencies. The35 active paths,15 passive instances,56 achievements and14 guide entries are implementation coverage. They are not a declaration that the complete classic port is finished. Current cloud client captures are silent because no usable OpenAL device is available.

The prior physical/client visual acceptance belongs to its recorded milestones. The M25 changes above have native/code acceptance; an updated actual singleplayer client workflow and fresh screenshots remain pending.
