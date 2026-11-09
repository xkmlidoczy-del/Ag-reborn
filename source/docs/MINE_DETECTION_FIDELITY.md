# Mine Detection: AcademyCraft 1.0.7 → NeoForge 21.1.252

This is an isolated additive source implementation for Minecraft 1.21.1. It has been compiled against the cached current APIs and checked without starting Minecraft. Runtime, audible and GPU/pixel parity are not established by these tests. No client, world or saves were opened by this worker.

## Canonical sources and supported stock scope

- AcademyCraft `.reference/AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/electromaster/skill/MineDetect.scala`: level3 keydown, costs, blindness, mastery, effect handler and renderer
- AcademyCraft `src/main/java/cn/academy/vanilla/electromaster/CatElectromaster.java`: `mineDetect.setParent(magManip,1f)` and `isOreBlock` native BlockOre/BlockRedstoneOre + block-item OreDictionary name containing lowercase `ore`
- AcademyCraft `AbilityContext.java`, `AbilityData.java`, `Skill.java`, `ACSounds.java`: stock configuration defaults, Float mastery cap/add, optional achievement and following one-shot sound
- LambdaLib1.2.3 `WorldUtils.java`, `MathUtils.java`, `EntityAdvanced.java`, `MeshUtils.java`, `Mesh.java`, `SimpleMaterial.java`, `Color.java`: scan traversal, strict refresh/death comparisons, float operations, exact cube/UV order, fullbright color and GL states
- Primary current API evidence is cached `build/moddev/artifacts/neoforge-21.1.252-sources.jar`, the matching Minecraft resource JAR and NeoForge21.1.252 source JAR. No third-party API guesses are used

The source stock `mine_detect {}` configuration has no cost/XP overrides, so its three effective multipliers are1.0. This implementation ports that stock skill. The old cross-skill configuration lookup bugs and configured global progression/CP scaling are not newly implemented by this slice; this is not a claim of parity for custom legacy skill configuration.

## Server gameplay and authentication

`MineDetectRules` uses Float `a+t*(b-a)` expressions, not the shared double lerp. CP is1500→1000 and overload200→180 from execution-time Float mastery. Range15→30 and advanced (`exp>.5f && level>=4`) are captured before the award. The authoritative owned current preset slot starts a one-shot cast; arbitrary wire skill-name casts remain forbidden. Activation, category, learned state, level, cooldown, interference, overload state, alive/non-spectator and main-server-thread checks are enforced.

Success preserves stock sequence: consume; add real native BlindnessI100ticks; Float `added=min(1f-old,.008f)` mastery addition; modern achievement grant; send only caster/range/advanced to the casting player; then read post-award mastery and install truncating Float900→400cooldown. A novice cast therefore receives896ticks, rather than the pre-award900. Reentry is reserved until this transaction ends, including synchronous blindness hooks. Failed costs/gates do not award blindness, mastery, effect or cooldown. Presets are not automatically bound.

The source existing catalog and exact magnetic-manipulation mastery1 requirement are reused. The legacy achievement trigger becomes an impossible native advancement criterion awarded only by an accepted cast, with original title/description translations. Its iron-ore icon and rootless native advancement layout are a documented modern presentation adaptation; the original achievement page/icon tree is not claimed to be ported by this slice.

## Discovery and source handler quirks

`ClassicMineScan` retains inclusive floor(center−range)..ceil(center+range) bounds, ascending X outer/Y middle/Z inner order, ore predicate before integer-block-origin distance²≤range², and the first1000 matches. It never sorts by distance. The complete coordinate scan precedes harvest capture, and novice mode never queries harvest. Effective radius ismin(captured range,28).

On the first tick, it scans the spawn position before following the current player's feet. Sufficient movement can therefore cause two scans on that first tick. Each refresh appends without clear/deduplication, even duplicate or already-mined records. Captured harvest/color remains unchanged. Refresh uses strict distance²>range*.2*range*.2, preserving left-associated Double operations. Expiry is strict tick age>100 and follows the final movement/refresh, matching the source conditional. The first hundred client ticks stay alive; tick101can scan and then die.

Modern local-world safety differences are explicit:

- Only chunks genuinely present in `level.getChunkSource().hasChunk(x>>4,z>>4)` are examined; ClientLevel.hasChunk/hasChunkAt alone is not a valid loaded check in cached1.21.1
- Modern build-height bounds are skipped instead of probing invalid Y coordinates; no chunks are requested/generated or server ore positions sent
- A per-access identity memo caches pure block/tag and metadata0-equivalent harvest classification during a synchronous client tick. Coordinate order and selection are unchanged; caches are recreated before the next tick so datapack/config changes cannot remain stale
- Level/player/connection changes clear handlers and terminate their owned sounds. Invalid/non-finite packets are rejected. At most four handlers/sound handles are retained; the natural≥400tick cooldown means stock gameplay has only one active100tick handler. Repeated/admin-reset effects beyond four evict oldest effects. This is a bounded safety adaptation, not source unlimited handler parity
- Stored records inside a valid handler are never truncated/deduplicated. Render submissions flush a private buffer each1000boxes while retaining all source insertion/face ordering
- Invalid coordinates and alpha inputs are bounded; invalid lower harvest levels below−1 are not accepted by the compatibility override config. Source negative array-index failures are not reproduced

## Native ore/harvest migration rationale

