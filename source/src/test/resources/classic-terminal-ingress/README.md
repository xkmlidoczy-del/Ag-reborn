# Modern terminal common ingress oracle

Run with Python 3 and JDK 21:

    JAVA_HOME=/path/to/jdk21 python scripts/verify-classic-terminal-ingress.py

For a separately staged terminal implementation, choose `--common-root` and
`--dependency-root` to select the repositories containing the terminal and
wireless graph sources. After promotion both default to the current checkout.
`--report` writes counts and SHA256 hashes of all actual production inputs.
No `.reference`, `.staging`, Gradle, network, Minecraft launch, desktop, or
client runtime is required. Compiled classes exist only in a temporary folder.

The suite compiles and executes the actual production `TerminalNetwork`,
`TerminalFrequencySessions`, `TerminalEvents`, `TerminalStorage`, and
`TerminalState`, plus the actual `ClassicWirelessGraph`, `ImagFluxNode`,
`ImagFluxMatrix`, `ImagFluxGenerator`, and `ImagFluxReceiver`. It does not
substitute models of their admission, authorization, storage, or graph logic.
`harness-manifest.json` locks each finite shim and the Java test harness by
SHA256. The complete GPLv3 license is included with this bundle.

The finite shims implement only the server/world/tile/capability/network
boundary touched by those classes. World collision results, chunk availability,
permissions, thread state, player life state, capability stores, and packet
capture are controlled inputs. Real graph links and persistent state changes
are asserted through their actual public APIs. The real production block
entity implementations and NeoForge transport/runtime are verified in the
separate native lane; this harness does not claim to simulate those systems.

Coverage includes:

- Actual sender rejection for null/removed/dead/spectator/wrong-thread players,
  known command routing, malformed commands, absent terminal, unknown app IDs,
  source default apps, tutorial opening, and skill-tree synchronization
- Actual frequency app/terminal gates, null/malformed fields, 128-character and
  control-character password restrictions, and unknown action rejection
- Finite no-hit/occluded/different-target ray inputs, four-block ray endpoint,
  finite-vector rejection and diagonal crossing-chunk preflight before any clip
  invocation, unloaded chunks without lookup, raw-hit/target/origin permissions, unavailable
  matrix and absent network rejection
- Real SSID query, exact case/whitespace-sensitive matrix/node passwords, real
  graph matrix-to-node and node-to-generator/node-to-receiver links, idempotent
  replay without duplicated capacity, and graph capacity/range admission
- Matrix-part origin canonicalization with exact raw requested target and
  selected endpoint echo for response correlation
- Sender-specific transient capabilities, mismatched selected endpoint,
  expired session, dimension change, tile identity replacement/removal,
  password changes, failed-auth revocation, close/open/app revocation, and
  persistent installed state independent from transient authorization
- Actual death/respawn/dimension/logout/clone/server-stop hooks, state saving
  and synchronization, and capability cleanup
- Actual stream-codec writer/reader argument bounds, all client outbound
  request routes, server payload registration, and rejection of non-server
  context players
- PacketDistributor-captured server snapshots and frequency replies without
  matrix or node password disclosure
- Actual common network/session/storage/lifecycle/graph class loading while
  client namespaces are denied

Session expiration uses reflection solely to set the actual private session
record's deadline to the past; it then executes the normal public request path.
World movement checks assert that the clicked endpoint uses the current player
ray while a selected, authorized source remains usable during the short session.
No sleep or game tick is needed.

The source `Syncs` handler/event boundary is independently executed in the
classic-terminal-source suite. Modern sender, world-ray and transient-session
checks are additional ingress protections, not checks attributed to classic
`Syncs`. This fixture runs actual modern code rather than reproducing its logic.
