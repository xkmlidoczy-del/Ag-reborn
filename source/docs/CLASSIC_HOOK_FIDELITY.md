# Classic Magnetic Hook port

Supplied cached baseline: AcademyCraft1.0.7, expected commit `00d19ec0cf538f61c1095c9292f5ee6863db4521`, with LambdaLib1.2.3. Original witnesses are checked byte-for-byte against the supplied trees; this stage does not independently resolve the Git tag. The relevant originals are `ItemMagHook`, `EntityMagHook`, `RendererMagHook`, `Motion3D`, `Rigidbody`, `EntityAdvanced`/`EntityX`, `RenderModelItem` and `ItemModelCustom`. The recipe is declaration41 of the49 declarations in `assets/academy/recipes/default.recipe`. Originals retain their upstream headers; AcademyCraft GPLv3/additional notices and LambdaLib MIT notices remain applicable. This private development stage does not authorize redistribution.

## Gameplay and obtainability

`academy:maghook` is a stackable64 item. Source recipe41 uses five iron plates in a3×3 cross, yielding three hooks. The modern `c:plates/iron` tag already includes the obtainable `academy:reinforced_iron_plate`; source recipe06 crafts two plates from a vertical column of three iron ingots. Both main and offhand native use throw a real `academy:maghook` entity from the player's current eye position, with normalized head-look direction multiplied by2. Successful survival throws consume one; creative throws retain the stack. There is no durability, cooldown, rope, pull, automatic pickup or throw limit.

The source random bow sound is mapped to the modern arrow-shoot sound at volume0.5, with pitch `0.4/(random*0.4+0.8)`. The source landing-sound call was commented out and remains absent.

Flight bounds are0.5×0.5. Each flight tick traces the OLD velocity, subtracts0.05 from vertical velocity, then advances using that NEW velocity. There is no drag and the initial yaw/pitch remain fixed throughout flight. Ordinary collidable entities take4 damage attributed to the throwing player when that player is present in the same server world; the hook then returns an item at its old tick position. The shooter is excluded for the entire flight, including after save/load. Flying hooks are not pickable and pass through each other. Hitting an anchored hook returns the incoming hook without damaging or removing the anchored hook.

A block hit immediately marks the hook hit/pickable, records source face0–5 and host coordinates, and still finishes that tick's movement. The following tick zeros motion, expands bounds to1×1, and fixes the hook at the host block center plus0.51 along the face normal. Vertical faces retain the preceding yaw. Horizontal yaw values are north0, south180, west−90, east90; vertical pitch is down−90/up90. Any player's attack, including zero damage, retrieves an anchored hook. Environmental/nonplayer damage does not retrieve it. Merely walking into it does not retrieve it. The source only checks whether the host becomes air: replacing the host with another non-air block retains the attachment.

The actual registered entity is included in configurable `ClassicMetalTargets` defaults as `academy:maghook`, at the original list position before the iron golem. Existing server config overrides remain user-controlled. A previously generated `academy-electromaster-server.toml` that contains the old eight-entry default needs `academy:maghook` appended before earned-world testing or use of that entity as a magnetic target; do not overwrite customized lists.

## Persistence, synchronization and deliberate adaptations

Source NBT keys `isHit`, `hitSide`, `hookX`, `hookY`, `hookZ` are retained. Vanilla1.21.1 persists position/motion/rotation/UUID. SynchedEntityData carries the same packed hit/face byte and host coordinates, plus owner UUID. Owner UUID also persists as `Owner`. Both authoritative server and receiving client restore delayed anchored dimensions; renderer-side face math uses the synchronized host rather than mutable client requests.

Required modern adaptations, with explicit source evidence:

- All spawn, damage and item-return decisions are server-owned. Client use predicts success without consuming or spawning. Native vanilla entity tracking distributes transforms and synchronized state; no arbitrary client packet can change an owner, anchor or return item
- A cancelled initial entity spawn consumes nothing. The unchanged source decrements the stack even when `spawnEntityInWorld` fails
- A successful item return commits exactly once and removes the entity. Repeated attacks against a removed entity cannot duplicate a return. The unchanged source's `attackEntityFrom` permits a second direct attack after death to spawn another item; the oracle executes and confirms that quirk
- A cancelled item-return spawn preserves the hook in `ReturnPending`, retries on server ticks, and never repeats impact damage. That pending state survives save/load. The unchanged source unconditionally sets the hook dead after a failed item spawn
- Reloaded flight restores normal authoritative collision behavior and owner exclusion. The source world constructor does not register the player's collision callback, so a reloaded flying hook no longer handles impacts; the oracle confirms it. The modern item can finish its intended lifecycle after reload
- Offline or other-dimension owners are resolved only by persistent UUID in the entity's current world. The hook remains physical; an ordinary impact without a current player reference uses a native thrown damage source rather than inventing another player's attribution
- Malformed saved attachment faces and nonfinite positions/velocities are discarded; nonfinite player aim/position is rejected before a throw. Modern spectators cannot throw. No fabricated host face, infinite motion or client-side refund is accepted
- Unloaded host chunks are not treated as air, and the attachment check does not force a host chunk load. Existing entities follow native chunk persistence/unload/reload and vanilla void removal; there is no invented expiry timer. A vanilla void removal is not an item refund
- Native collision shapes replace1.7.10 block bounds while retaining source collider-only, nonfluid ray semantics. Entity selection preserves LambdaLib's0.3 AABB expansion, old-velocity trace, nearest-intercept selection and its unusual comparison of the chosen entity's POSITION distance against block-hit distance

