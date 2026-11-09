# Classic portable developer hardware and finite IF display

## Integration

All worker changes are isolated in `.staging/portable-visual`. Production, build/classes, Gradle, client/server launches and computer UI were not operated.

1. Copy staged new `src/main/java` classes and `src/main/resources` into matching production paths. Existing `ClassicDeveloperObj` is reused; do not duplicate the OBJ parser. The only new resources are the actual AcademyCraft1.0.7 `developer_portable.obj` and `textures/models/developer_portable.png`.
2. Review/apply `docs/portable-visual-integration.patch`, which only adds read-only energy gauge/tooltip overrides to the current DeveloperItem. `integration/cn/academy/port/DeveloperItem.java` is a compile/reference snapshot. Preserve any newer use/session code; the narrow additions do not alter right-click behavior.
3. `ClassicPortableRendering` is its own client-only mod-bus subscriber. It additively wraps only the portable inventory model, registers its item client extension and resource reload listener. No AcademyCraft or AcademyClient registration method is replaced, and all current solar/generator/energy/machine registrations stay intact.
4. No model JSON or current charge-icon property is replaced. Existing empty/half/full generated-item models and the recently corrected Math.round charge-icon mapping remain authoritative in GUI. New classes wrap every resolved charge variant, so overriding to half/full cannot lose the physical OBJ route.
5. Copy the two tests and add JavaExec/check tasks for `cn.academy.port.client.ClassicPortableVisualRegressionTest` and `cn.academy.port.develop.ClassicPortableDisplayRegressionTest` if required by the parent aggregate workflow. Do not overwrite an existing build.gradle/tasks list; only add these two new tasks. `scripts/verify-portable-visual.sh` is the cached JDK21/API/headless route and writes only stage `.javac` files.

## Canonical source behavior reproduced

Canonical sources: AcademyCraft1.0.7 `ability/item/ItemDeveloper.java`, `ability/client/render/RenderDeveloperPortable.java`, `energy/template/ItemEnergyBase.java`, `energy/api/IFItemManager.java`; LambdaLib1.2.3 `template/client/render/item/RenderModelItem.java`, `util/deprecated/ItemModelCustom.java`.

- Portable source renderer disables inventory rendering, using2D item icons there, and handles equipped/first-person and entity contexts with the actual portable OBJ and model texture
- Source model:32OBJ positions,90UVs,32declared normals,60triangular faces. Existing parser preserves source Wavefront V flip, per-face flat geometric normals and .0005UV inset. Modern entity buffer emits each source triangle as(a,b,c,c), preserving geometry and native packed lighting/overlay
- Source standard transform: scale6, zero standard offset/rotation, scale(-1,-1,1), rotateY180, then ItemModelCustom scale1/16. Net size scale is .375, rather than inventing a new portable model or using the item sprite as a mesh
- Equipped local call order: Rz40; third-person-only T(.1,.05,.2); T(.6,0,-.2); Ry(-10),Rz(-5),Rx0; Ry(-90); equip scale1; third-person-only scale.6; then standard transform
- Entity local order: Rx15; T(0,0,.2); entity scale1; then standard transform. Native outer bob/spin remain enabled as source shouldUseRenderHelper requests. Physical identity metadata prevents generated sprite's ground .5 scaling metadata from changing the OBJ helper height
- Source RenderModelItem enables source-alpha blending and disables face culling around geometry. Modern renderer uses entityTranslucent with no culling, preserving actual source texture alpha and full world light/overlay
- The portable's model texture includes the original static painted screen graphic. It is not a dynamic screen or a second live developer GUI; static artwork is exactly what the original renderer displays
- GUI context returns the original selected generated icon and its original GUI transform. Non-GUI selects an immutable custom-renderer model. No last-rendered-context flag mutates a shared model; interleaving GUI, held and dropped rendering cannot contaminate later icons
- Charge variant wrapper ownership is per resource bake, keyed by original baked-model identity. Reload creates new source wrapper ownership and atomically replaces the immutable portable mesh. A missing/bad reloaded OBJ logs the actual resource error and discards stale geometry rather than retaining old pack data
- Inherited ItemEnergyBase description is restored as `%.0f/%.0f IF`, reading the existing stack-owned DeveloperItemEnergy payload. Capacity remains10000IF and charging bandwidth/capability semantics are unchanged
- Inherited rounded13-step damage-equivalent gauge is restored as a read-only modern bar: damage=round((1-IF/10000)*13), width=13-damage, full gauge hidden, red→green HSV. It does not introduce real item durability, modify DAMAGE, rewrite custom payloads, or make the developer breakable
- Corrupt/nonfinite/negative/over-capacity values use existing DeveloperEnergy sanitization. No implicit charge, creative override, source IF key migration, storage/capability replacement or solar-specific shortcut is added

