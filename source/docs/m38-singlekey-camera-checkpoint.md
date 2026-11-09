# M38 authenticated single-key and ThunderBolt camera checkpoint

This development checkpoint jointly integrates the reviewed M37 six-family input/owner metadata and the narrow M38 ThunderBolt camera conversion. It includes M36's source coordinator oracle and M35's held energy-item presentation fix. The full classic port remains under acceptance.

CurrentCharging, MagMovement, MagManip, GroundShock, DirectedShock and ThreateningTeleport now capture positive physical input, actual actor and server-issued owner epoch. Accepted terminals echo the captured token; pending terminals retain fixed skill/input/owner identity after remapping. Official respawn reuses actor ID and connection, so the actual owner object and issued epoch are checked. A bounded six-family pending watermark prevents a canceled, not-yet-started input from reviving; this is explicit modern protection, separate from original ordered transport.

ThunderBolt subtracts the double camera position before narrowing world coordinates to float. Its geometry, RNG, attributes, timing, resources and audio generation remain protected. In the actual current-main CPU oracle, 48 third-person host cases compare 58,176 coordinates, retain 4,848 byte-identical near vertices and verify class/resource origins. The earlier renderer produced 37,249 far-coordinate mismatches; the promoted renderer has none above .00001. This does not establish original-engine, first-person, GPU, shader, animation or audio parity.

## Passed checks

- Final full check/JAR:209 tasks,2m16s, successful
- Actual native GameTest:386/386 required tests passed,46.48s, with normal shutdown
- Six new native cases use actual PlayerList/Level admission, actual outgoing identities and official respawn. Explicit skills/items/maxima are fixture setup; direct requestFromClient calls do not prove sockets or physical client input
- Final isolated M37 ingress:1,934 assertions/303 scenarios; original source callback binding554 and new identity binding186 retained; protocol11,897 and owner epoch184 checks.168 skill sections and164 common method bodies remain exact

The first joint check stopped on a strict whole-archive guard: ModDev's slim and merged official archives have different container hashes but identical Vec3/PoseStack/VertexConsumer class bytes. The Gradle task now binds its already pinned standard merged build artifact first. No guard or production source was weakened.

The first native run had one new fixture-count failure. A second legitimate player received two neighboring actor start broadcasts. Its own actor/UUID start count was zero, its hold was inactive, epoch unchanged and full resource NBT unchanged. The fixture now checks its exact actor; production behavior was preserved. Both original failure logs remain.

## Actual client evidence so far

Pinned M36 packaged-JAR baseline at X≈14,502,577 used explicit command-assisted category/level4/learn/fullCP, teleport, stone target and daylight setup. Two actual K casts saved ThunderBolt XP .006000000052154064. The first-person raw frame shows stepped collapsed ribbons, consistent with the CPU precision defect. A first wrong-preset scene remains marked inconclusive. The original worlds were byte-exact cloned and preserved.

M38 packaged-client visual/earned-Charging comparison is prepared and pending. The user-requested cloud browser has a coordinated focus window; no game or recording is currently running. Earlier M36 earned Charging and actual WaveFileWriter/source-loop mix evidence remain valid for their specific checkpoint, without claiming M38 live acceptance or human listening.

## Remaining full-port acceptance

35 active paths,50 catalog skills,15 passive paths,49 classic plus4 RF recipes,56 achievements,14 guide entries and54 fields are integrated scope counts. Full unassisted material/world-generation/developer/level progression, broader hand/player action and shader comparisons, other world-coordinate render adapters, original partitioned CP/ability synchronization, audible full SFX, genuine optional integration and external multiplayer remain open. The original PlasmaBodyEffect also narrows world floats early; changing that behavior requires an explicit fidelity-versus-improvement distinction.

Tested installation: Minecraft1.21.1,NeoForge21.1.252,Java21. This JAR has no additional runtime mod dependency in the tested installation. Cloud consumer testing uses official forgeclientdev with only the packaged AcademyCraft JAR, not a Windows retail installation.
