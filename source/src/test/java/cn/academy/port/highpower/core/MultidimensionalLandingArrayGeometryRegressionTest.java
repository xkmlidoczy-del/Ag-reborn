package cn.academy.port.highpower.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** Pure closed-route boundaries plus read-only native-source guard contracts; no native world is launched. */
public final class MultidimensionalLandingArrayGeometryRegressionTest {
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        for (double source : new double[]{0, .125, 1.2, 16, 32, 48, 64, 95, 96}) {
            double flight = 96 - source;
            check(MultidimensionalLandingArrayRules.path(source, flight), "both source and physical flight consume same96m budget");
            check(!MultidimensionalLandingArrayRules.path(source, flight + .000001), "flight cannot add distance beyond whole scope");
            check(!MultidimensionalLandingArrayRules.path(source + .000001, flight), "source cannot subtract from remaining flight");
        }
        check(MultidimensionalLandingArrayRules.path(32, 1.2, 62.8), "surface pluslift plusflight included exactly");
        check(!MultidimensionalLandingArrayRules.path(32, 1.2, 62.800001), "lift is not a free distance extension");
        check(!MultidimensionalLandingArrayRules.path(0, Math.nextUp(96d))
                && !MultidimensionalLandingArrayRules.path(Math.nextUp(96d), 0), "next representable distance past96 refused");
        check(!MultidimensionalLandingArrayRules.path(-Double.MIN_VALUE, 0)
                && !MultidimensionalLandingArrayRules.path(0, -Double.MIN_VALUE), "negative lengths cannot return budget");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE}) {
            check(!MultidimensionalLandingArrayRules.path(bad, 0) && !MultidimensionalLandingArrayRules.path(0, bad), "unbounded source/flight refused");
            check(!MultidimensionalLandingArrayRules.path(0, bad, 0), "invalid intermediate lift refused");
        }
        double cone = Math.cos(Math.toRadians(12));
        check(MultidimensionalLandingArrayRules.inCone(cone, 96), "exact cone and range boundary admitted");
        check(!MultidimensionalLandingArrayRules.inCone(Math.nextDown(cone), 96), "nearest representable outside cone refused");
        check(!MultidimensionalLandingArrayRules.inCone(1, Math.nextUp(96d))
                && !MultidimensionalLandingArrayRules.inCone(1, 0), "range overshoot and zero target refused");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(!MultidimensionalLandingArrayRules.inCone(bad, 1) && !MultidimensionalLandingArrayRules.inCone(1, bad), "nonfinite cone input refused");

        String world = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/MultidimensionalLandingArrayWorld.java"));
        String clip = world.substring(world.indexOf("BlockHitResult clip(Vec3 from, Vec3 to)"), world.indexOf("LevelChunk chunk(BlockPos pos)"));
        String chunk = world.substring(world.indexOf("LevelChunk chunk(BlockPos pos)"), world.indexOf("@Override public BlockState getBlockState"));
        check(clip.contains("clip(new ClipContext") && clip.contains("ClipContext.Block.COLLIDER") && clip.contains("ClipContext.Fluid.ANY"),
                "native first blocker shape/fluid traversal remains authoritative");
        check(!clip.contains("volume(") && clip.contains("hit.getType() == HitResult.Type.MISS ? to : hit.getLocation()"),
                "unused ray tail is never preflighted after first blocker");
        check(chunk.contains("getChunkNow(") && chunk.contains("if (chunk == null) throw refuse(\"unloaded_chunk\")")
                && !world.contains(".getChunk("), "unloaded first visited cell refuses without loading");
        check(chunk.contains("world.mayInteract(player, pos)") && chunk.contains("world.isOutsideBuildHeight(pos)")
                && chunk.contains("world.getWorldBorder().isWithinBounds(pos)"), "every visited shape cell checks permission, height and border");
        check(world.contains("getCollisionShape(view, pos") && world.contains("anchor_unsupported") && world.contains("origin_blocked"),
                "native full support and full needle body collision checks are retained");
        check(world.contains("++budget.candidates > MultidimensionalLandingArrayRules.CANDIDATE_LIMIT")
                && world.contains("EntityTypeTest.forClass(Entity.class)"), "whole query counts native players, allies and miscellaneous bodies before filtering");
        check(world.contains("distance < closest") && world.contains("entity_collision_tie"), "first competing body and exact tie fail closed");
        check(world.contains("HighPowerCombat.hostileTarget(target)") && world.contains("supportedMultidimensionalLandingArrayTarget")
                && world.contains("!player.isAlliedTo(target)") && world.contains("!target.isAlliedTo(player)"), "only reviewed native hostiles gain authority");
        check(!world.contains("teleport(") && !world.contains("teleportTo(") && !world.contains("moveTo("), "source placement does not move player or target");
        check(world.contains("private Point(ServerPlayer player, BlockHitResult hit)"), "no arbitrary coordinate mark constructor");
        String pincer = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/core/SpatialPincerCorridorRules.java"));
        String displaced = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/core/DisplacedProjectionRules.java"));
        check(SpatialPincerCorridorRules.RANGE < MultidimensionalLandingArrayRules.RANGE && DisplacedProjectionRules.RANGE < MultidimensionalLandingArrayRules.RANGE, "96m scope stays isolated to NEW351");
        System.out.println("MultidimensionalLandingArrayGeometryRegressionTest: " + checks + " checks passed (pure geometry/source guards; native fixtures pending)");
    }
}
