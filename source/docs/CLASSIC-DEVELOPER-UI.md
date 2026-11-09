# AcademyCraft1.0.7 developer UI, NeoForge1.21.1

## Integration

This worker changed only `.staging/classic-developer-ui`. No production, build/classes, Gradle, live game, CUA, user's computer, external push or publication was operated.

1. Copy `src/main/java/cn/academy/port/client/ClassicDeveloper*.java` and staged `src/main/resources` to the corresponding production paths. Source assets retain exact original paths; identical existing assets can remain untouched. Historical original `.lang`/XML/shader files are provenance resources; modern runtime uses existing JSON localization and the two new core shader JSON/FSH files.
2. Review/apply `docs/classic-developer-ui-integration.patch`. It replaces only the temporary `DeveloperScreen` implementation with a stable subclass entry point and adds three explicit missing/unsupported/error status keys per existing language. The full six-line entry point is also in `integration/cn/academy/port/client/DeveloperScreen.java`. Merge `integration/resources/assets/academy/lang/*.additions.json` into matching existing language JSON if the diff context has changed; do not copy these as replacement locale files.
3. Machine integration must already supply `AcademyClient`'s `developer_machine`, `developer_machine_update`, `developer_machine_close` packet handlers and authoritative nonce sessions. Public constructors, `machineSession`, `machineUpdate` and once-only `removed` machine_close behavior preserve that exact contract. No controller/network changes are needed here. Existing portable `new DeveloperScreen()` remains unchanged.
4. This UI reuses package-local `ClassicHudCanvas`, `ClassicHudFont`, `ClassicHudTimeline.Rgba`, `ClassicHudShaders.mono`, `ClassicHudConfig`. Do not duplicate them. New `ClassicDeveloperShaders` discovers/registers its GLSL150 programs on the existing client-only mod event bus. `ClassicDeveloperTextures` owns six trilinear original textures through the normal reloadable TextureManager.
5. Add a JavaExec/check task for `cn.academy.port.client.ClassicDeveloperUiRegressionTest` and optional Python source-check/unit-test tasks if desired. Tests need the production classic skill catalog plus the staged layout/assets resource classpath. `scripts/verify-classic-developer-ui.sh` is the cached JDK/API-only verification route and writes only this stage's `.javac` output.

## Implemented original visuals and interactions

- Exact centered400×187 source XML panel geometry. Left108.5×187 at x4; right278×187 at x118; tree area257×139 at x128,y18. Actual original parent/UI/machine/circuit backgrounds and ability/node/skill/requirement/button assets, not a modern grid or vanilla buttons
- All50 canonical category-local nodes, including source generic identities. Exact guiX/guiY, parent-only textured edges,16px hit targets,23px skill backs,31px outlines,14px icons and12.2px edge endpoint inset
- Original potential-learning visibility; machine tier does not hide nodes. Learned/parent-ready/parent-locked alpha1/.7/.25.100ms hover to1.2×,10px full-window mouse parallax,1.01× circuit UV parallax. Source stagger100ms+80ms/node with background/icon/line/radial reveal rates
- Source trilinear skill/ring/mask/line texture filtering. Unlearned icon grayscale follows LambdaLib arithmetic; modern mask shader approximates the original back alpha-test stencil. Learned EXP ring and larger development/success-glow icon use the actual source mask and strict `progress > maskRed` comparison
- Exact source left ability icon/name, level EXP/progress, level description/upgrade graphic, power and sync-rate placement. Real finite IF/capacity and tier syncRate, with unobtrusive hover numeric details. Portable hides the wireless fields as source does
- Source black cover200ms fade/.7 darkness;50px action icon, original title/font-size/alignment, learned EXP and original descriptions. Unlearned popups show LV, red state, canonical ordered requirement icons, failed monochrome and hovered red detail, source energy prompt and original32×16 confirmation graphic. No learn button exists for an unimplemented skill
- Original dependency icons are portable item or normal/advanced block artwork, then parent/extra skill icons and any-level condition. Player-level requirement is hidden in the icon row, as source. Descriptions appear only on learned popups
- Source OS terminal acquisition (`learn`) and held-magnetic-coil reset (`reset`) modes. Original localized boot text,20ms typewriter,400ms pause, seven random300ms numeric boot frames, failed boot/startup/override instructions,10line retention, blinking prompt, editable command input/backspace/Enter, live authoritative progress and success/failure,500ms rebuild delay
- Level-up and skill-learning confirmation uses server actions only. Source estimated CPS×stimulations and exact tier/prerequisite checks remain; actual source TPS+1 energy is still drained by existing server process. The popup button disposes after the first attempt, matching source
- Source Escape closes a nested wireless page first. Escape elsewhere closes the whole developer, including skill/level covers; closing never sends abort. Machine removal sends once-only nonce-protected close, freeing occupancy while source development continues. No artificial player pose/seating, energy refill, inventory shortcut or executable unported ability was added
- A successful source skill/level cover remains for acknowledgement, then rebuilds the tree from the current authoritative state. Console completions rebuild after the source500ms pause. Repeated/active commands cannot duplicate client start requests

