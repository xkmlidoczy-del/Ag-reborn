# AcademyCraft 1.0.7 native block charging source citations

Read-only audit of the canonical source under `.reference/AcademyCraft-1.0.7`. Only this audit document was written. Frozen wireless staging and production were not modified. No Gradle, game, client, network, or user-computer operation was performed.

## Conclusions

1. Native node and native receiver managers operate in double-valued IF, including fractions. They do not perform FE/RF conversion or integer rounding
2. `IFNodeManager` respects the node's bandwidth only when `ignoreBandwidth` is false. With true it still respects finite headroom/current energy, but skips the bandwidth cap
3. `IFReceiverManager` ignores the bandwidth flag entirely and delegates the full unchanged double amount to `injectEnergy` or `pullEnergy`. The stock receiver base caps capacity/current energy, and does not enforce bandwidth itself
4. The generic helper's charge return is an exact source quirk: every supported manager receives the original unchanged request, each manager's remainder is discarded, and the helper always returns the original request. A successful mutation does not change that return
5. Generic get/set/pull use only the first matching manager; support is true when any manager matches. Handler order is append/registration order
6. The original stock native node, receiver, and generator roles are disjoint. Native solar/Cat/Phase/Wind generators match neither native block-charging manager. Converter generators can separately match an enabled RF receiver or EU sink manager through their external interface
7. Real stock output converters demonstrate multi-manager overlap, but their external provider/source managers' charge methods are no-ops. This is not evidence that stock ordinary nodes/developers should receive two charging mutations

## Exact helper dispatch

Source `src/main/java/cn/academy/support/EnergyBlockHelper.java`:

- Lines 20–24: an `ArrayList` of handlers, with `register` doing an unconditional append; no deduplication
- Lines 26–30: `isSupported` returns true on the first supported handler, false if none
- Lines 33–37: `getEnergy` returns the first supported handler's result, or 0 if none
- Lines 40–45: `setEnergy` invokes the first supported handler and breaks
- Lines 48–54: `charge` calls every supported handler using the same `amt` and `ignoreBandwidth`; it neither updates the amount nor collects the returned remainder; after the loop it returns `amt`
- Lines 57–62: `pull` returns the first supported handler's actual pull, or 0 if none
- Lines 73–85: the interface documentation intends charge to return uncharged energy and pull to return removed energy. The public charge wrapper contradicts that documented remainder expectation

Therefore a helper request 1.5 that actually injects 0.25 still returns 1.5, including on a supported target. Unsupported/null targets also return the original amount without mutation, because stock support predicates use `instanceof`.

## Native node manager

Source `src/main/java/cn/academy/energy/api/IFNodeManager.java`:

- Lines 18–24: `@Registrant`; static singleton construction immediately registers the manager with `EnergyBlockHelper`
- Lines 27–30: support is exactly `tile instanceof IWirelessNode`
- Lines 32–39: get/set directly delegate node energy values
- Lines 43–51: charge computes `chg = min(amt, maxEnergy - energy)`; if bandwidth is not ignored, further clamps `chg` with `node.getBandwidth()`; adds `chg`; returns `amt - chg`
- Lines 55–63: pull computes `pull = min(energy, amt)`; if bandwidth is not ignored, further clamps with `node.getBandwidth()`; subtracts it; returns actual removed IF

No integer cast exists in these native transfer paths. No per-tick ledger exists either: the source bandwidth clamp is applied per call. Setting energy directly is not itself capacity-clamped by the manager.

Examples for a node with enough headroom and bandwidth 2:

- charge 5.5, ignoreBandwidth=false: stores 2, manager returns 3.5, public helper returns 5.5
- charge 5.5, ignoreBandwidth=true: stores 5.5, manager returns 0, public helper returns 5.5
- With only 0.25 headroom, either flag stores 0.25 and the manager returns 5.25 for a 5.5 request; the helper still returns 5.5

The original `TileNode` is a direct node store: `energy/block/TileNode.java:35` declares `IWirelessNode`, line 45 stores a double, lines129–146 expose max/energy/set/bandwidth, and lines159–170 persist energy with double NBT. `core/tile/TileInventory.java:20` adds only `IInventory`, not another wireless role.

## Native receiver manager and base

Source `src/main/java/cn/academy/energy/api/IFReceiverManager.java`:

