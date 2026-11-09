# Classic Phase Generator source witness

The portable oracle compiles and executes the full, unchanged AcademyCraft 1.0.7 `TilePhaseGen`, `TileGeneratorBase`, `TileInventory`, `IFItemManager`, and `IFConstants`. They retain their original packages in an isolated class loader. Fifteen original files are pinned with SHA-256 hashes in the test; all original bytes are included in test resources. Minimal old Minecraft/Forge/LambdaLib API stubs provide inventory, NBT, fluid tank, item tags, and network capture. No client or native library is started. The port's buffer never supplies the expected original arithmetic.

## Source behavior preserved

- Tank 8000 mB; matter unit 1000 mB; generation consumes at most 100 mB/tick at 0.5 IF/mB; IF buffer 6000
- **Bandwidth is 50 IF/tick**, from `IFConstants.LATENCY_MK1 = 50`; the original Phase Generator does not use 500
- Fuel drain truncates `required / 0.5` to an integer: a remaining headroom below 0.5 IF leaves fuel untouched
- Server update order is generation, optional 10-tick tank snapshot, acquisition of one matter unit, then item charging; the base 20-tick energy snapshot also precedes charging
- Acquisition requires strictly more than 1000 mB of free tank space, after that tick's generation; exactly 1000 free space blocks it
- Acquisition requires the return slot to be absent or an empty matter unit stack below its original maximum of 16; a manually inserted filled phase unit blocks acquisition
- Original IF item manager returns the amount not accepted; the modern buffer callback returns the amount actually accepted, so the adapter translates the two conventions
- Original `getProvidedEnergy` returns and debits actual available IF without an internal bandwidth clamp; the wireless graph caller applies bandwidth before requesting
- Liquid fill accepts the original phase fluid from every side; the tank is not consumed on a client tick
- Recipe is two rows: `crystal0 frame crystal0` / `matter_unit empty matter_unit`
- Original render selects five `ip_gen` textures by clamping `round(4 * liquid / 8000)`; GUI contains IF buffer, mB tank and wireless user page

## Original defects and modern adaptation boundaries

- `TileGeneratorBase` never saves its private IF energy to NBT. Original tank and inventory survive reload but earned energy becomes zero. The modern explicit load seam deliberately conserves finite fractional IF. The portable test verifies that seam; actual modern block entity NBT wiring still needs its own integration check
- Original negative wireless requests return negative IF and increase generator energy. The modern adapter rejects invalid and negative requests
- Original fluid-stack drain ignores requested fluid identity even though `canDrain` rejects a wrong fluid. This is documented as an old API defect; it is not a reason to add a wrong-fluid acceptance path to modern capabilities
- Original zero-count phase input stack can produce an empty return unit without filling the tank. Modern empty-stack handling avoids this malformed inventory bug
- Original menu puts the phase-filled material filter on the return slot. This means manual filled-unit insertion is permitted even though acquisition expects empty return units; the intended preservation choice belongs in the actual modern menu
- Original player transfer group is `[4,40)` despite three machine slots and 36 player slots starting at index 3. `CleanContainer` confirms an exclusive range; modern safe `[3,39)` mapping corrects the original out-of-range/first-slot omission
- `TileInventory.isItemValidForSlot` returns true for every slot and implements unsided `IInventory`; source menu filters do not establish sided automation restrictions

## Test depth and result

The verified run passed 243,977 assertions. It covered a matrix of fractional-energy and fuel boundaries, original battery capacities and bandwidths, all strict acquisition boundaries, filled/full return slot gates, 80 deterministic traces of 1000 operations each, simulated fluid/IF operations, actual wireless withdrawal, explicit save/load seams, corrupt receiver reports, signed native menu words, and all 8001 liquid texture thresholds. It also proved the source pre-charge energy snapshot and pinned the actual matter item maximum.

Run from the repository root after merging this additive `files` directory into the parent stage's `files` directory:

```sh
bash .staging/phase-generator/files/scripts/verify-phase-generator-source.sh .staging/phase-generator/files
```

Before merging, run against the separate adapter and oracle directories:

```sh
bash .staging/phase-generator/oracle-worker/files/scripts/verify-phase-generator-source.sh \
  .staging/phase-generator/files .staging/phase-generator/oracle-worker/files
```

This validates portable arithmetic and unchanged original behavior. It does not claim a NeoForge build, renderer execution, native GameTest, or runtime client result.