## Explicit adaptations and deferred behavior

- GUI-scaled windows narrower than400×187 uniformly fit the original panels with a6px margin. Normal-size windows retain source1:1 layout
- Core-profile shaders replace1.7.10 depth/alpha-test fixed-function operations. The icon uses source back-mask threshold rather than depth equality; parent lines render behind node artwork. Modern clamp-to-edge replaces legacy GL_CLAMP. These paths need actual client pixel/GL QA; pixel parity is not claimed
- AWT font/glyph rasterization is shared with the existing classic HUD: source Microsoft YaHei preference and CJK fallback sequence, no proprietary bundled font. Missing OS families use the same existing system fallback, so appearance depends on installed fonts. LambdaLib word/CJK/punctuation wrapping and exact line spacing are preserved
- Existing modern server `progress` is normalized/monotonic. Original `source_progress`'s missing per-stimulation divisor is retained server-side but intentionally not used as a misleading UI completion percentage/ring
- Command input is accepted after boot and while idle, rather than queueing an operation during the source boot animation. A second development cannot be started while a current operation is developing. A start with no server confirmation after3seconds reports an unconfirmed-start state, permits closing, and never fabricates success, category changes or refunds
- Original config flags are extracted with shipped enabled=true defaults. No new runtime per-skill enable/disable config schema is introduced in this task
- Wireless N/A uses original current-node fields and a dim original node icon. Clicking opens a cover that explicitly says real wireless nodes/network are absent; Escape/backdrop returns. No fake SSID, battery node, link button, mutable network graph, or dependency shell exists. A faithful functional WirelessPage requires the actual wireless network/UI and remains deferred
- Unported source skills can be inspected but cannot be learned or cast. Some implemented later skills, notably Railgun, require currently unimplemented dependencies in the untouched source catalog and remain naturally unreachable. This is surfaced accurately rather than silently weakening prerequisites
- The native source two-slot machine inventory exists in the separate machine task. Original DeveloperUI has no standalone inventory screen here, and none was invented

## Verification and live QA boundary

Last cached API/headless check:

- All new client classes and narrow entry point compile against cached JDK21/Minecraft1.21.1/NeoForge21.1.252; only the existing mod-bus annotation deprecation warns
- ClassicDeveloperUiRegressionTest:620assertions pass (layout/fit/inversion, reveal/hover/cover/parallax/link arithmetic, source50node/catalog/resource identity, prerequisite icons, terminal timing/editing/retention, source text wrapping)
- Independent source extractor:50canonical skills and77original assets byte-for-byte verified
- Nine independent source extraction/visibility/requirements/provenance tests pass

Headless checks do not prove GLSL compiles in the current graphics driver, real GUI mouse/keyboard behavior, wire acknowledgement, text glyph GPU uploads, trilinear resource reload, or server lifecycle. Parent must integrate and run aggregate/native checks and serial live QA:

1. Uninitialized portable: original boot animation; type invalid command then learn, actual finite IF consumption, DONE category rebuild; low battery failure
2. Each category: reveal/parallax/hover; source node graph and no executable unimplemented ability; dependency hover; learned description/EXP ring; early root brain_course remains visible
3. Portable L1–2 vs normal L3 vs advanced L4–5 requirement icons; source live IF/sync bars and insufficient actual energy failure
4. Skill/level covers: accept once, repeated click cannot duplicate, close backdrop after terminal state, Escape closes whole screen and leaves development running; reopen during pending process
5. Normal/advanced machine nonce context, reach expiry/break/unload close, replacement/new screen stale-close protection; live machine IF updates
6. Advanced reset with coil/different factor/level≥3: source override console, reset, source consumption/loss of level and full slots; reject wrong tier/missing factor/low level; terminal500ms rebuild
7. Disabled/missing wireless N/A cover and nested Escape return; next Escape releases machine screen
8. Narrow and ordinary GUI scales, zh_CN/en_US font rendering, F3+T reload while open, close/reopen without font/texture leaks, HUD still renders correctly afterward

No Minecraft/game/Gradle/CUA run was performed by this worker. The handoff is implemented and API/headless verified, awaiting integration and live client QA rather than claimed finished visual/gameplay parity.