- Lines 18–24: `@Registrant` static singleton self-registration
- Lines 27–30: support is exactly `tile instanceof IWirelessReceiver`
- Lines 32–39: generic manager getEnergy always returns 0; generic manager setEnergy does nothing, even though the receiver's own underlying store can contain energy
- Lines 43–44: charge directly returns `receiver.injectEnergy(amt)`; ignoreBandwidth is unused
- Lines 48–49: pull directly returns `receiver.pullEnergy(amt)`; ignoreBandwidth is unused

Source `src/main/java/cn/academy/core/block/TileReceiverBase.java`:

- Line 24: implements `IWirelessReceiver`
- Lines 30–38: double maximum, double bandwidth, double stored energy
- Lines 52–61: required energy is max minus current; injection accepts the minimum of request and required energy, mutates the actual store, and returns the remainder
- Lines 64–74: the tile's own energy/max/bandwidth getters expose real values; this differs from generic manager getEnergy returning0
- Lines 78–86: double NBT energy persistence
- Lines 95–98: pull removes the minimum of request and stored energy, with no bandwidth cap

Thus a valid positive fractional request 5.5 can charge 5.5 despite receiver bandwidth 2, with either ignoreBandwidth flag, if headroom permits. Receiver pull is likewise not bandwidth-limited through this manager. Wireless graph callers impose their own node/receiver bandwidth budgets; that caller-side limit is not part of block charging.

## Actual original native interface overlap

The interfaces are distinct roles:

- `energy/api/block/IWirelessNode.java:13`: extends only `IWirelessTile`
- `energy/api/block/IWirelessReceiver.java:13`: extends `IWirelessUser`
- `energy/api/block/IWirelessGenerator.java:12`: extends `IWirelessUser`
- `energy/api/block/IWirelessUser.java:9`: extends only the marker `IWirelessTile`

Sharing a marker interface does not make a receiver a generator or a node.

Original native classes:

- `energy/block/TileNode.java:35`: node only, besides inventory
- `core/block/TileReceiverBase.java:24`: receiver only, besides inventory
- `ability/block/TileDeveloper.java:39`: extends receiver base and adds `IMultiTile`/`IDeveloper`; Normal and Advanced inherit this at lines 49 and 63. `ability/develop/IDeveloper.java:13–23` does not extend a wireless role
- `crafting/block/TileMetalFormer.java:37`: extends receiver base plus sided inventory
- `crafting/block/TileImagFusor.java:40`: extends receiver base plus fluid/sided inventory
- `core/block/TileGeneratorBase.java:23`: generator only, besides inventory
- `energy/block/TileSolarGen.java:31`, `TileCatEngine.java:27`, `TilePhaseGen.java:32`, and `energy/block/wind/TileWindGenBase.java:35`: extend generator base. None declares node or receiver
- `energy/block/TileMatrix.java:35`: matrix plus multiblock, not a node/receiver/generator charging endpoint

Consequently plain solar/Cat/Phase/Wind generators and matrices are unsupported by the two native block managers. A native solar has a finite generator store, but existence of that store is not source authorization for CurrentCharging block support.

The helper has no endpoint-origin canonicalization. It passes the exact tile argument into matching managers. Original developer receiver injection/pull are inherited without a multiblock-origin override; its `updateEntity` origin gating at `ability/block/TileDeveloper.java:90–97` does not change the inherited direct injection method. Modern canonicalization of an intact body part to the origin is a separate gameplay correction, already used by this port’s FE bridge.

## Optional external support overlaps

Original `RFSupport.java:49–52` registers RF provider and receiver managers in its Init callback. `IC2Support.java:49–62` registers EU sink and source managers from an IC2-optional Init callback.

- `support/rf/TileRFOutput.java:25`: receiver base plus `IEnergyProvider`. Matches native IFReceiverManager and RFProviderManager. `RFProviderManager.java:42–44` charge is a no-op returning the input, so only the native receiver charging call mutates this stock output
- `support/ic2/TileEUOutput.java:24`: receiver base plus `IEnergySource`. Matches native IFReceiverManager and EUSourceManager. `EUSourceManager.java:40–42` charge is a no-op returning the input, so only the native receiver charging call mutates this stock output
- `support/rf/TileRFInput.java:22`: generator base plus `IEnergyReceiver`. Native IF managers do not match, but RFReceiverManager can match the external receiver interface (`RFReceiverManager.java:22–27`). This exception does not make an ordinary solar supported
- `support/ic2/TileEUInput.java:24`: generator base plus `IEnergySink`. Native IF managers do not match, but EUSinkManager can match the external sink interface (`EUSinkManager.java:20–25`)

