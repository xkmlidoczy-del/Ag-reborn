# Classic wireless graph staging handoff

The owned deliverable is entirely in `.staging/wireless-energy/graph-stage`. No production file was changed, and no Gradle task, native Minecraft process, client process, user computer, publication, or paid service was used.

## Files to merge

Copy `src/main/java/cn/academy/port/wireless/*.java` to the matching production source package. These are the two native contracts, pure graph, native SavedData resolver/codec, and server-post event adapter. Copy `src/test/java/cn/academy/port/wireless/*.java` to production tests. Copy `fixtures/cn/academy/port/gametest/AcademyWirelessGraphRuntimeTests.java` to the native GameTest source location when the parent schedules aggregate runtime verification.

Do not merge `.javac` outputs. The parent owns the native nodes, matrix, registration, menus, protocol, client work, recipes, and the complete daylight-generated source solar → basic node → Normal developer route.

## Registration and native endpoint contract

- `ClassicWirelessDevices.register(bus)` calls `ClassicWirelessSavedData.register()` once. It delegates to `ClassicWirelessSystem.register()`, which installs a `ServerTickEvent.Post` listener on the common NeoForge bus
- `ClassicWirelessSavedData.get(ServerLevel)` creates/loads dimension-local state; `getNonCreate(ServerLevel)` reads existing state without creating a new data object; `graph()` exposes the trusted server graph
- `SavedData` ID is `academy_wireless` in each dimension's own `DimensionDataStorage`; there is no global Level cache and no client-world entry point
- `ClassicWirelessSavedData.pos(BlockPos)` and `blockPos(Pos)` convert coordinates
- `ClassicWirelessNodeBlockEntity` implements `ImagFluxNode`; `ClassicWirelessMatrixBlockEntity` implements `ImagFluxMatrix` and overrides `isWirelessOrigin()` as `isOrigin()`
- The matrix origin remains an identifiable matrix when neighboring structure chunks unload; native capacity/bandwidth/range can be zero until it is available
- Native generation resolves the actual `ClassicSolarGenerators.IMAG_FLUX`; native reception resolves the actual `MachineDevelopers.IMAG_FLUX`
- Machine subparts are explicitly rejected even though their existing native capabilities alias the origin battery; this prevents duplicated graph endpoints/throughput
- A genuine machine origin whose other structure chunks are unloaded keeps its connection through a zero-bandwidth dormant receiver; it transfers no IF and never accesses or substitutes stored battery energy
- The resolver checks `ServerChunkCache.getChunkNow` before every block-entity read. It never calls a loading `getChunk`, adds chunk tickets, or performs offline transfer

## Public graph API

All positions are `ClassicWirelessGraph.Pos(int x,int y,int z)`. Operations are server-trusted and do not perform player ownership checks; the parent protocol must continue to enforce sender, menu token, dimension, distance, source tile identity, and ownership where required.

Mutation API:

- `boolean createNetwork(Pos matrix, String ssid, String password)`
- `void removeNetwork(Pos matrix)`
- `boolean renameNetwork(Pos matrix, String ssid)`
- `boolean changeNetworkPassword(Pos matrix, String password)`
- `boolean linkNode(Pos matrix, Pos node, String password)`
- `void unlinkNode(Pos node)`
- `boolean linkGenerator(Pos node, Pos generator, String password, boolean needAuth)`
- `boolean linkReceiver(Pos node, Pos receiver, String password, boolean needAuth)`
- `void unlinkGenerator(Pos generator)` / `void unlinkReceiver(Pos receiver)`

Readout API:

- `NetworkSnapshot networkAt(Pos matrixOrNode)` is nullable; fields are `matrix, ssid, load, capacity, buffer, nodes`
- `NodeSnapshot connectionAt(Pos node)` returns a zero-load view for an unconnected node; fields are `node, load, capacity, generators, receivers`
- `Pos nodeForGenerator(Pos)` / `Pos nodeForReceiver(Pos)` are nullable
- `boolean isNetworkEncrypted(Pos matrixOrNode)` exposes the source lock flag without revealing passwords
- `String networkPassword(Pos matrix)` is for trusted owner-only server readout, never public discovery
- `List<NetworkSnapshot> nearbyNetworks(Pos origin, double scanRange, int max)` accepts a nearby matrix OR a linked nearby node advertising the same network, then checks actual matrix reach and free capacity
- `List<NodeSnapshot> nearbyNodes(Pos origin, double scanRange, int max)` checks actual node reach and free shared user capacity. The user UI calls this with source range 20/max 100
- `tick()` performs all matrix balancing first, then each node's generator input and receiver output
- `snapshot()` / `restore(State)` expose immutable persistence state. This state includes fictional game-world passwords and is not a public discovery payload

## Faithful transfer semantics

