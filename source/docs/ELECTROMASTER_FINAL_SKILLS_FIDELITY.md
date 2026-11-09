# Final original Electromaster active skills

This staged private port supplies AcademyCraft1.0.7's last two category-specific active gameplay paths: `body_intensify` and `thunder_clap`. `BodyIntensify` is **active**, as both the actual Scala activate override and the canonical catalog say. Together with the seven already-integrated category actives, this covers the original nine functional category-specific active skill IDs. It does not claim the whole mod is ported, nor claim execution of this staged package.

## Learning and classification

The existing `classic-skills.json` is retained byte-for-byte. Source `CatElectromaster.java` assigns:

- BodyIntensify: level3, parent ArcGen mastery1, independent CurrentCharging mastery1, Normal or Advanced developer, seven stimulations
- ThunderClap: level5, parent ThunderBolt mastery1, no Railgun prerequisite, Advanced developer, fifteen stimulations

The additive request allowlist enables their existing genuine DevelopmentController/finite-IF learning paths. Naturally learned skill mastery begins at zero. No command grant replaces development, inventory, recipes, or energy. Learning regression also executes the real dependency-free development state machine through Normal and Advanced finite batteries, validates all TPS+1 energy pulls, fails on power exhaustion and rechecks prerequisites at completion.

## ThunderClap executable behavior

- Capture current mastery as Float on context construction
- Start pays Float overload lerpf(390,252,exp), no CP; source OverloadEvent disposal is represented by post-payment validity and an immediate matching end when overload locks use
- Each accepted server tick recomputes a solid-block-only, no-fluid/no-entity ray from the eye/head direction at40 blocks, normalized as source Motion3D; fallback is the40-block aim endpoint
- Increment tick, then pay Float CP lerpf(18,25,exp) only for ticks1..40
- A failed CP tick before40 cancels; failure exactly40 still fires. Ticks41..59 incur no CP. Tick60 fires automatically
- Key-up and key-abort are termination at **every** charge age. Releasing at40,41 or59 does not fire
- Genuine nonvisual-only vanilla LightningBolt is spawned. Default lightning weather sound, strike/fire and gamerule behavior replace legacy weatherEffects, including its additional native strike consequences
- Original attackRange queries loaded entities of all kinds except the caster in an inclusive feet-coordinate sphere, with no line-of-sight requirement. Range is Float lerpf(15,30,exp); damage is lerpf(36,72,exp)×lerpf(1,1.2,(ticks−40)/60), preserving divisor60
- Damage falls linearly to zero at the sphere boundary using the source Float ratio/factor intermediates. The modern LightningBolt is excluded from the range query because1.7 stored weatherEffects outside its normal loaded-entity list
- Existing damage scale/player-attack policy is honored; vanilla ServerPlayer PvP/team checks remain the existing port safety convention. The source canAttack rule excludes painting/item-frame damage when terrain destruction is disabled
- Set source cooldown Int(ticks×lerpf(10,6,exp)) BEFORE adding .003F mastery/level EXP, then terminate. No cooldown or EXP on cancellation

Client input/parameters: BOLD five-subarc surround, local gray three-layer ripple, source40-block aim, local walk-speed .1→.001 over60 ticks, immediate restore to.1 on end, ripple immediately removed, surrounds linger10 ticks. ThunderClap defines no custom sound. See `THUNDER_CLAP_VISUALS.md` for every original arc/ripple parameter and render adaptation.

## BodyIntensify executable behavior

