package cn.academy.port.city;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CityPlanRegressionTest {
    private static final long PLAN_SEED = 20261103L;
    private static final int STRIDE = 8;

    private CityPlanRegressionTest() {}

    public static void main(String[] args) {
        int[] samples = new int[24];
        int[] buildings = new int[24];
        Set<CityPlan.Kind> kinds = new HashSet<>();
        int changedLots = 0;
        for (int z = -CityPlan.HALF; z < CityPlan.HALF; z += STRIDE) {
            for (int x = -CityPlan.HALF; x < CityPlan.HALF; x += STRIDE) {
                var first = CityPlan.sample(PLAN_SEED, x, z);
                var other = CityPlan.sample(PLAN_SEED + 1, x, z);
                require(first.equals(CityPlan.sample(PLAN_SEED, x, z)), "sample changed after interleaved seed");
                int district = CityPlan.district(x, z);
                require(first.district() == district, "sample district does not match grid");
                samples[district]++;
                kinds.add(first.kind());
                if (first.kind() == CityPlan.Kind.BUILDING) {
                    buildings[district]++;
                    if (!first.equals(other)) changedLots++;
                    if (district != 23) checkFootprint(first);
                }
            }
        }
        int expectedDistrictSamples = (CityPlan.CELL / STRIDE) * (CityPlan.CELL / STRIDE);
        for (int d = 1; d <= 23; d++) {
            require(samples[d] == expectedDistrictSamples, "unexpected grid area for D" + d);
            if (d <= 20) {
                double coverage = (double) buildings[d] / samples[d];
                require(coverage > 0.10 && coverage < 0.55, "ordinary building coverage for D" + d + ": " + coverage);
            }
        }
        require(samples[0] == 2 * expectedDistrictSamples, "exactly two grid cells must be sea");
        require(changedLots > 1_000, "seed must affect building footprints or variation");
        require(kinds.containsAll(Set.of(CityPlan.Kind.WATER, CityPlan.Kind.TERRAIN, CityPlan.Kind.RUNWAY,
                CityPlan.Kind.LANDMARK, CityPlan.Kind.BUILDING, CityPlan.Kind.PLAZA)), "missing terrain or landmark role");
        assertSea(-1600, -1600); // NW cutout
        assertSea(1600, 1600);   // SE cutout
        assertSea(Integer.MIN_VALUE, 0);
        assertSea(0, Integer.MAX_VALUE);
        require(CityPlan.sample(PLAN_SEED, 0, 0).kind() == CityPlan.Kind.PLAZA, "D7 central plaza");
        System.out.println("PASS CityPlan: 23 districts; " + Arrays.stream(samples).sum()
                + " deterministic samples across two seeds; building sample counts " + Arrays.toString(buildings));
    }

    private static void checkFootprint(CityPlan.Sample sample) {
        require(sample.height() >= 8 && sample.height() % 4 == 0, "four block floor height");
        require(sample.bw() > 0 && sample.bd() > 0, "missing facade footprint");
        require(sample.a() >= 0 && sample.a() < sample.bw(), "facade x offset outside footprint");
        require(sample.b() >= 0 && sample.b() < sample.bd(), "facade z offset outside footprint");
    }

    private static void assertSea(int x, int z) {
        require(CityPlan.sample(PLAN_SEED, x, z).kind() == CityPlan.Kind.SEA, "expected sea at " + x + "," + z);
    }

    private static void require(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }
}