For a hypothetical tile implementing both native node and receiver interfaces, public helper charge would issue two independent full requests, possibly twice mutating one backing store until capacity stops it. Get/set/pull would choose whichever manager registered first. There is no such node+receiver stock class in the inspected canonical source.

External RF managers have their own integer conversion quirks; they are not evidence for rounding native IF. `RFReceiverManager.java:42–44` casts its request to an int, while native IF manager methods do not. This audit is not recommending that those old RF conversion bugs be copied into new native IF charging.

## Registration and ordering evidence

- Native managers' `@Registrant` and static singleton registrations are at each manager's lines18–24
- `LambdaLib-1.2.3/src/main/java/cn/lambdalib/core/LLModContainer.java:92–94` collects Registrant classes and feeds `RegistrationManager.annotationList`
- `LambdaLib-1.2.3/src/main/java/cn/lambdalib/annoreg/core/RegistrationManager.java:27,36–46` stores the unloaded registrants in a HashSet and prepares each one; lines50–52 use initializing `Class.forName(name)`; lines140–142 load registrants before running a registration stage
- `AcademyCraft-1.0.7/src/main/java/cn/academy/core/AcademyCraft.java:85–98` invokes the PreInit registration stage, followed by Init
- `LambdaLib-1.2.3/src/main/java/cn/lambdalib/annoreg/mc/InitCallbackRegistration.java:23–30` assigns Init callbacks to INIT and invokes their static methods

The stock initialization path therefore constructs native IF managers while preparing registrants before Init callbacks append the external managers. Do not claim a fixed ordering between IFNodeManager and IFReceiverManager: their class preparation is HashSet-based and can also be affected by earlier class initialization. The helper itself has no explicit type-priority policy.

## CurrentCharging call site

`src/main/scala/cn/academy/vanilla/electromaster/skill/CurrentCharging.scala:86–105` is the source server tick listener. It raytraces the tile, treats `EnergyBlockHelper.isSupported(tile)` as the good-target test, obtains the charging speed, calls `EnergyBlockHelper.charge(tile, charge, true)`, and ignores its return. Good-target status is based on support, not acceptance/headroom; skill experience and CP consumption then continue.

The client feedback support test is at lines188–198. It does not perform a charging mutation.

## Invalid-input limits

These source managers do not guard NaN, infinity, negative requests, or corrupt stores. The receiver API contract requires positive injection/pull (`IWirelessReceiver.java:17–26`). A modern port may sanitize invalid inputs explicitly for safety; it must not present those guards as exact original arithmetic. Valid positive finite fractional IF is already represented without rounding in the source.

## Developer slave-local energy path: explicit modernization boundary

The source helper really does allow developer slave tiles. This is confirmed by the construction and inherited methods, not merely inferred from an interface name:

- `ability/block/BlockDeveloper.java:25` extends the shared multiblock implementation; lines 39–45 add the seven slave cells
- `ability/block/BlockDeveloper.java:77–80` creates a Normal or Advanced `TileDeveloper` for any metadata, with no slave-specific nonreceiver tile type
- `.reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/multiblock/BlockMulti.java:189–201` places the same block at each occupied slave coordinate and assigns a nonzero subID to that newly created tile
- `ability/block/TileDeveloper.java:39,85–87` inherits the receiver base and initializes each instance with its own receiver capacity/bandwidth/store
- `energy/api/IFReceiverManager.java:28–29` tests only `instanceof IWirelessReceiver`; it does not require subID 0, an origin, or complete structure
- `core/block/TileReceiverBase.java:57–61,95–98` modifies that instance's energy field; it has no origin, completeness, canReceive, chunk, or removal guard
- The full `TileDeveloper.java` declares no `injectEnergy` or `pullEnergy` override. Lines90–97 gate ticking to subID 0, but do not override direct charging
- `ability/block/TileDeveloper.java:113–119` explicitly redirects normal slave use to the origin; lines130–134 resolve the origin. Lines160–165 perform the developer's atomic energy check/pull using that chosen instance's own energy

