# M31 far-coordinate Arc and railgun rendering

Development checkpoint for Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21. Full faithful port acceptance remains open.

The Arc/railgun renderer previously narrowed global coordinates to float and then subtracted the camera through a float matrix. At the actual QA camera near X14,502,577.5/Z4,277,858.5, a 0.01-block offset became zero. The real cached Minecraft PoseStack and VertexConsumer reproduce the cancellation. Classic EntityArc instead renders local geometry after its entity-relative translation.

Arc origins, railgun sparks, glow boards, cylinder rings and third-person charge centers are now camera-relative in double precision before narrowing. Geometry generation, textures, colors, timing, buffer ownership and first-person hand rendering are unchanged. The other effect renderers and original random-geometry equivalence still need separate comparison.

A regression invokes the actual private Arc renderer and official Minecraft vertex transformation with the same declared deterministic full branch geometry at near, fourteen-million and positive/negative world-border cameras. The old renderer differs in 1,397 of 2,640 coordinates, with maximum drift about 1.87 blocks. The candidate has zero differences above 0.00001 block; maximum drift is about 0.00000006 block. This CPU test does not establish GPU or complete visual fidelity.

Full check/JAR passes. Compared to the fully 366-native-tested M29 JAR, only the client-only ClassicEffects class family changes; every other production class and resource is byte-identical.

Actual fresh client testing loads the existing earned M27 world, preserving Electromaster level1, the root skill, preset and portable developer3760IF. Two real held inputs hit stone and produce blue-white curved ribbons in unedited recordings. After normal save/quit, raw XP is exactly0.005404861643910408, extra CP0.2255401611328125 and extra overload0.31298068165779114. Independent source float arithmetic agrees; inventory is unchanged. A very short V input is missed by the physical20Hz poll; explicit120ms short input restores activation under the original<300ms rule.

The prior supplied tools, pose, residency, accelerated native clocks and material-fixture provenance prevent describing this as complete unassisted survival. No new skill/material grants or synthetic effect messages are used in this client continuation. Clips are silent because the cloud OpenAL device is unavailable. Full original geometry, actions, sounds and all world-renderer comparisons remain open. M30 client transport is not included in this checkpoint.
