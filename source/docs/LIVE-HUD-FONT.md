# Live classic HUD font investigation and narrow alpha-test repair

## Findings

The initial absent-label report mixed intentional source behavior with a genuine render-fidelity defect.

- Canonical AcademyCraft 1.0.7 CPBar draws CP/OL values only when the activation key is held: 200 ms delay, 400 ms fade-in, alpha `0.6 × HUD alpha × numbers alpha`. A warning overload suppresses these numbers. Preset digits are transient after switching presets. CPBar does not draw a category-name text label.
- Canonical PresetEditUI deliberately sets an empty slot's text to the empty string. The XML contains no key-label widget. Production PresetEditScreen uses Minecraft font for populated skill names, its title/page tags, and selector hint. It does not use ClassicHudFont. Empty rows should remain blank.
- Visible R/F and `[V]: Abort Skill` in the supplied actual scatter recording already demonstrate that the font upload/render path works. The serial integration owner then directly held V and switched C: actual CP 2803/2803, OL 0/152 and classic four-preset boxes appeared. The held-number frame is `recordings/m12/verification/hud-v-held.png`. The disappearance report is not evidence of a global broken font.

The precise remaining defect is different: LambdaLib 1.2.3 TrueTypeFont.draw explicitly disables GL_ALPHA_TEST before drawing glyphs. Production non-monochrome ClassicHudFont used Minecraft's position_tex_color fragment shader, whose literal alpha `< 0.1` discard removes antialiased edge fragments and completely erases low-alpha portions of otherwise valid source fades. Source font drawing should retain those fragments.

This repair must not be described as fixing missing settled CP values or restoring source-invented editor/category labels.

## Narrow production delta

Only these four files are staged:

1. ClassicHudFont.java: choose the registered classic_font shader for ordinary colored text; mono still uses the existing classic_mono shader
2. ClassicHudShaders.java: add/reset/register its classic_font shader alongside the existing shaders
3. classic_font.json: existing Minecraft POSITION_TEX_COLOR vertex program and the source font fragment program, Sampler0, unchanged matrix/color uniforms
4. classic_font.fsh: sample texture × vertex color × ColorModulator, with no alpha discard and no grayscale operation

The original plain/bold rasterization, AWT font preference/fallback, width metrics, glyph margins/baseline, Unicode loop, ARGB-to-native conversion, DynamicTexture registration/upload, 33×33/5-level mip allocation, trilinear filtering, -0.65 LOD bias, global texture-owner keys, release lifecycle and Minecraft-font fallback remain byte-preserved. ClassicHudCanvas, ClassicAbilityHud, ClassicHudTimeline and PresetEditScreen are untouched. No media or font files are duplicated.

## Verification

Run `.staging/live-hud-font/scripts/verify-live-hud-font.sh` from any directory. It only writes this stage's .javac output. It starts no Minecraft process, Gradle, browser, window, desktop automation, or user-computer action.

- Cached JDK21 / Minecraft1.21.1 / NeoForge21.1.252 API compilation passed; two existing NeoForge mod-bus deprecation warnings
- 39,286 independent source raster/coverage/timing checks passed. Source oracle includes genuine antialiased edges, native alpha preservation, source-visible 0.06 fade versus vanilla cutoff, ordinary-number suppression, exact V delay/fade/release and transient C preset visibility
- 1,172 existing actual ClassicHudFont texture-ownership assertions passed using the staged class and fake manager
- Durable production Java binding test passed 595 current source/compiled-class/classpath-resource/witness checks and rejected 16 fail-closed mutants; exact noticed witnesses are on the test classpath, with no fallback legacy checkout path
- 11 standalone source checkpoint/route/descriptor/mutation-rejection tests passed; rejects vanilla-font route, discarded alpha, lost tint, absent registration and wrong sampler
- Actual staged GLSL150 fragment compiled, linked and rendered in an isolated surfaceless software EGL llvmpipe context. No UI or Minecraft was initialized. With source C glyph and exact modern mip allocation/filter: at alpha 0.06, vanilla rendered 0 pixels, corrected font rendered 91 pixels at 33×33 and 9 at source-scale-like 8×8. At settled alpha 0.6 vanilla still rendered 83/8 pixels, disproving total-settled-absence from cutoff alone. Source teal text tint remains colored
- Patch dry-run passed against exact current production baselines; no production changes were applied by this worker

The offscreen test is native shader/texture evidence, not actual Minecraft render parity. Actual m13 client screenshots must establish the new shader's registration and fade/edge appearance after serial integration. Runtime game verification belongs to the serial integration owner.

Logs: docs/verification.log and docs/offscreen-shader-verification.log

## Original-source recovery

source-checkpoints/original-text.json contains only small exact relevant excerpts from the authorized cached LambdaLib1.2.3 TrueTypeFont.scala and AcademyCraft1.0.7 CPBar.java / PresetEditUI.java, with original line ranges, full-file SHA-256 digests and copyright/license notices. The witness JSON itself is pinned by original-text.sha256. Source assertions read this bundle, never .reference. NOTICE and the full LambdaLib MIT license are included next to the witnesses; the root GPLv3 LICENSE and existing NOTICE remain applicable.

No build or regression script requires original checkout contents. Cached modern Minecraft/NeoForge build dependencies are required for API/offscreen checks. Pure checkpoint tests do not need those dependencies.

## Serial integration

1. Verify production-baseline-and-staged-hashes.json's baseline digests before applying docs/live-hud-font.patch with `patch -p1`. Absent resource files have null baselines
2. Apply the exact durable-font-binding-additions.patch (both Java tests, canonical noticed test resources and durability document), and wire both tasks from the supplied Gradle snippet into the parent's ordinary aggregate check; see DURABLE-FONT-BINDING-REGRESSION.md
3. Preserve source blank empty rows and CP/OL hold gating
4. Do the next normal serial build and fresh actual client launch. Verify shaders register without errors; compare antialiased edges and low-alpha CP/preset fades, and recheck settled V numbers, C strip, colored R/F, monochrome cooldown, GUI close/reopen and resource reload
5. Until step 4 succeeds, call this a source-backed, offscreen-verified font-fade fidelity repair, not live Minecraft visual parity

All exact target baseline/staged digests are in production-baseline-and-staged-hashes.json. A separate unchanged-source hash snapshot records observed HUD/editor/canvas/timing sources. The unified patch SHA-256 is recorded in live-hud-font.patch.sha256.
