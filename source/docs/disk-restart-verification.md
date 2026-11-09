# Native two-process disk restart probe

`AcademyRestartRuntimeTests` is a separate, opt-in `academy_restart` namespace. The normal `academy` run still contains its existing 34 tests. The existing jar exclusion for `cn/academy/port/gametest/**` also excludes this probe from the shipped gameplay jar.

## Run configuration

Add this independent run inside the existing `neoForge { runs { ... } }` block. Do not enable the namespace on a client run or point it at a gameplay save.

```groovy
restartGameTest {
    type = 'gameTestServer'
    gameDirectory = layout.projectDirectory.dir(
        providers.gradleProperty('restartDirectory').getOrElse('run-restart'))
    systemProperty 'neoforge.enabledGameTestNamespaces', 'academy_restart'
    systemProperty 'academy.restart.phase',
        providers.gradleProperty('restartPhase').getOrElse('verify')
    programArgument '--world'
    programArgument 'academy-restart-world'
}
```

After authorized Minecraft startup, run the bounded orchestrator from the checkout:

```sh
scripts/verify-disk-restart.sh
```

It allocates a fresh `run-restart*` directory, launches `runRestartGameTest` first with `-PrestartPhase=seed` and then `-PrestartPhase=verify`, and shares that same isolated directory. Each phase has a 240-second timeout plus a 30-second termination grace. It refuses existing directories, does not delete any save, requires exactly one native test pass per phase, and preserves separate Gradle/Minecraft logs. A timeout is a failure and does not establish hard-crash correctness.

Equivalent manual commands, using a fresh directory for the first phase:

```sh
scripts/gradle-cloud.sh runRestartGameTest --console=plain -PrestartDirectory=run-restart-manual -PrestartPhase=seed
scripts/gradle-cloud.sh runRestartGameTest --console=plain -PrestartDirectory=run-restart-manual -PrestartPhase=verify
```

The fixture checks the actual process working directory and canonical native world/playerdata paths. It accepts only `run-restart*` working directories and the child world named `academy-restart-world`; it will not touch `run-client`, `run-server`, or another save. Seed refuses a previously saved probe UUID or certificate; verify refuses an already verified probe.

## What is tested

- Actual `PlayerList.placeNewPlayer` admission for fixed profile `6c7c7918-557c-4f9a-9426-88dd520dc516` / `ACRestartProbe`. The embedded connection declares `academy:client_data` before login subscribers send initial state, as required by NeoForge's network-aware mock fixture
- A native `PlayerList.saveAll` → `PlayerDataStorage.save` → compressed on-disk UUID `.dat`, observed through `PlayerEvent.SaveToFile`. The first file contains six inventory coins and one genuine pending coin escrow
- The player remains online when the native GameTest server completes. Actual `ServerStoppingEvent` runs the production coin refund and progress save before `MinecraftServer.stopServer` performs the final `PlayerList.saveAll`. The final native save contains exactly seven coins and no escrow. The certificate is written only after `ServerStoppedEvent` and a final disk recheck
- A second Minecraft JVM, identified by a different PID and independent boot UUID, reads the native file and admits the same profile. Actual `PlayerDataStorage.load` is observed through `PlayerEvent.LoadFromFile`; the new ability cache must decode exactly the native on-disk state
- Full progress-tag equality against an independent known fixture, with explicit stable assertions: electromaster level 4; active and overload-fine; ArcGen .375 and Railgun .625 mastery; brain/mind passives .25/.5; extra CP 125.75; extra overload 7.25; level experience 1.125; CP 321.25; overload 73.5; initial delays 15/32; cooldowns 300/900
- Full native inventory/item serialization equality, including a portable developer's entire `CUSTOM_DATA`: finite 4321.25 IF and an unrelated `restart_fixture=finite-seed-v1` sentinel
- Five consecutive actual world ticks after re-admission, each driving one explicit registered player-post-tick event. Full independent expected state must match every tick. Recovery delays keep CP/overload unchanged; delays/cooldowns decrease by exactly the observed bounded tick count. Coin count stays seven, escrow stays absent, and no transient QTE resumes or double-refunds

The seed allows at most two observed player-post events between setup and stop. The independent expected model applies that bounded count; it never treats loaded values as their own expected answer. Verify allows exactly five events across its five world ticks.

Evidence in the disposable run directory:

- `seed-gradle.log`, `seed-minecraft.log`, `verify-gradle.log`, `verify-minecraft.log`
- `academy-restart-seed-proof.nbt`: actual lifecycle/native-save flags, seed PID/boot ID, bounded seed tick count, explicit expected progress
- `academy-restart-verified.nbt`: distinct verifier PID/boot ID, exact five-event verification, final verified progress
- `academy-restart-world/playerdata/6c7c7918-557c-4f9a-9426-88dd520dc516.dat`: actual native player file; verify's normal fixture removal subsequently saves its five-tick-evolved state

## Scope and implementation sources

This establishes native disk persistence across two completed Minecraft JVM processes and graceful-stop pending-coin refund/cleanup. It does **not** establish a real multiplayer socket, real-client request transport/codec/handshake, rendering, input cadence, or hard-crash/kill/power-loss durability. The embedded mock is admitted to the real PlayerList, but no live client exists; player-post events are deliberately driven at bounded actual world-tick boundaries. Seed state and finite item energy are test fixtures, not claims of a player earning them through gameplay.

The lifecycle and native storage design were checked against the generated official NeoForge/Minecraft sources in `build/moddev/artifacts/neoforge-21.1.252-sources.jar`, specifically `net/minecraft/server/Main.java`, `MinecraftServer.java`, `players/PlayerList.java`, `world/level/storage/PlayerDataStorage.java`, `world/entity/Entity.java`, `gametest/framework/GameTestServer.java`, and NeoForge `event/entity/player/PlayerEvent.java` / `gametest/GameTestHooks.java`. No production gameplay source or build configuration is changed by the probe itself.

## Validation status

**PASS in the actual m05 runtime run (2026-10-01 UTC).** Seed PID 190 and verifier PID 466 were distinct Minecraft JVMs. Both phases executed exactly one required native test, passed, and completed with successful Gradle exits. The seed stop produced the native final-save certificate; verification loaded that disk playerdata and checked five bounded ticks. Evidence: `runtime-evidence/m05-disk-restart.log`, plus immutable copies of the seed/verified NBT certificates under `runtime-evidence/disk-restart-m05/`. This is graceful-stop durability only; it does not prove hard-crash or real-network correctness.

## Actual m06 extension

Two fresh native Minecraft JVMs passed again (seedPID190, verifyPID463). The independent known fixture now includes four preset pages with selected index2/revision5, Arc/Railgun bindings on separate pages, and fixed cooldown maxima300/900 while remaining ticks decrease. Native full-tag equality, finite item energy, pending coin graceful refund and five bounded post-reload ticks passed. Evidence: runtime-evidence/m06-disk-restart.log; this remains graceful-stop only.