The old `BlockOre` corresponds to current vanilla `DropExperienceBlock` ore instances, confirmed in cached1.21.1 `Blocks.java` for coal/iron/gold/lapis/diamond/emerald/quartz and modern copper/deepslate/nether-gold counterparts. Native `RedStoneOreBlock` maps the separate old redstone class. Current `SculkBlock` also inherits DropExperienceBlock but is a new non-ore family; it is explicitly excluded from the class shortcut. Semantic ore tags or the explicit config may opt such a block in if a mod intentionally treats it as ore.

OreDictionary entries have no direct1.21.1 registry. Automatic compatibility therefore uses block OR block-item semantic tag paths: `ores`, `ores/...`, `ores_in_ground`, `ores_in_ground/...`, or `*_ores`, including standard `c:ores` descendants and vanilla `minecraft:coal_ores` families. The block item must exist, as in the old dictionary fallback. There is deliberately no registry-name substring guess and no arbitrary ore ID list. Modern `stone_ore_replaceables`, `deepslate_ore_replaceables`, biome and storage-block tags are worldgen/material groupings, not ore-dictionary entries, and do not make ordinary stone or iron blocks detectable.

Legacy mod dictionaries with unconventional names containing `ore` cannot be reconstructed from missing dictionary metadata. `academy-mine-detection-server.toml` provides an explicit `additionalOreBlocks` registry/#block-tag bridge rather than pretending all modern tags containing `ore` mean OreDictionary ore identity. That semantic narrowing is a documented migration boundary.

Metadata0 harvest levels map current `needs_stone_tool`→1, `needs_iron_tool`→2, `needs_diamond_tool`→3; otherwise native correct-tool-required→0 and hand-harvestable→−1. Configured registry/#tag `=level` overrides (−1..3, first match wins) cover modded legacy metadata0/tier compatibility. No attempt is made to infer arbitrary custom modern tool tiers. Advanced source index ismin(3,harvest+1); normal harvest−1→default blue,0→gray,1→cyan,≥2→green. The source's fifth orange-red palette entry exists but is normally unreachable because of this cap, and is not artificially activated.

## Actual textured through-wall rendering

`ClassicMineDetectEffects` emits all six original source box faces using `mineview.png`, with Double camera-relative translation and block-local[.05,.95]extent. Every face maps the full(0,0),(1,0),(1,1),(0,1)UV rectangle. Face order and source winding are retained; culling remains disabled. Modern QUADS use the same0–2 triangle diagonal as source Mesh.setQuads. Source accumulation/face insertion order is unsorted.

RGB values are exactly(115,200,227),(161,181,188),(87,231,248),(97,204,94),(235,109,84). Alpha is exactly `.3f + (float)((1-sqrt(dx²+dy²+dz²)/range*2.2)*.7)`, measured from current handler feet to integer block origin, without interpolation or time fade. Only normalized U8 vertex transport clamps/quantizes it after the source calculation. Source zero/negative-alpha records still pass through the draw loop.

`ClassicMineRenderType` uses the native position_tex_color shader, which contains no fog or lighting inputs, with no lightmap/overlay, no culling and legacy SRC_ALPHA/ONE_MINUS_SRC_ALPHA blending for both RGB and alpha. Its explicit depth-state override calls RenderSystem.disableDepthTest: cached Minecraft NO_DEPTH_TEST is a no-op and would not by itself implement through-wall rendering. COLOR_WRITE avoids any depth writes, equivalent to disabled OpenGL depth-test behavior. Source forced state leakage is replaced by scoped native restoration.

The stock shader hard-discards combined textureAlpha*vertexAlpha<.1. The old skill itself never specifies an alpha-test threshold; its inherited legacy alpha-test state has not been established in this task. This cutoff and U8 quantization are disclosed rendering compatibility boundaries, not pixel-parity claims. Actual Fast/Fancy/Fabulous, blindness, wall, distance-alpha, duplicate buildup and camera-view behavior need native client capture.

AFTER_LEVEL is used after modern transparency composition, before hand/HUD. The event's actual camera model-view matrix is explicitly scoped/restored because its PoseStack is newly identity after LevelRenderer pops its model-view stack. MAIN_TARGET avoids Fabulous particle composition depth reintroducing occlusion. This modern stage may differ in weather/transparency overlap from the original entity pass1; visual parity for those overlaps is unverified.

The source following non-looping feet-position `em.minedetect` sound at volume.5 is implemented with a native tickable following instance. Four handles are retained for session cleanup; natural SoundEngine completion does not set isStopped. Texture and audio byte hashes are checked against the retained originals. No replacement texture, generic particle or line-only placeholder is used.

## Checked and unchecked

Checked: cached Java21/NeoForge21.1.252 compilation of additions and patch-applied integration copies; independent differential numeric/scan/handler/alpha/UV tests; native class cold linkage with client namespaces denied; canonical original asset hashes; original translations and advancement JSON structure; existing preset regression after narrow fixture migration; additive build/constructor preservation and patch hashes.

Five native fixtures compile but have not run: real blindness/cost/cooldown/replay, pre-award advanced packet shape, owned preset ingress/source learning dependencies, native ore classes/tags/harvest/occluded scan, and synchronous blindness-hook reentry/poor CP rejection.

Unchecked: GameTest runtime, full Gradle aggregate, actual client GPU/audible capture, external mod/custom tool-tier interoperability, modern advancement codec/runtime and original achievement-page presentation, legacy inherited alpha-test threshold and direct pixel comparison. Integration and those runtime checks belong to the parent task.
