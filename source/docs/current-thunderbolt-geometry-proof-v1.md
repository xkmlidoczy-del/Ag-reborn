# Current ThunderBolt CPU geometry contract v1

This companion proves a bounded **current .54 compileJava execution**. It does not
replace, relax, or pass the historical `classicThunderBoltGeometryTest` archive
admission. No production source, production artifact, old test, Gradle runner,
fixture, expected hash, or original output digest is changed by this proof.

## Run

After `compileJava` and `processResources`, use a new report directory:

```sh
python scripts/verify-current-thunderbolt-geometry.py --report build/reports/current-thunderbolt-proof/example-new-run
```

Optional `--project`, `--original-source`, `--java-home`, and `--cache-dir` change
locations, not expected identities. The default locations match this restored
workspace. The contract pins Temurin 21.0.12.1+1's launcher, compiler, module image,
VM library, and release descriptor. Each JVM is limited to 256 MiB. The script
never invokes Gradle, a native client, or production compilation. Only the frozen
view host/harness and the new proof/mutation drivers are compiled in temporary
directories. An existing report directory is rejected; historical results remain
available and a failed rerun cannot leave a previous PASS marker looking current.

## Independent anchors and current-only bounds

* The unchanged API audit verifies original M41 source ZIP
  `8dd1dc1345cb687347d6a89abae5f30587a3dbd97a3dd31c7377da4e361c602b`.
  Original test sources bind all three fixture indexes and their complete files.
  All eleven original API class fingerprints and original whole-JOML digest are
  retained. No historical API fingerprint is learned from the current cache.
* ThunderBolt's original protected methods, candidate camera-relative methods,
  geometry/timeline sources, source and processed assets, frozen harness and
  null-player/options Minecraft view host remain byte-identical. The host is
  the one explicit historical shadow of Minecraft; it is not a production class.
* `contract-v1.json` separately bounds the ten actual production ClassResources
  and their explicit constant-pool production reference graph. Their current
  `.54` fingerprints do not assert a new historical binary provenance. The two
  pre-existing outgoing references, `ClassicRenderTypes` and `ClassicRaytrace`,
  belong to unexecuted event/packet paths. Attempting to load either outside the
  admitted ten-class CPU slice fails, as the executable negative control shows.
* Every allowed additional host class is named individually with its archive,
  selected class member, and current repeatability fingerprint. The contract has
  observed loaded closure, rather than a whole-JAR inventory. It has
  1,098 class entries: ten production classes, eleven historically anchored API
  classes, and 1,077 additional current host classes. It also names eleven host
  service/configuration resources and two known absent optional logging classes.
  The 24 archive origins cover Minecraft/NeoForge linkage, codec/container
  dependencies, JOML, and logging/service discovery. Each dependency is explicit
  in the JSON; a whole current Minecraft archive hash is **not** substituted for
  the missing original archive hash.
* The host-class fingerprints establish repeatable execution of this current
  contract only. Original M41 fingerprints are unavailable for those additional
  transitive classes/resources. Their historical behavior and other methods are
  unproven. Two Log4j classes use their actual Java-9 multi-release members, rather
  than incorrectly defining their base entries on Java 21. The positive run
  must define exactly this declared class set plus the two frozen fixture classes;
  an unused declared entry or an undeclared definition fails. Normal verification
  has no discovery, auto-regeneration, or fingerprint-refresh option.

The historical archive expectation remains
`59a14efc6e5c163fe7e1374b3cf66ace00dd20f1b847ae18dd5a08aee186d0c7`.
The currently observed merged archive is
`87794ad77a8887b2eec81450c89637cf9db3c64ce5a11310f926af74fa07da59`.
The old admission therefore still fails. This companion records that failure
alongside its independently named `PASS_CURRENT_CONTRACT_ONLY` result. Matching
the eleven API classes does not make the two whole archives interchangeable.

## Execution and negative controls

The loader has only the JDK platform parent. It defines admitted class bytes with
the real compileJava directory or archive CodeSource and actual ClassResource
URL. It checks exact render/vertex/Arc-constructor signatures before invoking the
unchanged harness. Unexpected classes, duplicate admissions, wrong origins,
changed bytes, ambiguous signatures, and changed source/fixture inputs fail.
The positive renderer is read from `build/classes/java/main`; no renderer source
snapshot or rebuilt renderer is substituted. All read inputs are rehashed before
success is published. Eleven original API classes also receive real load/origin
checks, even when some are definition-only within this scene matrix.

The unchanged 48-scene matrix emits 4,848 near vertices and compares 58,176
coordinate values at near and far cameras, with 175,728 state/attribute checks.
The final near mesh/UV/color/alpha serialization must retain the original SHA-256:

`56d71d97bfb8b4a0203821f24edb2068620944a1d27906274e3aa5fb4f7394e9`

Every run must execute and detect all eleven negative controls:

1. Actual renderer x/z instruction mutation: frozen near digest fails
2. Camera subtraction changed to addition: frozen far-origin comparisons fail
3. Actual renderer UV-load mutation: frozen near digest fails
4. Extra render-time effect RNG draw: frozen RNG preservation assertion fails
5. Stale renderer bytes with the original digest: class-byte admission fails
6. Unapproved renderer origin with matching declared digest: origin admission fails
7. Incorrect class fingerprint: pin admission fails
8. Duplicate subject entry: duplicate admission fails
9. Undeclared existing production dependency: closed loader refuses definition
10. Undeclared existing host dependency: closed loader refuses definition
11. Exact Vec3 bytes copied to an unapproved origin: origin admission fails

The four behavioral mutants transform a temporary copy of actual compileJava
bytecode using the separately pinned ASM test utility. They execute the real
renderer methods through the unchanged harness. They cannot use a source-hash
rejection as evidence of behavioral sensitivity. The stale-byte fixture is
separate from production and retains the original expected class digest. Every
negative records its specific required failure, rather than accepting any error.
The real production classes remain untouched throughout.

## Observed validation and limits

The validated actual run reports zero coordinate mismatches and maximum error
`2.384185791015625E-7`; all eleven controls are detected. A full report contains
`evidence.json`, individual execution logs, actual near bytes, loaded-class
origin/hash/resource receipts, original API identity output, and input receipts.

This is third-person, null-player/options, CPU geometry evidence for the frozen
matrix. Defining a class is not proof that all of its methods executed. There is
no first-person, event-dispatch, game/world/bootstrap, GPU, native-client, sound,
full source-engine visual parity, or whole-project dependency-closure claim.
Historical archive admission remains a separately failing requirement.
