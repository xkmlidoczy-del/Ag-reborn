# Classic terminal common-source oracle

Run from any checkout with Python 3 and JDK 21:

    JAVA_HOME=/path/to/jdk21 python scripts/verify-classic-terminal-oracles.py

A separate modern checkout can be selected with `--common-root /path/to/repo`.
`--reference-only` executes the classic fixture and exact recipe/asset checks.
`--report /path/to/report.json` writes a machine-readable result. The runner
uses a temporary directory for all `.class` files and removes it on completion.
There are no Gradle tasks, Minecraft launches, network requests, `.reference`,
or `.staging` runtime dependencies.

## Independent source execution

The SHA256-locked fixtures preserve original source bytes. AcademyCraft files
are pinned to classic1.0.7 commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`.
LambdaLib sources are pinned to release 1.2.3 and exact SHA256 values.
`source-manifest.json` records every copied source path and its provenance.
`license-integrity.json` locks the complete GPLv3, MIT, and fixture notices.

The compiler executes unchanged `TerminalData`, `App`, `AppRegistry`,
`AppEnvironment`, `ItemApp`, `ItemTerminalInstaller`, app definitions in
`AppSettings`, `AppSkillTree`, `AppFreqTransmitter`, and `ModuleTutorial`, their
events, and `ModuleTerminal`. Unchanged LambdaLib `NBTS11n` and
`SerializationHelper` serialize the actual original terminal data. Unchanged
LambdaLib `RecipeParser` parses the complete original recipe file.

There are finite boundary stubs for old Minecraft/Forge and the incidental
Guava cache/reflection APIs. Source app UI bodies are never invoked. A call to
Minecraft UI or auxiliary GUI methods fails immediately. `MediaApp.scala` is
retained unchanged as source evidence for the ID `media_player` and its
non-default state; its Scala UI body is excluded from execution, and the
harness creates a plain original `App` with that source-defined name.

The modern harness compiles the actual six common production files
`TerminalState`, `TerminalStorage`, `TerminalInstallerItem`, `TerminalAppItem`,
`TerminalInstalledEvent`, and `AppInstalledEvent`. Its finite modern NBT,
player, item, event-bus, and network shims record observable effects without
launching NeoForge. The production `TerminalNetwork`, frequency implementation,
client transport, and lifecycle event wiring are not executed here; the test
checks storage lifecycle calls through a finite transient-session shim.

## Coverage

- All 120 registrations of the five app definitions
- 4,200 exact classic/modern item-use observations: terminal/app state, semantic
  app IDs, item count, sync count, event count, notification channels, chat keys
- Survival and creative, server and client item paths, repeated installs, and
  app use before the terminal exists, null/unknown app rejection, and
  crosswired client-world/server-player no-mutation guards
- Actual original NBT round trips for all eight optional-app subsets with each
  terminal flag under every registration order
- Direct `installApp` before terminal installation, matching the original data
  method; the item entry point separately requires the installed terminal
- Modern semantic NBT schema, unknown saved ID preservation, stable ordering,
  detached immutable observations, empty data, and explicit absence of numeric
  bitset reinterpretation
- Save/remove/reload, player clone, independent player data, cache cleanup and
  removal of transient sessions
- Newly explicit empty-stack, wrong-held-item, and invalid-sender guards
- Common class cold loading while all modern client package access is denied

Original comparisons are made through semantic app names because the source
numeric registry IDs depend on registration order. The old bitset is never
imported into modern persistence. Modern save format behavior is tested on its
own public encode/decode API; it does not claim old save-file migration.

## Recipes and assets

Recipe blocks 22/38/39/40 are parsed independently in Python and differentially
checked against unchanged LambdaLib `RecipeParser`. Each modern JSON is checked
for exact dimensions, ingredient identity or iron-plate tag, amount, and result.
`recipe-witnesses.json` includes all eight unique legal 3x3 offsets/mirrors and
72 missing/extra-ingredient negative grids. Four modern item models are checked
against their exact classic texture IDs.

`asset-inventory.json` inventories 35 non-song terminal/app assets and nine
explicit exclusions, with byte hashes and expected destinations. The four item
textures this oracle promotes are checked strictly. Other assets present at
listed destinations must match the original bytes. Inventory-only entries do
not imply that their full renderer or client UI has been ported.

The excluded material is the three song audio files, three album-cover images,
and three song-item images for `only_my_railgun`, `level5_judgelight`, and
`sisters_noise`. The fixture does not distribute those bytes. The runner checks
all PNG/OGG files in modern main resources against their excluded hashes,
including relocated files. Original terminal select/confirm sound effects and
app/terminal UI/item textures are non-song assets covered by the project notice.

The complete GPLv3 license is provided as `LICENSE-AcademyCraft-GPL3.txt`; the
complete LambdaLib MIT license with its copyright notice is
`lambdalib/LICENSE`. AcademyCraft source file notices remain unchanged.

## Frequency transmitter source boundary

Unchanged original `Syncs`, wireless interfaces, `LinkNodeEvent`,
`LinkUserEvent`, and their event bases execute in `FrequencySourceOracle`.
It tests real SSID versus absent-network null, exact case/whitespace-sensitive
matrix and node passwords, nonexistent-network rejection, both canceled and
accepted node/user link events, canonical matrix event payloads, generator and
receiver routes, no-auth versus explicitly authenticated user-link event
constructors, and all five request channel argument forwards.

The original node-link handler delegates password approval to `LinkNodeEvent`
listeners; it does not itself compare passwords. The test intentionally shows
that an uncanceled wrong-password event returns true at this handler boundary.
The user-link handler likewise derives its result from cancellation and its
two-argument event has `needAuth=false`. Modern sender validity, terminal/app
gating, unloaded-chunk rejection, permissions, four-block unobstructed ray
selection, origin canonicalization, endpoint identity, password input bounds,
and transient authorization sessions are additional modern ingress safeguards,
not claims about checks present in original `Syncs`. The independent common
frequency fixture tests those modern safeguards separately.
