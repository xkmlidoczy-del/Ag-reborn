# M35: keep held energy items visible while charging

Continuous Charging previously pushed the portable developer below the first-person view as its energy component changed. M35 adds a narrow re-equip policy to the portable developer and energy unit: valid IF-only changes in the same single-item slot keep the item equipped. Actual slot, item, count, unrelated NBT and component changes retain re-equip behavior. The energy unit's changing damage gauge is accepted only when both gauges derive from their corresponding IF values.

The official Minecraft 1.21.1/NeoForge 21.1.252 item renderer and item hook explain the mechanism: changed component snapshots reach an inherited identity comparison that targets held height zero. The policy does not alter IF transfer, costs, mastery, storage, geometry, textures or sound. Original Minecraft 1.7.10 renderer behavior has not been executed here; this fixes the observed modern presentation defect without claiming complete legacy animation parity.

## Verification

Full check/JAR passes (201 tasks). All372 native tests pass, including four new actual registered ItemStack/storage/component/FakePlayer-slot fixtures. The bounded official-bytecode policy harness also passes82 cases and162 read-only snapshot checks; its omitted registry initialization is a declared test boundary.

Only three production class entries differ from M32: the two item classes and the new comparison helper. Every other JAR entry is byte-identical. Native test classes are excluded from the distributable JAR.

The actual client uses a byte-exact copy of the previously earned prelearn world. Its original progression world remains preserved. The replay learns Charging through the ordinary portable GUI, edits the preset and holds the physical key for10 seconds. The closed ledger independently matches201 charging ticks:1,420→4,435IF, Charging mastery0.020099973306059837, extraCP15.698209762573242 and extraOverload15.921841621398926. Actor and host progress agree; all other inventory entries and Arc mastery remain unchanged. No new grants or NBT values are injected.

Unmodified raw frames at3,6 and9 seconds during the held input show the portable developer remaining visible. The earlier M32 continuous-charge frame lacked it. Actual hotbar3→2 switching also displays the other item and restores the developer. Two raw silent game-window clips and their scene index retain this comparison.

## Limits

The earlier earned sequence remains qualified by its material QA tools, pose, residency and accelerated native material clocks. It proves specific acquisition, learning, resource and persistence paths; complete unassisted survival progression remains open. Aiming at ordinary stone hides Charging's surrounding arcs under the original supported-block rule, including item mode. These clips do not claim visible surrounding-arc, complete original hand/shader/GPU, or audible audio acceptance.

M34 client groups, callback dispatch and context lifecycle are still isolated and are not included in this checkpoint. Full-port acceptance, remaining client transport/sync timing, broader source visual comparison, optional integration and multiplayer remain open.
