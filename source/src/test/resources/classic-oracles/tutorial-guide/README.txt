Immutable AcademyCraft 1.0.7 tutorial guide oracle
================================================

AcademyCraft source baseline: tag 1.0.7, commit
00d19ec0cf538f61c1095c9292f5ee6863db4521.
LambdaLib source baseline: tag 1.2.3.

The original source bytes, including original LF/CRLF line endings and copyright
headers, are preserved. source-manifest.json lists 20 canonical source witnesses
and three complete notices/licenses. TutorialSourceFixtures has independent
hard-coded SHA-256 pins for every witness and the manifest itself. It fails closed
for missing, changed or unlisted sources. No source is loaded from .reference.

ClassicTutorialOracle compiles these twelve original Java class bodies unchanged:
TutorialData, Conditions, Condition, ACTutorial, TutorialRegistry, ModuleTutorial,
ItemTutorial, TutorialActivatedEvent, App, AppRegistry, TickScheduler and RandUtils.
Its compiler classpath is empty. The oracle is loaded with only the platform class
loader as its parent, so the production port is not used to obtain expected state,
condition, schedule, visibility, activation, item-use or module declaration values.

The finite test boundary contains 53 tiny dependency stubs and one harness.
Every stub, its list and the harness are pinned separately by hard-coded SHA-256
witnesses. These provide only the public boundary operations called by the listed
original classes: item identity and metadata, player position/world side, event
sinks, annotations, collection operations, UI opening, declared module item fields,
resource placeholders, DataPart ownership and annotated-field copy persistence.
Guava item multimap identity is sufficient because original Item uses identity
semantics. No Forge runtime, Minecraft simulation, real network, GL renderer,
actual NBTS11n byte format or classic client binary is claimed to run here.

The original source ModuleTutorial execution registers fourteen pages, five
source-default pages, twenty-one item targets and sixty-three event conditions.
Every itemObtained target allocates craft, pickup and smelt indices, in that order.
ACTutorial's original Conditions.alwaysTrue identity check and condition OR tree
execute directly. Preview group constructor sinks record the original order;
actual recipe rendering and availability remain separate runtime/client checks.

AppRegistry retains insertion order, but LambdaLib RegistrationManager discovers
annotated classes using a HashSet. The old global app order is not asserted as an
invariant. Six explicit non-preinstalled app-order fixtures demonstrate unchanged
semantic terminal activation and condition count. The runtime's stable semantic
item mapping is tested under its declared skill_tree/freq_transmitter/media_player
ordering. This does not claim import compatibility with arbitrary classic saves
whose app condition indices followed a different discovery order.

TutorialMetadataSourceOracleTest executes original module/condition/scheduler/item
behavior and compares current metadata. TutorialStateSourceOracleTest compares all
21 targets x 3 event kinds, duplicates, unknown items, current visibility versus
stored activation, local 3/10 counters, batched page order, config latch, once-only
guide grant, transient dirty/counters, raw surplus bit positions, default/unknown
saved IDs and a deterministic 400-tick event/restart trace against original state.

TutorialPersistenceSourceOracleTest compares four original annotated field values
with real modern compressed NBT write/read and restored runtime behavior. The
original NBTS11n boundary only copies these fields and does not claim old-format
binary compatibility. Modern schema, deterministic activation-string encoding
and AC_Tutorial_Open extension are explicitly modern storage boundaries.

All test input lives on the classpath. A packaged test-only JAR can be run from an
unrelated empty directory without the private source checkout or staging tree.
A JDK is required to compile the unchanged classic source fixture. Native NBT
checks also require the official NeoForge/Minecraft dependency classpath.

No media audio, covers, song items, song content or song-specific assets are copied
here. MediaApp.scala is a source-only witness of a tutorial condition's installer
identity. Neither that witness nor the finite anonymous App boundary implements a
media player. Existing upstream non-sale restrictions remain preserved in the
complete NOTICE-upstream-port.txt and GPL/MIT license resources.