## Source visuals and presentation

The closed `maghook.obj` and open `maghook_open.obj` are byte-identical copies from the pinned source. Each has353 positions and616 triangles. Both existing model/item textures in main are checked byte-for-byte against the source; no invented artwork is added. Resource reload installs the entire closed/open pair atomically or clears it on failure, avoiding a stale model from an older resource pack.

Flight uses the closed mesh; attachment uses the open mesh immediately upon the synchronized hit flag. The source has no additional time-based spin, opening animation or cable. Entity rendering preserves `Ry(−yaw+90)`, `Rz(pitch−90)`, then scale0.0054. Anchored render origin snaps to the exact source host face even between native interpolation ticks. Frustum culling is disabled as in the original receiving entity constructor. Native translucent entity buffers preserve source triangles, flat normals, flipped/inset UVs, packed light and overlay.

GUI uses the original2D item icon. Physical item contexts use the closed OBJ, the existing separately verified1.7.10-to1.21.1 caller bridge, and exact LambdaLib/HookRender local transforms: equipped `Rz40`, previous equipment offset, `Ry−90`; standard scale0.15, offset(0,0,1), axis flip(−1,−1,1), `Ry90`, `Rz90`, and model scale1/16. The source renderer mutates its equipment offset only inside `renderAtStdPosition`, so a fresh renderer's first equipped call uses(1,0,0); subsequent equipped calls use(0.5,0.1,0). A ground render first changes which equipped offset follows. That stateful source quirk is intentionally retained in the shared modern item-renderer instance. Modern left-hand contexts mirror the source hand basis;1.7.10 had no left hand.

All four translated names are retained: Magnetic Hook, 磁力钩, 磁力鉤 and 磁気フック.

## Verification boundaries

The isolated stage uses cached official JDK21 and NeoForge21.1.252 APIs. No Gradle invocation, game/bootstrap/server/client/UI launch, world/save mutation or user-computer action occurs in this stage.

Executed checks:

- An independent source oracle compiles and executes15 byte-identical original source files with42 old external-API/GL/world shims. Those shims provide no hook lifecycle decisions or item/entity transformation formulas. It covers2,000 source Rigidbody updates, all six faces, item use, collision/drop/retrieval, packed watchers, NBT, closed/open selection, exact GL matrices and the explicitly repaired source edge cases. Programmable ray/world shims isolate the original hook and physics behavior; this oracle is not proof of a1.7.10 game runtime or native collision-engine parity
- Native headless buffer tests execute the actual modern VertexConsumer emission on both source OBJs, six entity matrices and eight item/hand matrices, with689,950 assertions on geometry, UVs, light, overlay and normals
- Ten common/fixture classes cold-link against cached real APIs with all client namespaces denied. This is a link check, not native server bootstrap
- Seventeen compiled native GameTests exercise real recipe manager/items/entities/world collision, delayed anchoring, six faces, retrieval, host replacement/removal, four-damage attribution, cancelled-spawn/return conservation, pending-return reload, owner/logout handling, synchronized data, flying/anchored-hook collisions and configurable metal membership. They are not executed here
- Additive destinations are collision checked; each shared patch has frozen before/after SHA256. Full production snapshots record the parent-confirmed Wind fixture/QA-helper updates and are then refreshed solely for those unrelated paths. The Hook worker made no production writes; all production bytes are unchanged from that final confirmed baseline, and every Hook shared/additive guard remains exact

The owner must separately execute the `academy_hook` batch and perform actual client/earned-world/synchronization/visual/audio acceptance before claiming runtime or pixel parity. Full process restart, unload/reload, disconnected-client and rendered screenshot acceptance remain distinct from compiled NBT/sync fixtures and pure source evidence.
