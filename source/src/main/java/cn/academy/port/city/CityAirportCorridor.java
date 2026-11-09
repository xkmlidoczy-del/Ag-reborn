package cn.academy.port.city;

/** Small groves in reserved lots beside the airport viaduct; generation remains column-local. */
final class CityAirportCorridor {
    private CityAirportCorridor() {}

    static Column at(long seed, int x, int z, CityPlan.Sample sample) {
        if (sample.kind() != CityPlan.Kind.PARK || sample.district() < 1 || sample.district() > 20) return null;
        int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL);
        int lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
        if (lx < 12 || lx >= 788 || lz < 12 || lz >= 788) return null;
        int ru = (lx - 12) % 64, rw = (lz - 12) % 64;
        if (ru < 12 || ru >= 60 || rw < 12 || rw >= 60
                || !CityAirportTransit.overlapsLot(x - ru + 12, z - rw + 12, 48, 48)) return null;
        int cx = Math.floorDiv(x, 32) * 32 + 16, cz = Math.floorDiv(z, 32) * 32 + 16;
        int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
        if (dx > 2 || dz > 2 || CityAirportTransit.reserved(cx, cz)) return null;
        // Keep every part of the crown within the same open lot, away from roads and landmarks.
        for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) {
            var ground = CityPlan.sample(seed, cx + a, cz + b);
            if (ground.kind() != CityPlan.Kind.PARK || ground.district() != sample.district()
                    || CityDistrictLandmarks.at(seed, cx + a, cz + b) != null) return null;
        }
        return new Column(dx, dz, sample.district());
    }

    record Column(int dx, int dz, int district) {
        int top() { return 6; }
        String block(int y) {
            if (y < 0 || y > 6) return null;
            String log = district == 6 ? "minecraft:cherry_log" : district == 18 ? "minecraft:birch_log" : "minecraft:oak_log";
            String leaves = district == 6 ? "minecraft:cherry_leaves[persistent=true]"
                    : district == 18 ? "minecraft:birch_leaves[persistent=true]" : "minecraft:oak_leaves[persistent=true]";
            if (dx == 0 && dz == 0 && y <= 4) return log;
            if (y >= 3 && y <= 5 && dx + dz <= 3 || y == 6 && dx + dz <= 1) return leaves;
            return "minecraft:air";
        }
    }
}