Result: raytraced charging at a source slave is supported and stores IF in a separate slave-local field. Ordinary use is redirected to the origin, whose energy is different, so this charged slave-local energy does not power the normal development route. This is a nonfunctional slave-local energy-path bug for ordinary gameplay; the tile is not literally unreachable or unchargeable by source APIs. The source receiver's super NBT methods also persist that local field.

The final modern adapter preserves this port’s existing body-part alias: each clicked intact machine part resolves to the live canonical native origin, with no second FE transfer and no mutation of the unusable slave-local field. Removed/incomplete structures remain unsupported. This is an intentional cleanup of the source slave-local storage bug, preserving the existing playable FE-origin contract with fractional native IF. It is not exact original slave-local storage parity; disconnected targets also differ from original support/feedback/experience.

## CurrentCharging feedback, ordering, and rejected-input examples

Source `src/main/scala/cn/academy/vanilla/electromaster/skill/CurrentCharging.scala`:

- Lines53–56: charging speed is float lerp 15→35, good-target experience increment is 0.0001 versus 0.00003 for other targets, CP consumption is lerp 3→7, overload lerp 65→48
- Lines91–100: raytrace the block, set good=true when the helper supports it, call charge with ignoreBandwidth=true. There is no result assignment or accepted-energy comparison
- Lines104–108: apply experience based on good, then consume CP and end the effect if consumption fails
- Lines194–198: client good-target feedback uses helper support as well

A full original supported node/receiver therefore remains a good target even when it accepts zero IF. The ignored generic charge return is not used to calculate experience or refund CP. The server charging mutation/experience application precedes the CP-consumption check in this original routine.

Invalid inputs are different from valid fractional IF:

- Node charge with a negative amount subtracts energy and can drive it below zero; node pull with a negative amount adds energy and can exceed capacity (`IFNodeManager.java:43–63`)
- Receiver injection with a negative amount subtracts energy; receiver pull with a negative amount adds energy (`TileReceiverBase.java:57–61,95–98`)
- NaN propagates through Java Math.min into the store; neither manager has a finite-value guard
- A positive-infinity charge into a finite valid node/receiver accepts its finite remaining headroom and leaves an infinite remainder; no explicit nonfinite rejection exists
- Already corrupt overfull/negative stores are not sanitized by these source managers; direct node set accepts the passed value (`IFNodeManager.java:38–39`, `TileNode.java:140–141`)

Rejecting negative/nonfinite requests and sanitizing corrupt stores in a modern adapter is intentional safety hardening. It avoids accidental discharge, overfill, and NaN poisoning; it should be documented separately from faithful positive finite fractional transfer behavior and the helper's original always-input charge return.


## Staged modern-helper adaptation declaration

The staged helper intentionally corrects the legacy aggregate-return and overlapping-dispatch bugs: it selects one applicable actual native endpoint/manager, transfers only once, and reports the actual remaining request. This preserves the original ordinary native node/receiver's positive finite fractional IF mutation and ignoreBandwidth arithmetic. It intentionally differs from `EnergyBlockHelper.charge`'s always-original-input return and hypothetical double-mutating overlapping handlers.

For Current Charging, the original caller ignores charge's return, so correcting the remainder does not itself alter that source call-site rule. Support-based feedback/experience and source call ordering are separate from the helper return. Do not describe a new accepted-energy-based good-target rule, or pre-charge CP check, as exact original behavior if the port chooses one.

The final modern endpoint policy preserves the port’s existing body-part alias: any intact clicked developer part is canonicalized to the live finite native origin. There is no slave-local mutation or second FE transfer. Incomplete, removed or unloaded structures remain unsupported. This repairs the source’s nonfunctional slave-local-energy path without regressing the existing playable FE-origin redirect contract.

The modern helper rejects nonpositive/nonfinite requests and uses existing finite native stores. This is explicit hostile-input safety hardening: original managers lack such guards and can turn negative pulls into charging or poison stores with NaN. Ordinary positive finite node/receiver IF transfers preserve source mutation arithmetic. Plain native generators remain unsupported; this is separate from external RF/EU input-converter support.

## Canonical source SHA-256

The hashes below identify the exact local canonical inputs used for this audit. No network source was consulted and no source file was changed.

