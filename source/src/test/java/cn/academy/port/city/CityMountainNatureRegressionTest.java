package cn.academy.port.city;

import java.util.HashSet;
import java.util.Set;

/** Woodland must remain on mountain ground and clear the full trail and protected structures. */
public final class CityMountainNatureRegressionTest {
    public static void main(String[] args) {
        for (long seed : new long[]{20261103L, 42L, 0L}) {
            var trail = new CityMountainTrail(seed);
            var woodland = new CityMountainNature(seed, trail);
            int spruce = 0, birch = 0;
            Set<Integer> bandsX = new HashSet<>(), bandsZ = new HashSet<>();
            Set<Long> treeFootprints = new HashSet<>();
            for (var tree : woodland.trees()) {
                var ground = CityPlan.sample(seed, tree.x(), tree.z());
                check(ground.district() == 21 && ground.kind() == CityPlan.Kind.TERRAIN && ground.height() == tree.base(), "tree base on land");
                var trunk = woodland.at(tree.x(), tree.z());
                check(trunk != null && (tree.spruce() ? "minecraft:spruce_log" : "minecraft:birch_log").equals(trunk.block(tree.base())),
                        "grounded trunk");
                for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                    int x = tree.x() + dx, z = tree.z() + dz;
                    var sample = CityPlan.sample(seed, x, z);
                    if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3)
                        check(treeFootprints.add(((long) x << 32) ^ (z & 0xffffffffL)), "woodland trees must not overlap");
                    check(sample.kind() == CityPlan.Kind.TERRAIN && sample.district() == 21
                                    && trail.at(x, z) == null && CityDistrictLandmarks.at(seed, x, z) == null,
                            "tree must remain away from water, trails and landmarks");
                    var col = woodland.at(x, z);
                    if (col != null) for (int y = sample.height() - 3; y < sample.height(); y++)
                        check(col.block(y) == null, "tree must not overwrite the terrain below the surface");
                }
                if (tree.spruce()) spruce++; else birch++;
                bandsX.add(Math.floorDiv(tree.x() + 2000, 200)); bandsZ.add(Math.floorDiv(tree.z() + 1200, 200));
            }
            check(spruce >= 100 && birch >= 30, "woodland too sparse or missing a species: " + spruce + "," + birch);
            check(bandsX.size() >= 3 && bandsZ.size() >= 3, "woodland coverage limited to one corner");
            check(woodland.trees().equals(new CityMountainNature(seed, trail).trees()), "same seed must produce identical woodland");
            System.out.println("PASS D21 seed=" + seed + " spruce=" + spruce + " birch=" + birch
                    + " coverage=" + bandsX.size() + "x" + bandsZ.size() + " guards=" + trail.guards().size());
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
