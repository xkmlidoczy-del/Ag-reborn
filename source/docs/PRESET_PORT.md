# Classic skill presets port

## Source baseline

Inspected vendored AcademyCraft 1.0.7:

- `ability/api/data/PresetData.java`: `MAX_PRESETS = 4`, `MAX_KEYS = 4`; current index initially zero; all mappings initially empty; independent editable arrays; category-change clears all mappings without resetting current index
- `ability/api/ctrl/ClientHandler.java`: slot defaults left mouse, right mouse, R, F; V activation uses short release strictly below 300ms; N opens editor with a category; C cycles modulo four only when activated
- `ability/api/context/ClientRuntime.java`: persistent physical/real state, logical key down/tick/up/abort; rebuilding the default group aborts delegates; GUI/cooldown/interference/overload abort active delegates without generating a new press from an already held physical key; one delegate per physical key
- LambdaLib 1.2.3 `util/key/KeyManager.java`: edge-triggered key down (not operating-system key-repeat) sampled on client ticks
- `ability/client/ui/PresetEditUI.java` and `assets/academy/guis/preset_edit.xml`: editor browsing starts at page zero and is separate from gameplay current index; clicking another page browses it; editing commits immediately, with no deferred Apply/Save transaction; another row click cancels an already open selector; removal option followed by learned controllable skills absent from that preset
- `vanilla/electromaster/skill/Railgun.scala`: coin QTE can only be offered if the current preset contains Railgun
- `ability/api/cooldown/CooldownData.java`: remaining ticks and maximum ticks are separate; overlapping cooldowns preserve the greater values

Learning never automatically binds a skill. Old schema-1 progress migrates to four empty presets, retaining category, level, mastery and existing progression. The development UI uses the existing developer item; removed fixed R/G/H gameplay bindings are replaced by source V/C/N and four slot controls. Standard Controls settings expose the source slot defaults.

## Modern ownership and safety

`AbilityProgress.presets` is server-owned saved state. Schema 2 stores all 16 sparse mappings, selected index and a revision used to rebuild client delegates. Existing player persistence, clone, login synchronization and periodic state sync include presets automatically. Holds are transient and never stored.

Only `AcademyGameplay.requestFromClient` may be registered on network ingress. It resolves `slot_press`, `slot_release` and `slot_abort` using the server's current preset. Clients supply a slot index, never a cast's skill, cost, mastery, cooldown, coordinates or success. `preset_switch` accepts canonical indices 0–3 only while activated. `preset_edit` accepts `preset:slot:skill_id`, with an empty skill ID removing the mapping. Edits validate all slots atomically and abort active holds even when a different page was edited, matching the source PresetUpdate/default-group flush.

Selections require the server's learned skill, correct category, minimum level, controllable metadata and an implemented gameplay handler. Duplicate IDs in one preset, foreign/passive/unported/unlearned skills, malformed indices, oversized IDs and unknown commands are rejected. Different presets may contain the same skill. Rejecting a request reasserts the authoritative snapshot. A selection can never create learning. Real gameplay also independently checks activation, overload, interference, cooldown and payment.

The common allowlist contains the seven staged implemented active skills: Arc Generation, Current Charging, Railgun, Electron Bomb, Threatening Teleport, Directed Shock and GroundShock. It deliberately does not advertise remaining classic metadata as executable. GroundShock is enabled only together with the verified staged runtime and client hooks, which the integration owner must merge as a unit.

`AcademyGameplay.request` remains a trusted internal/native-test API for existing skill fixtures. It is not a wire endpoint. Keeping its low-level cast/start/release API must not lead to registering it on the network.

Client edits and preset switches predict local mapping changes for the source's immediate response. Incoming server snapshots replace predictions. Prediction changes neither learned state nor CP/costs and cannot authorize a cast; server packet ordering resolves executable input after the accepted switch. Delegate replacement retains physical-down suppression. Edge-triggered C/N controls avoid OS-repeat cycling. If physical control bindings collide, the later slot owns that key, matching the source runtime's single-key map rather than executing multiple skills on one input.

## Editor rendering fidelity and remaining visual verification

The port uses the original `back.png`, `selected.png`, `cancel.png` and skill icons, rather than invented preset art. Logical source geometry is retained: 116.25×141.5 page, 125-unit horizontal page spacing, four 34.75-unit rows, 350ms slide/fade, inactive alpha .3 and scale .8, active alpha/scale 1, four-wide 15-unit icon selector with 18-unit spacing and 2.5-unit margin. The screen retains source immediate edits, separate browsing and current gameplay selection, click cancellation, transition input lockout, title/localization, blackout and default single-player pause behavior.

Minecraft GuiGraphics replaces the unavailable CGui/legacy OpenGL path. Page and texture destination dimensions are integer-rounded; text currently uses the native Minecraft font at an explicit .75 scale, with ellipsis for oversized labels. The selector's CGui glow is approximated by translucent one-pixel borders. It does not yet reproduce CGui's exact font rasterization, fractional texture positioning or blur. Therefore this is source-layout/control-flow parity, not verified pixel parity. Interactive screen QA and source-side screenshot comparison remain necessary before claiming full visual fidelity. The separately recreated classic CPBar/KeyHintUI owns gameplay HUD rendering; this module leaves the old placeholder HUD method for a serial merge.

Closing the editor discards only transient selector/transition state; accepted edits remain saved, as in the source (there is no Cancel-all transaction). Closing an uncommitted selector sends no edit. Changing category closes the stale editor. Server synchronization can reject a stale local selection and restore the valid mapping.

## Verification

`./scripts/verify-presets-standalone.sh` compiles staging main classes, including all four new native GameTests, directly with cached JDK 21 and NeoForge 21.1.252 APIs. It runs `PresetRegressionTest` without Gradle or starting Minecraft. At this stage 131 assertions pass, covering defaults, independent pages, duplicates, unknown/foreign/passive/unimplemented skills, invalid dimensions/indices, defensive copies, removal, category clear, compressed-NBT cold round trip, malformed-save recovery, migration, captured cooldown maxima, selector transitions/cancellation, strict activation timing and held-key suppression.

Five compiled native GameTests in `AcademyPresetRuntimeTests` cover:

1. Compressed NBT, real player persistent storage, cache eviction/reload and clone subscriber
2. Exact network-ingress validation, raw skill-name rejection, resolved slot execution, cooldown/interference/activation gates and malformed input atomicity
3. Preset-switch and any-page-edit cancellation of real charging contexts, strain preservation and late-release replay
4. Finite 78-world-tick learning against a real portable developer stack, no early/selectable learning, consumed IF and explicit separate binding
5. GroundShock mapped slot dispatch reaching its real runtime, switch/edit cancellation and no free-start CP/overload payment

Native tests have not been run by this staged implementation. The parent runs the aggregate GameTest server/build serially. The native FakePlayer tests exercise the registered ingress seam directly; they do not prove on-wire transport or interactive client behavior. Existing disk-restart tests should also be extended with bound preset fixtures as described in the integration notes.