```text
ee9c56d75c9120bab8064e0c82dd749ce25dd493f8d497e6e4e9db8c94ae5662  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/EnergyBlockHelper.java
eafa0b2fcbbceca4a4881184064dc39d5746f3223f0bb8212117793da0aab05d  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/energy/api/IFNodeManager.java
0f92cffc097013eca234458ed60865198124d4d23ed858be56c8b8d06cff56f3  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/energy/api/IFReceiverManager.java
d5a9be95e8dc141c2445242a9dafb02072bd04a9dbc6a0b3fbe9b44052a28fcc  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/energy/api/block/IWirelessNode.java
24db52572f29504f62ee0923481b506bbbc3348caa60421c73ca192fbbef49a8  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/energy/api/block/IWirelessReceiver.java
9c6f947ddca0f1ac241be0eb69c7fea9c8110f93ebc2756df1ae5659ab9d5a9f  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/energy/api/block/IWirelessGenerator.java
bc4f849fd6cafcb9ffa042235e96f775522e1a8fe92adb5f7c0543cabceeb61d  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/energy/api/block/IWirelessUser.java
3579e9af020d787fe885e761bc990f12f0a729122fc45349db16958ac033f35d  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/core/block/TileReceiverBase.java
21669f5fdb7684452fec12cb8e0384731b543ff8bda27c6d22fbacc122c6207a  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/core/block/TileGeneratorBase.java
748835f0402b366cc000bc64193245eeeb50b7047dea65d7430401c9e4df245e  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/core/tile/TileInventory.java
be131b1321ebb29c40521dc5dc7f8c55ce7b4428d93c0c79893aee45376cab5f  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/energy/block/TileNode.java
f13de82757ff343c766e126d32ed238e4414e66fad53f3c05aedee38e8ce2d09  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/ability/block/TileDeveloper.java
2b22eb3e635906973e09b5120e2622eb3648d7f37d1004c83339a50eafc3febf  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/ability/block/BlockDeveloper.java
0c78bdd2978ba5b0cd1e0a3210ce77a9b8eb33cc36512a46a6381c380380e757  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/ability/develop/IDeveloper.java
9bef9ca9a77a566773e23fc497a05f440d83f591bc2a642c869a1e96488d5069  .reference/AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/electromaster/skill/CurrentCharging.scala
cf9d6f11afe77341868815c44f04a8c02869d9f9ff5a35edb031f313b92c4441  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/rf/RFSupport.java
1aa2e7b46a6194180645ad54cadcef67717e6047574042249c7481d9816c7f82  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/rf/RFProviderManager.java
1cd83fbd7b73549f15e3bf5023d96a16d13b658eee51d51994891f2ce6adfac8  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/rf/RFReceiverManager.java
6b6339a49828fcccbd49772626c9594946ff6811a6895daa3845c18f14147a05  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/rf/TileRFInput.java
3fcb846068f76a8f12ac0f62bd6e1368b8747bea3f48bd8707606e443421cd92  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/rf/TileRFOutput.java
b6c9568208d138d0927273ab9643fff2ba619ddb16ddfd5cc56ade417af01a48  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/ic2/IC2Support.java
4cd17f6431c07012a583203c178ec34b373ac75b8e4668b908b9e587e806011f  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/ic2/EUSinkManager.java
317f72bbf9e0d8c3ca6b00414136159dc8739b3e3ac8e67b0c81985dc31bb42b  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/ic2/EUSourceManager.java
a93826d8778c6348fce618830d7f6e0ca3ba398bc821b6e4f385fbd6ef024768  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/ic2/TileEUInput.java
39d5cce018270ac006a7ec3cd3deaed243accc3b990bc32f261b8ce3be9bd3d5  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/support/ic2/TileEUOutput.java
180d98a394db74b49c1aec4bbb82389eddf22ccabea268d17678c854211a250e  .reference/AcademyCraft-1.0.7/src/main/java/cn/academy/core/AcademyCraft.java
606f12506897ffdc5dcfa0fdd675418592f306b31dc14b8ad6d7448377bd66f5  .reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/annoreg/core/RegistrationManager.java
16e6a3c73237f23449e1127d2a3ec6d90eb8634f6279f1c440ea1142a3c0ffe1  .reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/core/LLModContainer.java
d4fb2c57d535a9e17bd16a2ecdf35126f99da7a7ec0af2fe1a638e4b28cfc178  .reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/annoreg/mc/InitCallbackRegistration.java
00b4580b85cbec7673a178d7dfe7fb5f2e81a4d7bbba0e6ea60766bd20824f99  .reference/LambdaLib-1.2.3/src/main/java/cn/lambdalib/multiblock/BlockMulti.java
```
