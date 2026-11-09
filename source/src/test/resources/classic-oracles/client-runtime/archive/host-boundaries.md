# ClientRuntime host oracle boundaries

This oracle compiles and executes the byte-identical canonical AcademyCraft 1.0.7
`ClientRuntime.java`, `KeyDelegate.java`, `AbilityEvent.java`, and the five
activation/deactivation/preset/flush event Java classes copied under `original/`.
`source-hashes.json` pins every copied witness to its canonical file. The runner
checks canonical and copied bytes before compiling.

The candidate is independently compiled first with only JDK 21 and the cached
Guava 32.1.2 JAR. Its generated classes are stored in a separate output directory.
The differential harness checks its code-source URL against that directory.
A separate JDK-only probe class creates a loader containing only candidate
classes and Guava, denies Minecraft, NeoForge/Forge, Mojang, LWJGL, FML and
LambdaLib namespaces, resolves every generated candidate class and invokes
the coordinator's business methods. This demonstrates local JVM linkage only.

## Declared platform/test hosts

All 32 host Java sources are under `hosts/`; the runner records each source hash.
They are never on the candidate-only compiler or cold-loader classpath.

- `Controllable`, `Skill`: test preset activation callbacks and stable skill
  identities/icons; no actual skill implementation or capability owner
- `Context`: ALIVE/TERMINATED state and a terminate counter; no actual context
  manager, context replication, transport, rendering or termination engine
- `AbilityData`, `CPData`: per-player category and raw activation facts;
  `isActivated = hasCategory && raw`,
  `canUseAbility = raw && overloadFine && !interfering`, and raw assignment/request
  recording; no source CPData transport, event posting or CP/overload resource engine
- `PresetData`, `ClientHandler`: test slot mappings and configurable physical key
  IDs; actual original `Events.updateDefaultGroup` calls these controllables;
  no skill catalog, modern registry or activation-key timing adapter
- `Minecraft`, `EntityPlayer`, `DataPart`, `EntityData`: local player availability,
  runtime attachment and initializer flag recording; no real player, world,
  session replacement, death reset, data-part owner or engine bootstrap
- `CooldownData`: configured blocked skill identities; no actual cooldown engine
- `KeyManager`: explicit held-key set and stable display key name; no physical
  keyboard or mouse, GLFW, native input event bus or window
- `ClientUtils`, `AuxGuiHandler`, `TerminalUI`, `SideHelper`: explicit game/GUI/side
  predicates; no actual GUI or platform-side detection
- `ControlOverrider`: records every override replacement in order; no actual game
  control suppression or renderer
- `AcademyCraft`, `ModuleCoreClient`, `StatCollector`, `Color`, `ResourceLocation`,
  `RegistryDelegate`: inert log/helper/value hosts used by the copied source
- `Registrant`, `RegEventHandler`, `RegDataPart`, `SubscribeEvent`, `SideOnly`,
  `Side`, `Event`, `TickEvent`: annotation/type/phase hosts; no annotation scanner,
  Forge/FML/NeoForge bus or real platform event dispatch

## Exact exercised source surface

Direct calls execute the original constructor, instance lookup, add/get/clear,
per-key tick loop and abort methods, handler registration/removal/selection,
default activation handlers, `ActivateHandlers.terminatesContext`, translated
hint helper, and the actual `Events` preset/activation/deactivation/flush methods.
The private original `updateDefaultGroup` is invoked reflectively for one explicit
scenario and also reached through original observer/preset/flush methods.

The original event classes execute unchanged, but their methods are invoked by
the test harness. There is no claim that a real client bus dispatched these
events. In particular, this stage creates no XP/Level client observers and cannot
establish that such events arrive at a modern client.

The copied `KeyDelegates.scala`, `CPData.java` and `Context.java` are additional
read-only source witnesses and are not compiled here. The persistent-context
survival case invokes unchanged inherited `KeyDelegate.onKeyAbort`, whose body
is empty, and demonstrates that the supplied host context remains alive. This is
a witnessed callback contract scenario, not execution of Scala KeyDelegates or
the original context engine. Two-live-context stack scenarios invoke actual
original/candidate termination-handler factories against those host contexts.

Reentry coverage is bounded: an explicit abort callback clears and rebuilds a
group without structurally changing the key-state map being traversed; down/tick
callbacks reenter explicit abort. These preserve source callback/state order.
The oracle does not declare arbitrary collection mutation inside a callback
safe, suppress source fail-fast exceptions, or add a protective semantic policy.

No Gradle, Minecraft/client/server bootstrap, user computer, browser/CUA, network,
push, publication or paid service is used. All generated files are confined to
this isolated stage. Main sources, main docs, canonical reference sources and
earlier stages are read-only inputs.