## Modern context adaptations and honest limits

- Minecraft1.21.1 supplies modern outer hand pose, camera, third-person attachment, dropped spin/bob, frame/head attachment and packed lighting. Only the canonical source-local transform is reproduced; different surrounding1.7.10/1.21.1 camera/hand pipelines prevent a pixel-parity claim without live comparison
- Modern ItemRenderer always subtracts(.5,.5,.5) after applyTransform. The renderer cancels that convention once with T(.5,.5,.5) so source centered OBJ coordinates are relative to the callback origin. This follows inspected cached native API bytecode
- Original1.7.10 has no offhand. Modern left-hand equipped contexts mirror the source-local matrix acrossX; right-hand source transforms remain unchanged. FIXED/HEAD/NONE use source standard geometry without invented custom placement. These contexts need parent live QA
- Modern translucent buffered lighting/sorting replaces old immediate fixed-function OpenGL. CPU tests establish exact mesh/matrix/attributes, not driver shader/alpha/hand framing behavior
- Existing current item properties and GUI models were not modified. Shared HUD/font textures, source GUI and prior font-ownership fix are also untouched

## Verification

Latest `verification.log`:

- All new classes and narrow DeveloperItem snapshot compile against cached JDK21/Minecraft1.21.1/NeoForge21.1.252; client event-bus annotation deprecation only
- ClassicPortableVisualRegressionTest:22,028assertions pass
  - Actual original OBJ identity and topology
  - Independent double-precision canonical vector-transform oracle against actual native PoseStack and vertex emission in all four contexts and both handedness states
  - Exact source UVs, white tint, native packed light/overlay, unit normals and triangle→quad duplication
  - Repeated GUI/physical routing, charge overrides for all three source icon bands, wrapper ownership per original model, new-bake ownership and physical helper metadata
- ClassicPortableDisplayRegressionTest:60,038assertions pass across fractional/negative/full/over-capacity IF, every13-step rounding midpoint, tooltip and source icon/gauge parity, nonfinite sanitization and HSV endpoints
- Original OBJ and PNG byte-for-byte match canonical source, hashes in `docs/source-assets.json`
- Resource/integration check confirms unchanged empty/half/full inventory JSON, current Math.round property, existing GUI/session use code and read-only/additive changes

The tests call the actual mesh emission helper used by the renderer. They do not instantiate BEWLR/Minecraft/GL; its vanilla superclass requires game registry bootstrap, so such instantiation is deliberately excluded from this headless route. Cached API compilation covers that adapter, while live rendering remains parent's verification.

## Required serial live QA

1. Empty/half/full portable inventory and hotbar icons, finite IF tooltip and rounded bar; confirm current source thresholds near2500/13-derived rounding transitions
2. First-person right hand: visible full original device/body/screen/edges with normal swing and charge bands; confirm it is3D instead of a flat sprite
3. Third-person right hand and modern left-hand contexts: orientation/placement, lighting and mirrored source-local transforms; no unintended giant/clipped device
4. Drop portable: actual3D hardware, native spin/bob, correct ground height, intact pickup energy and inventory2D icon restored
5. Interleave GUI/held/dropped contexts and alternate charge variants; no model-context or texture ownership leak
6. F3+T/resource-pack reload with portable held/dropped, bad/missing model diagnostic and restored pack; fresh mesh/charge icons, no stale geometry or shared font deletion
7. Existing solar→finite charge→earned developer GUI workflow and machine GUI occupancy still behave identically; existing capability and server payload tests stay green

No live visual success is claimed by this worker until the parent verifies those contexts in its existing client.
