# Charging native entry companion

This proof is separate from the unchanged historical Charging geometry runner and
its protected-source admission. It does not change that runner, its 84-scene
fixture, production Charging code, or the original M41 source.

## Six native tests

`ChargingCompanionRuntimeTests` registers only under
`academy_charging_companion`. It uses actual admitted `ServerPlayer` instances,
the production `requestFromClient` handler, production accepted input/epoch/token
payloads, actual native IF storage, and the native event bus.

1. **Positive enrolled input and durable quota:** a real mapped press pays one
   transfer; exact CP, overload, both capacity-growth ledgers, XP, and recovery
   delays are checked. Wrong token/epoch/input and legacy bypasses are rejected.
   Release and retired-input replay are inert; fresh same-tick repress cannot
   produce another transfer. One genuine later world tick permits one transfer.
2. **Accepted-energy partial fill:** one IF of real headroom costs float-compatible
   0.2 CP, pays the first strain once, awards XP once, and automatically ends the
   hold. Full-target retry cannot pay, train, or reopen the quota.
3. **Callback cancellation/reentry:** actual positive input establishes ownership.
   Wire ingress and exact-token terminals remain rejected while the cost callback
   is busy. A trusted server lifecycle release then cancels before payment,
   native energy write, quota claim, or XP. Nested tick/start cannot revive it.
4. **Ordinary lifecycle before/after payment:** accepted abort, deactivation,
   dimension-event cancellation, logout, and state replacement remove the hold,
   preserve exact paid ledgers and quota, and make later tick/terminal calls inert.
5. **Death and official clone:** death retains the existing CP recovery,
   overload-reset and deactivation policy while preserving earned training, XP,
   and output quota. Actual `PlayerList.respawn` preserves nondeath clone ledgers
   and quota without copying the live hold; old-owner traffic is rejected.
6. **Unmodified M41/current native entry differential:** fourteen scenarios compare
   item novice/master/full/unsupported/exhausted/replacement, creative classic,
   block supported/exhausted/full/missed, and callback release/tick/start traces.
   Each lane records actual start/terminal results, native energy, exact ledgers,
   cost/XP events, active state, duplicate ticks, and output payload counts.

The original callback-release quirk is retained in the classic comparison: a
release inside the already-running classic cost callback can remove the hold
while that tick still finishes its debit, transfer, and XP award. The enrolled
transaction revalidates ownership after mutable callbacks and commits nothing
after trusted cancellation. These are deliberately different expectations.

## Original-source and current-class admission

`manifest.json` identifies the unchanged ZIP member and the exact review copy at
`original/CurrentCharging.java.txt`:

- M41 ZIP: `8dd1dc1345cb687347d6a89abae5f30587a3dbd97a3dd31c7377da4e361c602b`
- CurrentCharging member: `ea6c88aaa8a6abe6388c93053aa4315d0e839d94ad6e73c5f4278c5a1aaa108d`

`scripts/prepare-charging-m41-entry.py` verifies both hashes and byte equality,
then compiles only this unchanged original source against the current native
compile classpath. Exactly `CurrentCharging` and `CurrentCharging$Hold` may be
emitted. It snapshots the actual current compileJava Charging/ledger/identity
class families; it never recompiles or replaces the current subject.

At native runtime, `ChargingM41EntryFixture` binds only those two original
classes in a separate loader. All native APIs, storage, ledgers, identity,
networking, and the actual `HighPowerCharging` remain the shared current
production implementations. Current class-resource bytes must match the staged
compileJava hashes; original CodeSource/resource identity and before/after
input stability are checked. Missing staging fails the test rather than skipping.

This is **entry-path equivalence with shared current native dependencies**, not
whole-M41 equivalence. The separate `charging-core` proof executes original M41
core ledgers/session code against actual current compiled core classes. The
enhanced native tests assert their own authorized behavior, rather than expecting
it to match classic quirks. Neither native proof establishes sockets, request
codec behavior, real client input, rendering, or historical full-artifact identity.

## Run contract

The Gradle tasks `prepareChargingM41Entry` and `runChargingCompanionGameTest`
provide staging and the isolated native run. The combined
`currentChargingCompleteProof` also requires the core and geometry companions.

For explicit staging after `compileJava` and `writeVfx2NativeClasspath`:

```sh
python3 scripts/prepare-charging-m41-entry.py \
  --original-zip /absolute/path/academycraft-m41-meltdowner-camera-source.zip \
  --classpath-file build/vfx2/native-classpath.txt \
  --main-classes build/classes/java/main \
  --output build/current-geometry-proof/charging-entry \
  --javac /absolute/path/to/jdk-21/bin/javac
```

The native JVM must receive
`-Dacademy.charging.companion.baseline=/absolute/project/build/current-geometry-proof/charging-entry`.
This path contains `classes`, `source`, and `provenance.properties`.

The first verified native run on 2026-10-11 passed all six required tests,
including fourteen original/current entry scenarios. Native test support and its
in-memory template reside under the existing jar-excluded `gametest` package;
there is no new gameplay command or shipped test endpoint.
