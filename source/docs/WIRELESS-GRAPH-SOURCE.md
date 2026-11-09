# Canonical source citations

Paths below are relative to `.reference/AcademyCraft-1.0.7/src/main/java` unless stated otherwise. They cite the checked-out canonical 1.0.7 source, not a modern reconstruction.

- `cn/academy/energy/internal/NodeConn.java:98-133`: capacity/range admission, prior user-owner relink; `163-167`: inclusive squared spherical node range; `256-268`: combined generator+receiver load and node capacity
- `cn/academy/energy/internal/NodeConn.java:169-208`: loaded-only node/generator transfers; shuffled generators; shared input node bandwidth, individual generator bandwidth and available node storage; actual generator-return semantics
- `cn/academy/energy/internal/NodeConn.java:210-234`: input budget is reset for a separate receiver-output budget; shuffled receivers; stored energy, required energy and receiver bandwidth caps; receiver API returns unaccepted IF
- `cn/academy/energy/internal/NodeConn.java:61-85`: save retains unloaded user references and filters confirmed missing loaded users; `152-160`: loaded missing/empty node connection validation; `236-244`: source deferred user cleanup, motivating atomic-ownership correction
- `cn/academy/energy/internal/WirelessNet.java:28`: buffer max 2000; `57-90`: matrix, SSID, password, buffer and node persistence; unloaded node refs retained
- `cn/academy/energy/internal/WirelessNet.java:97-111`: SSID/password read and mutation; `139-164`: exact password, matrix capacity and inclusive matrix sphere admission; existing node network relink
- `cn/academy/energy/internal/WirelessNet.java:167-185`: matrix invalidation only when loaded; actual matrix reach used for candidates
- `cn/academy/energy/internal/WirelessNet.java:217-275`: matrix loaded-only balancing, shuffle, loaded-node sum/max targets, absolute matrix budget and per-node bandwidth
- `cn/academy/energy/internal/WirelessNet.java:250,263-271`: exact original loaded-node target calculation, buffer bounds and erroneous same-sign buffer/node arithmetic. The conservation sign and real-buffer target-pool corrections are explicitly documented in the port
- `cn/academy/energy/internal/WiWorldData.java:82-96`: recreating a network replaces the old one belonging to that matrix; SSIDs are not globally unique identifiers
- `cn/academy/energy/internal/WiWorldData.java:99-120`: nearby matrices or linked nodes may advertise a network; actual matrix range/free capacity are checked after candidate discovery
- `cn/academy/energy/internal/WiWorldData.java:255-258`: network balancing ticks precede node-connection ticks
- `cn/academy/energy/internal/WiWorldData.java:261-280`: `net` and `node` root persistence compounds; `283-305`: server-only create/read, noncreating lookup, source always-dirty behavior
- `cn/academy/energy/internal/WirelessSystem.java:39-50`: per-dimension server END tick using noncreating SavedData lookup
- `cn/academy/energy/internal/WirelessSystem.java:54-92`: source create/destroy/change-password/link/unlink graph operations; `96-113`: user links authenticate node password only when needAuth; `117-127`: user unlink
- `cn/academy/energy/internal/VBlocks.java:43-59`: chunk-existence guard followed by legacy force-loading `loadChunk`; `62-67`: integer coordinate persistence; typed virtual roles distinguish matrix, network node, local node, generator and receiver
- `cn/academy/energy/api/WirelessHelper.java:43-55`: active-node visual status means node linked to a matrix network; it does not mean a local generator/receiver connection exists
- `cn/academy/energy/api/WirelessHelper.java:88-108`: source user-node discovery fixed sphere 20/max 100 with node's actual range/shared capacity eligibility
- `cn/academy/energy/api/block/IWirelessGenerator.java:17-25`: provided energy must be within 0..request and bandwidth is maximum per-tick transmission
- `cn/academy/energy/api/block/IWirelessReceiver.java:15-31`: required energy, positive injection returns unaccepted IF, positive pull returns actually removed IF; receiver bandwidth follows
- `cn/academy/energy/api/block/IWirelessNode.java:15-39`: real store/max/bandwidth/capacity/range/node-name/password seam
- `.reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/util/mc/WorldUtils.java:123-180`: squared spherical range, integer bounds, x/y/z scan ordering and first-max-candidates cutoff

Native adaptation references inspected locally:

- `src/main/java/cn/academy/port/solar/ImagFluxGenerator.java` and `ClassicSolarBlockEntity.java`: existing native generator interface and actual finite solar battery extraction
- `src/main/java/cn/academy/port/solar/ClassicSolarGenerators.java`: existing native IF block capability registration
- `src/main/java/cn/academy/port/machine/ImagFluxReceiver.java` and `MachineDeveloperEnergy.java`: existing native receiver interface, real finite injection/pull and required-energy arithmetic
- `src/main/java/cn/academy/port/machine/MachineDevelopers.java`, `MachineDeveloperBlockEntity.java`, `MachineDeveloperStructure.java`: capability registration, slave-to-origin aliases, complete-structure availability and unloaded-neighbor guard
- Cached NeoForge 21.1.252 merged classes inspected with JDK 21 `javap`: `SavedData.Factory`, `DimensionDataStorage.computeIfAbsent/get`, `ServerChunkCache.getChunkNow`, `LevelChunk.getBlockEntity(...,IMMEDIATE)`, `ILevelExtension.getCapability`, `ServerTickEvent.Post`, and compressed `NbtIo` APIs