Generator and receiver users share the same node connection capacity. The node has an entire shared input bandwidth budget and a separate entire shared output budget per tick, as in the source. Every individual generator/receiver's bandwidth is also respected. Receivers are restricted by required energy; nodes are restricted by finite native maximum storage. Each list is shuffled for fairness. One matrix's balancing budget counts the absolute IF moved on every node-buffer leg, and each node's bandwidth limits its one balancing visit.

The graph transfers actual finite stored IF. A generator's returned number is bounded by the amount actually removed from its native exposed store. A receiver causes a node debit equal to the amount actually added to its native exposed store, even if its remainder return is wrong. Valid existing native generator/receiver callbacks produce the source-equivalent result. Foreign callbacks that themselves mutate stores beyond requested bounds violate the seam contract and are not given extra power by the graph.

Every successful graph/link/name/password mutation, actual generator/receiver transfer, and node-buffer movement marks SavedData dirty. Native node/generator/receiver setters independently persist their own actual block-entity batteries.

## Explicit source-intent corrections and modern safety differences

1. Source `WirelessNet` applies the same signed delta to both buffer and node. A +10 node transfer also creates +10 buffer IF; a -10 donor transfer also destroys 10 buffer IF. The port deliberately uses `node += delta` and `buffer -= delta`, with negative delta bounded by free buffer space and positive delta bounded by actual buffer energy
2. A conservation-only sign correction with the original target `sumNodes/maxNodes` can strand real buffer energy permanently after the nodes become equally full. The port therefore uses `percent = min(1, (sumLoadedNodes + realBuffer) / sumLoadedNodeMaxima)`. No additional IF is created: every recipient increase still consumes its actual buffer debit. The deterministic recipient-before-donor fixture proves 100/0 + buffer 0 becomes50/50 + buffer 0 after two visits, rather than trapping50 IF forever
3. Source virtual block `get` may force-load chunks, including matrix and local-node references. The modern adapter never does so. Unloaded references remain persisted and resume when genuinely loaded. New links require loaded endpoints, and a matrix that is unloaded cannot offer an active link candidate
4. Legacy deferred relink cleanup can remove a lookup already reassigned to a new owner. The port unlinks/relinks atomically and uses one owner per generator, receiver, and network-node role. Repeated same-target links are idempotent rather than duplicating membership/throughput
5. Source NBT list reads assume correct types. The native codec filters missing/wrong-type coordinates, bounds the real buffer, reconstructs lookups without reading a world, and deduplicates malformed memberships. Legitimate persisted links are not rechecked against changed capacity/range on restore
6. Native discovery enumerates loaded chunk block entities and sorts x/y/z, matching LambdaLib positional scan order without scanning every block in the sphere. The network search retains the original raw-wireless-block candidate cap. User discovery applies max to actual eligible nodes. A 512-block scan guard rejects malformed oversized native calls; real source searches are 20 or node 9/12/19

The native NBT schema retains source logical tags `net/networks`, `node/list`, `matrix`, `ssid`, `password`, `buffer`, `list`, `node`, `generators`, `receivers`, and position `x/y/z`. This is not a promise that a 1.7 Minecraft world can be loaded directly by 1.21.1.

## Verification performed

Run: `.staging/wireless-energy/graph-stage/scripts/verify-graph.sh`

- Cached Minecraft 1.21.1 / NeoForge 21.1.252 / JDK 21 javac compilation of graph, native SavedData/capability adapter, common server-post adapter: passed
- `ClassicWirelessGraphRegressionTest`: 60,300 assertions passed, including 6,000 randomized whole-system conservation ticks, finite capacity/bandwidth, separate input/output limits, combined user capacity, password admission, same-SSID distinct matrices, atomic relinks/removal, discovery quirks, corrupted state, unloaded retention, no fake generator return power, dirty callbacks, and deterministic buffer redistribution
- `ClassicWirelessDataRegressionTest`: 16 actual modern NBT codec assertions passed, including fractional values, Unicode network names, passwords, immutable state, and malformed tags
- `ClassicWirelessServerLinkRegressionTest`: 16 common graph/native-adapter classes cold-linked with every Minecraft/NeoForge/project client namespace denied; no game initialization
- `ClassicWirelessDiskCodecProbe`: actual compressed NBT file written and reopened/decoded by a fresh ordinary Java JVM; passed. This is not a Minecraft/server-world restart claim
- `AcademyWirelessGraphRuntimeTests`: 3 actual native GameTest fixtures compiled against the parent's staged native classes; never launched. They cover native SavedData roundtrip and loaded removal, origin-only machine/matrix alias suppression, and unloaded reference restore/tick/snapshot without chunk creation

Remaining runtime gaps: parent must run the three native fixtures after integration, confirm common event registration is exercised by a real server END tick, and verify real `DimensionDataStorage` save/restart plus block-entity battery persistence. Parent owns full recipe-derived daylight route, native user authorization/UI, recipes/assets, matrix upgrades, and client visual parity. No native/client runtime result is claimed by this worker.