- Capture only Float per-tick CP lerpf(20,15,exp) at context creation
- Start pays Float overload lerpf(200,120,currentExp), captures resulting overload floor, no CP; every active tick restores any lower overload to that floor before increment/consumption
- Pay CP only on ticks1..40. Any failure in those ticks cancels with performed=false. Ticks41..99 are free; tick100 auto-cancels with performed=false
- Key-up releases; abort only terminates. Release before10 is failure. Release at10 succeeds with Hunger even though probability0 yields no beneficial buff
- Clamp successful release charge to40; use current mastery for duration. Double probability=(charge−10)/18. Double duration=Int(RandUtils.ranged(1,2)×charge×lerp(1.5,2.5,currentFloatExp)); hunger duration=Int(1.25F×charge), amplifier2
- Preserve the real source quirk: `Random.shuffle(effects)` returns an unused immutable Vector, and the buff index increments before lookup. Actual successful selection order is Jump, then optional Regeneration. Speed, Strength and Resistance are unreachable in this1.0.7 implementation. The deterministic differential tests explicitly preserve this behavior
- Each candidate roll uses uniform[0,1); decrement probability by1 each iteration. Buff amplifier=min(floor(probability),original effect cap), with cap1 for the two reachable buffs
- Apply native effects and HungerIII, then add .01F EXP BEFORE deriving Int(lerpf(900,600,newCurrentExp)) cooldown. A novice10/40 release therefore has cooldown897, not900
- Cancellation never grants a buff, EXP or cooldown. Every accepted start's paid overload/CP remains spent

Client input/parameters: local-only looping em.intensify_loop at source default volume.5/pitch1, original charging mask and sprites; successful broadcast plays em.intensify_activate at.5 and one15-tick thin-arc effect. Executable upper-exclusive integer counts are5–6 held sprites,10–14 successful first-person sprites, exactly3 arcs per wave. See `BODY_INTENSIFY_VISUALS.md` for exact wave, HUD, random, sound and rendering details.

## Modern lifecycle and network guards

Server state/mastery/aim/ticks/RNG are authoritative. Public skill-name methods remain trusted test/internal seams; existing network ingress never exposes raw skill names. Normal authenticated slot transitions are preserved. The two new physical-input paths use additive `slot_press_token` with canonical `<slot>:<positive-client-nonce>` correlation only. It cannot supply a target, duration, mastery, cost or effects. Server monotonic replay history prevents nonce re-entry after cancellation; logout/death/clone final disposal retires that history.

Client packets echo the input nonce plus a server-owned positive context token. Matching local nonce is checked before touching token history, context, HUD, speed or sound, so rapid abort/repress cannot be revived or canceled by an older start/end acknowledgement. Remote viewers use only authoritative server tokens. Local internal nonce0 is accepted only when physical input is undriven. Every accepted start audience is captured within source25-block range and retains end delivery even if the viewer moves away.

Repeated ticks in the same world gameTime are suppressed; active state identity/world/death/spectator/category/level/interference/overload/cooldown conditions are rechecked. Discharge/release commits are reserved against callback re-entry. Preset switch/edit, deactivation, GUI/input abort, logout, clone, death, dimension change and shutdown are integrated additively alongside every old lifecycle hook. Held inputs never persist across restart.

## Explicit limits

- Native gameplay fixtures are compiled only in this worker; main integration must execute them before counting the two paths as runtime-verified
- Pixel/audio parity and live observer/network rendering are unverified. Original assets and parameter/timing arithmetic are preserved, but modern source-derived CPU ribbon geometry/random processing is not pixel-identical legacy display-list geometry
- Body HUD stage is modern RenderGuiEvent.Post HIGH before the existing normal-priority ability HUD. Its world arcs use the modern particle output stage. ThunderClap's through-wall ripple uses AFTER_LEVEL after Fabulous compositing. Both scope actual shader/model-view state; GUI/ripple explicitly disable depth because1.21.1 NO_DEPTH_TEST is a no-op. Their new client-registered classic_skill_alpha fragment preserves every low-alpha sample: the stock position_tex_color shader discards alpha<.1, whereas source Body HUD disables alpha testing and RippleMark uses threshold0. Source world arcs retain the stock material; actual GPU registration remains to be checked live
- Modern local movement mirrors the source capability walk speed onto the local MOVEMENT_SPEED base while retaining modifiers, because1.21 Player.getSpeed uses attributes. No server attribute or abilities packet is changed
- Existing port progression/resource storage uses its established Double state. This package preserves each source Float/Double formula and literal ordering, without changing global storage arithmetic
- Original manual `triggerAchievement` calls have no modern advancement counterpart because the current port has no category achievement infrastructure. Trophy parity is not claimed
- No production files, build configuration, constructor, existing clients, saves, user computer, paid service, push or publication were changed/used by the staging work
