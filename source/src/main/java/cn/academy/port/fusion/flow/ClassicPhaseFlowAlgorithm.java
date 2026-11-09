/* Forge1.7.10 BlockFluidClassic compatibility adaptation. Forge-derived portions retain
 * Minecraft Forge Public Licence1.0; new port adapters follow project terms. See NOTICE. */
package cn.academy.port.fusion.flow;

/**
 * Literal three-quanta BlockFluidClassic update ordering, independent of Minecraft.
 * The bridge exposes old material/displacement semantics rather than vanilla fluid rules.
 */
public final class ClassicPhaseFlowAlgorithm {
    public static final int QUANTA = 3;
    public static final int DENSITY = 1;
    public static final int TICK_DELAY = 6000 / 200;
    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1};
    public enum Material { AIR, PHASE, WATER, LAVA, PORTAL, OTHER }
    /** Null displacement means no explicit override; MAX density means not a classic fluid. */
    public record Cell(Material material, int metadata, boolean blocksMovement,
                       Boolean displacement, int density) {
        public static Cell air() { return new Cell(Material.AIR, 0, false, null, Integer.MAX_VALUE); }
        public static Cell phase(int metadata) { return new Cell(Material.PHASE, metadata, false, null, DENSITY); }
    }
    public interface World {
        Cell cell(int x, int y, int z);
        void setAir(int x, int y, int z);
        void setPhase(int x, int y, int z, int metadata, int flags);
        void schedulePhase(int x, int y, int z, int delay);
        void notifyPhaseNeighbors(int x, int y, int z);
        void dropDisplaced(int x, int y, int z);
    }
    private ClassicPhaseFlowAlgorithm() {}

    public static void tick(World world, int x, int y, int z) {
        if (!isPhase(world, x, y, z)) return; // Stale native tick must never create liquid.
        int quantaRemaining = QUANTA - world.cell(x, y, z).metadata();
        int expected = -101;
        if (quantaRemaining < QUANTA) {
            if (isPhase(world, x, y + 1, z)
                    || isPhase(world, x - 1, y + 1, z) || isPhase(world, x + 1, y + 1, z)
                    || isPhase(world, x, y + 1, z - 1) || isPhase(world, x, y + 1, z + 1)) {
                expected = QUANTA - 1;
            } else {
                int maxQuanta = -100;
                maxQuanta = largerQuanta(world, x - 1, y, z, maxQuanta);
                maxQuanta = largerQuanta(world, x + 1, y, z, maxQuanta);
                maxQuanta = largerQuanta(world, x, y, z - 1, maxQuanta);
                maxQuanta = largerQuanta(world, x, y, z + 1, maxQuanta);
                expected = maxQuanta - 1;
            }
            if (expected != quantaRemaining) {
                quantaRemaining = expected;
                if (expected <= 0) {
                    world.setAir(x, y, z);
                } else {
                    world.setPhase(x, y, z, QUANTA - expected, 3);
                    world.schedulePhase(x, y, z, TICK_DELAY);
                    world.notifyPhaseNeighbors(x, y, z);
                }
            }
        } else if (quantaRemaining >= QUANTA) {
            world.setPhase(x, y, z, 0, 2);
        }
        // Deliberately after decay, even if decay removed the current block.
        if (canDisplace(world.cell(x, y - 1, z))) {
            flowInto(world, x, y - 1, z, 1);
            return;
        }
        int flowMetadata = QUANTA - quantaRemaining + 1;
        if (flowMetadata >= QUANTA) return;
        if (isSource(world, x, y, z) || !isFlowingVertically(world, x, y, z)) {
            if (isPhase(world, x, y + 1, z)) flowMetadata = 1;
            boolean[] directions = optimalDirections(world, x, y, z);
            for (int side = 0; side < 4; side++) {
                if (directions[side]) flowInto(world, x + DX[side], y, z + DZ[side], flowMetadata);
            }
        }
    }
    public static int quanta(Cell cell) {
        if (cell.material() == Material.AIR) return 0;
        return cell.material() == Material.PHASE ? QUANTA - cell.metadata() : -1;
    }
    private static int largerQuanta(World world, int x, int y, int z, int compare) {
        int remaining = quanta(world.cell(x, y, z));
        return remaining <= 0 ? compare : Math.max(remaining, compare);
    }
    private static boolean isPhase(World world, int x, int y, int z) {
        return world.cell(x, y, z).material() == Material.PHASE;
    }
    public static boolean isSource(World world, int x, int y, int z) {
        Cell cell = world.cell(x, y, z);
        return cell.material() == Material.PHASE && cell.metadata() == 0;
    }
    public static boolean canFlowInto(Cell cell) {
        if (cell.material() == Material.AIR || cell.material() == Material.PHASE) return true;
        if (cell.displacement() != null) return cell.displacement();
        if (cell.blocksMovement() || cell.material() == Material.WATER
                || cell.material() == Material.LAVA || cell.material() == Material.PORTAL) return false;
        return cell.density() == Integer.MAX_VALUE || DENSITY > cell.density();
    }
    public static boolean canDisplace(Cell cell) {
        if (cell.material() == Material.AIR) return true;
        if (cell.material() == Material.PHASE) return false;
        if (cell.displacement() != null) return cell.displacement();
        if (cell.blocksMovement() || cell.material() == Material.PORTAL) return false;
        return cell.density() == Integer.MAX_VALUE || DENSITY > cell.density();
    }
    public static boolean isFlowingVertically(World world, int x, int y, int z) {
        return isPhase(world, x, y - 1, z)
                || isPhase(world, x, y, z) && canFlowInto(world.cell(x, y - 1, z));
    }
    private static void flowInto(World world, int x, int y, int z, int metadata) {
        if (metadata < 0) return;
        Cell cell = world.cell(x, y, z);
        if (!canDisplace(cell)) return;
        if (cell.material() != Material.AIR
                && (Boolean.TRUE.equals(cell.displacement()) || cell.density() == Integer.MAX_VALUE)) {
            world.dropDisplaced(x, y, z);
        }
        world.setPhase(x, y, z, metadata, 3);
        // Forge onBlockAdded schedules this tick implicitly; the bridge makes it explicit.
        world.schedulePhase(x, y, z, TICK_DELAY);
    }
    public static boolean[] optimalDirections(World world, int x, int y, int z) {
        int[] cost = {1000, 1000, 1000, 1000};
        for (int side = 0; side < 4; side++) {
            int x2 = x + DX[side], z2 = z + DZ[side];
            if (!canFlowInto(world.cell(x2, y, z2)) || isSource(world, x2, y, z2)) continue;
            cost[side] = canFlowInto(world.cell(x2, y - 1, z2))
                    ? 0 : calculateFlowCost(world, x2, y, z2, 1, side);
        }
        int min = Math.min(Math.min(cost[0], cost[1]), Math.min(cost[2], cost[3]));
        return new boolean[] {cost[0] == min, cost[1] == min, cost[2] == min, cost[3] == min};
    }
    private static int calculateFlowCost(World world, int x, int y, int z, int depth, int side) {
        int cost = 1000;
        for (int adjacent = 0; adjacent < 4; adjacent++) {
            if ((adjacent ^ 1) == side) continue;
            int x2 = x + DX[adjacent], z2 = z + DZ[adjacent];
            if (!canFlowInto(world.cell(x2, y, z2)) || isSource(world, x2, y, z2)) continue;
            if (canFlowInto(world.cell(x2, y - 1, z2))) return depth;
            if (depth >= 4) continue;
            cost = Math.min(cost, calculateFlowCost(world, x2, y, z2, depth + 1, adjacent));
        }
        return cost;
    }
}
