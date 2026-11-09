# Classic terminal and application increment

Behavior baseline: AcademyCraft classic **1.0.7**, expected commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`, and LambdaLib **1.2.3**. Existing guide, developer, finite wireless graph and machines remain genuine dependencies. Original third-party songs, album covers and song-item artwork are excluded; no song collection items, song loot or substitute tracks are registered.

## Persistent installation and actual item use

`academy:terminal_installer` is the source nonstackable installer. Its first successful server use consumes one in survival, records installation, saves and synchronizes state, posts the server terminal-installed event, informs the local client and starts the source installation effect. Creative retains the installer. Repeated use sends the original already-installed notice and neither consumes another item nor repeats events/effects.

The terminal starts with exactly the source **settings** and **tutorial** apps. These are preinstalled predicates, not artificial saved installation bits. Only **skill_tree**, **freq_transmitter** and **media_player** own real default-stack64 installer items, named `academy:app_skill_tree`, `academy:app_freq_transmitter` and `academy:app_media_player`. First app use requires an installed terminal, consumes exactly one survival item, saves/synchronizes and emits one server/local-client app event plus the original installed notice. Missing-terminal and already-installed uses preserve the stack. Creative installation preserves the stack. Like original TerminalData, the pure state method can install an app without the terminal; real item and wire ingress always enforce the terminal prerequisite.

The modern player save stores an installation flag and semantic app IDs under schema1. Saved unknown app IDs survive load/clone, but cannot produce executable apps or installers. Ordering is stable settings/tutorial/skill_tree/freq_transmitter/media_player; original annotation app registration order was nondeterministic. This is not a classic bit-index/world importer. Login, save, logout, clone, respawn and dimension changes maintain state; transient frequency authorization does not survive death/clone/logout/dimension/restart.

Actual crafting, pickup and smelting of these newly real registry items naturally feed the existing tutorial OR ledger. Direct tutorial-item use remains available independently of terminal installation and retains the guide. Terminal tutorial activation goes through the installed-terminal application ingress and invokes the same authoritative guide snapshot/first-open path.

## Exact source crafting

| Source recipe | Modern result | Original occupied cells |
| --- | --- | --- |
|22|terminal_installer×1|data chip / glass pane / data chip; iron plate / brain component / iron plate; info component / redstone block / info component|
|38|app_skill_tree×1|one-column compass; data chip; info component|
|39|app_media_player×1|three note blocks; center data chip; center info component|
|40|app_freq_transmitter×1|one-column resonance component; data chip; info component|

Source `plateIron` remains the genuine existing `c:plates/iron` tag. A recipe resource does not itself prove a full natural survival acquisition chain. Native fixtures physically take genuine CraftingMenu result slots and verify occupied input decrement, preserving the distinct vertical one-column app grids and source output counts.

## Authoritative application and frequency transport

Common classes contain no client namespace references. Wire requests cannot install a terminal/app or write saved IDs. Dead/removed/spectator/wrong-thread and malformed entry are rejected. Every application checks the real persistent installed predicate. The Skill Tree route synchronizes current genuine ability data and opens the source developer-null view; it is not a development device and provides no machine energy, learning, upgrading or developer action authority.

The Frequency Transmitter uses the original world-HUD flow: a **four-block block raycast**, matrix SSID query plus exact password authorization followed by repeated node clicks, or node authorization followed by repeated generator/receiver clicks. It links the existing actual persistent wireless graph. Matrix parts and machine parts resolve to actual canonical origins. Every clicked endpoint must be loaded, editable and the actual unobstructed ray endpoint. No request forces chunk loading or exposes a fictional password in a discovery reply.

Modern server authorization is held per concrete player, server level, selected block-entity identity and source20-second interval. Node or matrix replacement, dimension/lifecycle change, expiry and changed password invalidate admission. The selected source may remain behind while the player approaches another real endpoint as the source flow permits. Successful links retain the source repeated-link workflow. The modern graph's already documented conservation, idempotence and unloaded-chunk corrections remain intact. Sender/ray/session validation is an intentional transport hardening of the original `Syncs` client-provided tile references. Packet replies echo the original action/endpoints, so clients can reject late callbacks after changing state.

## Source client and safe local media

The client stage restores the source terminal world HUD, source pause-aware UI clocks, release-triggered default Left Alt entry, app grid, pointer animation, selected-app sound, installation progress/fade/key hint and app environment transitions. Install progress lasts4000ms with a700ms wait; source blend-in/out uses200ms. Terminal installer inventory stays the source two-dimensional item icon, while ground/equipped contexts use its original model and transforms. Source settings artwork controls real keys, source singleplayer-only gameplay config, Heads or Tails and positions of the actual HUD elements. A narrow trusted `natural_return` coin-end field preserves its optional exactly-once local result when the server return packet arrives before the client simulation finishes; QTE/abort paths do not announce a result.

Media Player is a real client-only local Ogg player. It discovers user-provided `acmedia/source/*.ogg`, uses optional matching `acmedia/cover/*.png`, supports original external name/description edits, first/last-played selection, pause/resume/stop/volume/timing and the source mini-HUD. External media is always available as in classic MediaAcquireData. The internal catalog is intentionally empty, so there is no song acquisition state/item/loot. Playback and local file processing do not transmit tracks or metadata. Malformed/outside-root/symlink sources are rejected through explicit modern local-file boundaries.

## Evidence boundaries

Packaged, pinned immutable original Java/Scala/XML/recipe/model witnesses preserve upstream copyright/GPL/additional notices and LambdaLib MIT text. Original unchanged terminal/item/data, actual NBTS11n and recipe-parser execution are differentially compared with the modern state/item/persistence behavior, including all120 app-registration permutations. Separate actual official binary NBT/event-bus, common cold-link, sender ingress, source client/installer/media and resource-binding checks cover modern adaptations. They need no `.reference` or `.staging` directory at runtime.

The final staged overlay compiles **63 Java sources** and passes **nine headless suites**, with **379,365 counted assertions** plus source/recipe hashes, audio API shapes and fifteen common cold-link class checks. See `TERMINAL_CLIENT_FIDELITY.md` and `MEDIA_PLAYER_FIDELITY.md` for source UI/clock/model and local-media boundaries. Six finite native GameTest fixtures compile against cached official Minecraft1.21.1/NeoForge21.1.252 APIs. They cover genuine registry/item use, all installer counts/creative/events, actual crafting-result consumption, real player lifecycle/guide independence, app forgery rejection and real matrix→node→generator/receiver links. This stage does **not** launch Gradle, a native server/client, desktop UI, audio playback or capture. Main owns that serial acceptance lane. Headless source and compilation evidence does not certify native transport, live visual/audio equivalence, multiplayer, natural survival obtainability or old world conversion.
