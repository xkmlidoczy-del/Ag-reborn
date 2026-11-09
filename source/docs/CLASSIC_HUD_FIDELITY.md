# Classic ability HUD reconstruction

## Provenance and scope

This port is grounded in canonical AcademyCraft 1.0.7 `CPBar.java`, `KeyHintUI.java`, `BackgroundMask.java`, `ClientHandler.java`, `ClientRuntime.java`, `KeyDelegate.java`, `Skill.java`, and the two `cpbar_*.frag` files; plus LambdaLib 1.2.3 `CGui.java`, `HudUtils.java`, `CubicCurve.java`, `TrueTypeFont.scala`, and `mono.frag`. AcademyCraft source/assets remain GPLv3; LambdaLib math/font/mono adaptations are MIT. Existing NOTICE and source license statements remain applicable. No proprietary font is bundled.

This replaces the disclosed generic cyan/orange rectangle HUD with the source's actual layered textures and widget geometry. It does not restore every AcademyCraft GUI or imply that all catalog skills have runtime implementations.

## Reconstructed CP/overload widget

- 964×147 authored units, scale 0.2, right aligned, offset (-12,12). Origin uses `(GUI width - 964×0.2 - 12, 12)` in Minecraft GUI coordinates, without multiplying the offset by widget scale or framebuffer scale again. This follows CGui's root alignment equation.
- Category required, activation-triggered 200ms fade-in and 200ms fade-out. Hidden GUI suppresses rendering; ordinary menus retain HUD behavior. Pause-aware animation clock continues to be sampled by client ticks so opening a paused menu does not add its elapsed wall time to the animation.
- Separate buffered CP and overload fill, source 2.0 units/second with delta capped at 100ms, preserving the original inactive last-draw-time handling.
- Actual `back_normal`, `back_overload`, `cp`, `front_overload`, `mask`, and `highlight_overload` PNGs. `overloaded.png` is declared but never drawn by canonical CPBar, so no invented warning label was added.
- Normal overload tint alpha/color knots 0→0x0Adfdfdf, .55→0x23f0d49d, 1→0x50f56464; right-filled 943×104 region at y21.
- CP tint knots 0→0xfff06767, .35→0xffffae44, 1→white. Fill length is `883×(.16+.8×progress)`, with the source diagonal left edge `103×sin(44°)` at (47,30), height84, right edge930. Empty CP retains the authored 16% angled cap; it does not shrink to an unrelated zero-width rectangle.
- Category icon alpha subtraction at source coordinates (857,43), 65×65, implemented by a modern GLSL150 equivalent of `cpbar_cp.frag`. The icon is a hole in the CP texture, rather than a separately pasted icon.
- Overload warning uses `!overloadFine && overloadDelay>0`, matching CPData `!overloadFine && untilOverloadRecover>0`. Ordinary post-cast delay does not trigger warning. The front scroll is 1 UV cycle per 10 seconds, with the exact 914/974 UV span, multiplied by the source mask; highlight alpha .3+.35×(sin(time/200)+1).
- Recovery/interference dims usable CP to .3 of normal alpha; normal recovery remains visually distinct from the initial overloaded warning. Literal source autoLerp's zero-progress endpoint bypassing master alpha is retained, including its otherwise surprising red cap behavior at exactly zero.
- Context consumption hint renders a pulsing current-CP layer at .2+.1×(1+sin(time/80)), then the predicted post-consumption fill. Input is a public bridge; there is no invented hint for skills that lack a source IConsumptionProvider.
- Interference uses 60 random offset keyframes, 80–400ms holds, cubic random radius, source aspect-scaled jitter, source Hermite alpha curve, and 10ms quantized alpha samples. Randomized realization is reproducible with an explicit seed for tests; runtime uses a fresh realization.
- Hold activation key to display aligned CP and OL labels/current/max values at x110, y55/y85, font option 40, alpha .6×master: 200ms delay,400ms fade-in, 300ms release fade when held>400ms. A shorter hold removes the numbers immediately, matching source. Warning suppresses numbers.
- Preset switch strip: four 52×52 boxes at (580+i×62,136), digits 1–4, font 46 bold, source 2-second lifetime with 400ms endpoint ramps and rapid-switch fade-in suppression. Source-selected white glow retained.
- Active-delegate cancellation hint preserves source placement at right-aligned x500, y140, font 44, background margins 8 and source glow.

## Reconstructed key/delegate widget

