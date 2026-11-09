# Native launch QA gate

The m05 build, 45 native world tests and two-process graceful disk restart have passed. This file describes remaining launch QA, not proof all its steps ran. The user-approved EULA was accepted for existing cloud runs; new users must accept it before startup: https://www.minecraft.net/en-us/eula. NeoForge's native GameTest server treats startup as agreement automatically.

## 1. Server-world baseline

Run `./gradlew runGameTestServer --console=plain` after agreement. The academy namespace has 45 required tests. Require a genuine 45-test execution count, all required tests passing, zero process exit status, and no mod-loading/config/data errors. A successful Gradle task with0tests is a failure of this QA stage. Investigate every failed assertion before adding broader skills.

This suite covers real server entities/world events but uses synthetic players and capture/discard network fixtures. It does not prove actual multiplayer wire timing or rendering.

## 2. Dedicated-server loading and saves

Start a separate throwaway server/world using the supported development configuration. Confirm no client-class loading, correct item/config registration, and graceful stop. Use a real connected test client for category/mastery/trained capacity/cooldown and item-component persistence. Close cleanly, restart and compare authoritative state. Test an outstanding coin escrow, active development, delayed Electron Bomb, death/respawn, logout and dimension transfer.

Temporary test worlds and logs must remain separate from existing user saves. Native harness classes are excluded from the distributedJAR; verify the packaged mod in a separate production-style instance as well as development class output.

## 3. Client environment and resources

Verify the actual available display/OpenGL/audio environment before launch. `prepareClientRun` downloads/prepares assets without starting the game and is already successful. This is not a client-startup pass. Record any verified display, rendering-library, authentication or resource limitation honestly.

Inspect mod resources/logs and all four language dictionaries. Check developer empty/half/full icon boundaries, all four induction-factor icons/tooltips, malformed/absent factor data, FE charging/extraction and finite stored capacity.

## 4. Interrupted and repeated input

- R/G/Z press, hold, release, repeated press, GUI interruption, pause, death, logout and dimension transition
- Directed Shock release at6/7/49/50ticks; prepare300ms, punch identity300ms and cleanup350ms
- Teleporter through-wall entity-first target, last-item miss return, normal item switch, empty-hand abort, critical passives and completed/failed release visuals
- Electron Bomb delayed18/3tick shot follows current aim; overlapping launches; abort/lifecycle; block occlusion and radiation mark/expiry
- Railgun exactly20tick iron charge, resource failure consuming ammo first, switched ammo, coin strict>.7 single judgement and inclusive>=.6 UI readiness
- Offhand coin toss interrupting an existing iron charge without triggering an automatic earlyQTE
- Developer130tick first acquisition and78tick L1learning, exact nominal-energy late failure, depleted/changed main hand, explicit abort and closing GUI without abort

## 5. Audiovisual parity captures

Capture canonical1.7.10and1.21.1with matching camera, GUI scale, resolution, FOV, graphics settings and comparable game timing. Reference the official1.0.7releaseJAR and expected source commit, not the1.12.2master branch. Do not bundle unlicensed media in comparison deliverables.

Compare first/third person and both hands, camera movement, bob/FOV, overlapping effects, pause/unpause, fast/fancy/transparency, fog, resource reload and other render hooks. Inspect the original40×40mscharge frames,2.5srailgun length/fade/shrink, Weak Arc10ticklife, MDorb/small-ray timing, marker dimensions/colors/bob, and exact Directed Shock curves. Listen to skill audio and its position/volume.

Known attachment, coordinate, batching/procedural geometry and API adaptations remain listed in visual reports. Do not claim pixel/audio parity from mathematical or resource checks.