- Source 140×210, scale .23, right aligned and center aligned, offset (0,30). Each nonempty delegate group gets a 200-authored-pixel column to the left; default group `def` first, then alphabetic groups; 92-pixel row spacing. The adapter also accepts `default` as a compatibility alias.
- 300ms show fade, original `back`, `icon_back`, `key_short`, `key_long`, `mouse_left`, `mouse_right`, `mouse_generic` textures and exact source positions/size. Keyboard labels use current translated bindings; short/long sprite cutoff is length<=2.
- Original arithmetic-mean ShaderMono is ported, including grey .7 key backing and monochrome key text/frame/skill icon/glow during lockout or cooldown.
- IDLE alpha .7; CHARGE amber and ACTIVE blue source glow with .6+.2×(1+sin(time/50)) pulse. During cooldown icon alpha .4 and grey lower portion height `62×remaining/max`. Cooldown maximum comes from the presets implementation's captured persisted maximum, not a guess from remaining ticks.
- Caller must omit delegates superseded by a duplicate physical key binding. Binding a catalog entry does not itself imply executable runtime support.
- Coin/Railgun ready/charging state belongs to this skill icon. Remove the previous `ClassicCoinEffects.onRenderGui` colored readiness placeholder when merging; retaining it would duplicate a non-source HUD.

## Background mask and fonts

- Uses the original full-screen `effects/screen_mask` texture. Category colors/alpha are exact: EM (20,113,208,100), MD (126,255,132,80), TP (164,164,164,145), VM (0,0,0,255), overloaded (208,20,20,170). Color and alpha approach targets at 1 unit/second; deactivation fades alpha toward0.
- Source font preference `Microsoft YaHei`, fallback sequence (user preference, 微软雅黑, Microsoft YaHei, SimHei, Adobe Heiti Std R), then Java's logical default are retained. AWT 24-point font, 33×33 glyph raster, ascent+1 baseline, 3-pixel left margin, source font-option scaling, plain/bold families, antialiasing, trilinear mip generation, and -.65 LOD bias are adapted into owned Minecraft DynamicTextures.
- A stripped runtime lacking AWT/fonts receives an explicitly logged Minecraft-font fallback. Standard Minecraft Java 21 includes java.desktop; no OS font installation or new external download is performed.

## Honest parity limits

This is source-grounded reconstruction, not a claim of verified pixel-identical screenshots.

- Modern core shaders/vertex buffers replace compatibility OpenGL/GLSL 120. Vertex colors use Minecraft's packed 8-bit format; original GL float inputs and platform driver rasterization/filtering can differ slightly.
- Glyphs use individually owned 33×33 dynamic textures instead of the legacy 2048×2048 atlas. Raster and layout equations match, but atlas edge rounding, mip boundaries and modern clamp/wrap details can differ by subpixels. The actual font still depends on which source-selected system font is installed; Linux without Microsoft YaHei follows the same source fallback rather than silently bundling copyrighted Windows fonts.
- Screen-mask resource uses modern default texture loading rather than the exact legacy custom GL mipmapped uploader. Low-frequency mask layout/colors are restored, but minification filtering can differ.
- Game-time animation phase starts with the client session; rates/durations/pause behavior are preserved, but source used a globally phased GameTimer. Interference is inherently random and has no canonical identical realization.
- Inputs bridges carry numbers hold, selected preset, active context visual state, captured cooldown duration and consumption hint. Precise GroundShock CHARGE→ACTIVE uses `ClassicGroundShockEffects.localPrepareTicks()` (<5); its captured `consumptionHint()` supplies the predicted CP layer in the supplied integration snippet. A held physical delegate alone is insufficient to establish authoritative context readiness; parent must connect its packet/context state where available.
- Shader failures log and use source-style texture-only fallback; category hole/scroll mask/mono parity is then unavailable. Runtime validation must check that all 3 core shaders loaded and no shader warning appears.
- GUI widgets' original configurable positions and font preference are restored in a new client config; the legacy drag-to-reposition global HUD editor itself is outside this task.
- Rendering through RenderGuiEvent.Post is an ordering adaptation from legacy foreground=false AuxGui dispatch. Interaction with other mods' HUD ordering cannot be claimed identical.
- CurrentChargingHUD's separate first-person charge mask/sub-arcs are owned by the charging-effect milestone, not this CP/key widget bundle; this document does not claim to recreate those effects here.

## Validation and integration status

Pure deterministic tests pass 8,173 assertions covering authored geometry/alignment, color ramps, progress clamps, CP buffering, activation/key fades, held-number timing/release boundaries, initial warning versus recovery, preset ramps/repeated switches, deterministic interference/range bounds, mask targets/rates and scroll period. All new Java classes compile against cached Minecraft 1.21.1 / NeoForge 21.1.252 APIs with javac 21 (only NeoForge event-bus deprecation warnings).

These are timing/layout/API checks. They do not substitute for running the merged client. Parent should additionally capture normal, empty CP, high overload warning, recovery/interference, held numbers, and preset switch screenshots at 2 GUI scales; verify original textures, categoryhole, font glyphs, no duplicate coin placeholder, context-state bridge and no shader errors; then run dedicated-server cold linking.
