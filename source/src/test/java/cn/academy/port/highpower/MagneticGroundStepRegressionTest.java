package cn.academy.port.highpower;

import cn.academy.port.highpower.core.MagneticGroundStepRules;
import cn.academy.port.highpower.core.MagneticGroundStepRules.Vector;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Pure bounded motion and continuous-cost checks, independent of native geometry acceptance. */
public final class MagneticGroundStepRegressionTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static double length(Vector v) { return Math.hypot(v.horizontalLength(), v.y()); }
    public static void main(String[] args) throws Exception {
        for(boolean enabled:new boolean[]{false,true}){
            var first=cn.academy.port.highpower.core.MagneticGroundStepAdmission.migrate(enabled,0);
            check(first.enabled()&&first.version()==1&&first.changed(),"old unmarked config graduates exactly once, including generated false");
            check(!cn.academy.port.highpower.core.MagneticGroundStepAdmission.migrate(first.enabled(),first.version()).changed(),"reloading the migrated config is idempotent");
            for(int version:new int[]{1,2,Integer.MAX_VALUE}){
                var retained=cn.academy.port.highpower.core.MagneticGroundStepAdmission.migrate(enabled,version);
                check(retained.enabled()==enabled&&retained.version()==version&&!retained.changed(),"marked operator disable/enable and future versions are preserved");
            }
        }
        check(MagneticGroundStepRules.RANGE == cn.academy.port.core.SkillTuning.number("magnetic_ground_step", "base_distance") && MagneticGroundStepRules.MAX_CONTROLLERS == 1, "table-backed single-controller bound");
        check(MagneticGroundStepRules.cpCost(new BigDecimal("3")) == 8, "provisional LV3 cost");
        check(MagneticGroundStepRules.cpCost(new BigDecimal("4")) == 4, "provisional LV4 cost");
        check(MagneticGroundStepRules.cpCost(new BigDecimal("5")) == 1, "provisional LV5 cost");
        double last = 8;
        for (int i = 1; i <= 200; i++) {
            BigDecimal q = new BigDecimal("3").add(BigDecimal.valueOf(i).divide(new BigDecimal("100")));
            double cost = MagneticGroundStepRules.cpCost(q);
            check(cost < last && cost >= 1, "cost decreases inside levels without integer stepping"); last = cost;
            int cd = MagneticGroundStepRules.cooldownTicks(q);
            check(cd >= 1 && cd <= 40 && BigDecimal.valueOf(cd).compareTo(MagneticGroundStepRules.theoreticalCooldown(q)) >= 0,
                    "only native schedule rounds cooldown upward");
        }
        check(MagneticGroundStepRules.cooldownTicks(new BigDecimal("3")) == 40
                && MagneticGroundStepRules.cooldownTicks(new BigDecimal("4")) == 10
                && MagneticGroundStepRules.cooldownTicks(new BigDecimal("5")) == 1, "reviewed cooldown anchors");
        BigDecimal capacity = new BigDecimal("123456789123456789.123456789");
        check(MagneticGroundStepRules.shieldFee(capacity).compareTo(new BigDecimal("1234567891234567.89123456789")) == 0,
                "one-percent fee stays exact beyond double integer precision");
        Vector direction = new Vector(0, 0, 1);
        check(MagneticGroundStepRules.assist(new Vector(0, .01, 0), direction, 0) == null, "jump never becomes flight");
        check(MagneticGroundStepRules.assist(new Vector(0, -.2, 0), direction, 0) == null, "falling never becomes glide");
        check(MagneticGroundStepRules.assist(new Vector(0, 0, -.2), direction, 0) == null, "opposing knockback is not counteracted");
        check(MagneticGroundStepRules.assist(new Vector(.5, 0, 0), direction, 0) == null, "external overspeed is not clamped");
        check(MagneticGroundStepRules.assist(new Vector(0, 0, .2), direction, MagneticGroundStepRules.RANGE - .1) == null, "too much remaining inertia retires instead of braking");
        check(MagneticGroundStepRules.assist(new Vector(0, 0, 0), direction, MagneticGroundStepRules.RANGE) == null, "exact powered cap retires");
        check(MagneticGroundStepRules.expired(1, 1 + MagneticGroundStepRules.MAX_TICKS) && !MagneticGroundStepRules.expired(1, MagneticGroundStepRules.MAX_TICKS)
                && MagneticGroundStepRules.expired(1, 0), "finite lifetime and backwards-clock fail closed");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(MagneticGroundStepRules.assist(new Vector(bad, 0, 0), direction, 0) == null, "nonfinite inertia rejected");
            check(MagneticGroundStepRules.assist(new Vector(0, 0, 0), direction, bad) == null, "nonfinite distance rejected");
        }
        Random random = new Random(132);
        for (int i = 0; i < 12000; i++) {
            double angle = random.nextDouble() * Math.PI * 2, speed = random.nextDouble() * .4;
            Vector v = new Vector(Math.sin(angle) * speed, -.08, Math.cos(angle) * speed);
            Vector result = MagneticGroundStepRules.assist(v, direction, random.nextDouble() * 7.6);
            if (result != null) {
                check(Double.doubleToLongBits(result.y()) == Double.doubleToLongBits(v.y()), "vertical inertia preserved bit-for-bit");
                check(result.horizontalLength() <= .4000000001, "bounded total horizontal speed");
                check(Math.hypot(result.x() - v.x(), result.z() - v.z()) <= .0800000001, "bounded additive assistance");
                check(result.x() == v.x() && result.z() >= v.z(), "no rewriting perpendicular or forward external inertia");
            }
            Vector arrow = new Vector(Math.sin(angle) * 1.5, random.nextDouble() - .5, Math.cos(angle) * 1.5);
            for (int side : new int[]{-1, 1}) {
                Vector deflected = MagneticGroundStepRules.deflect(arrow, side);
                check(deflected != null && Math.abs(length(arrow) - length(deflected)) < 1e-12 && arrow.y() == deflected.y(),
                        "side turn preserves vertical velocity and total arrow speed");
            }
        }
        double travelled = 0; Vector velocity = new Vector(0, 0, 0); int ticks = 0;
        for (; ticks < MagneticGroundStepRules.MAX_TICKS; ticks++) {
            Vector result = MagneticGroundStepRules.assist(velocity, direction, travelled);
            if (result == null) break;
            velocity = result; travelled += velocity.horizontalLength();
            check(travelled <= MagneticGroundStepRules.RANGE + 1e-9, "every assisted step fits cumulative powered budget");
        }
        check(travelled > MagneticGroundStepRules.RANGE - .5 && ticks < MagneticGroundStepRules.MAX_TICKS && velocity.horizontalLength() > 0, "powered cap leaves genuine native coasting inertia");
        String backend = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/MagneticGroundStep.java"));
        for (String forbidden : new String[]{"teleportTo(", "setPos(", "moveTo(", ".move(", "fallDistance =", "resetFallDistance(", "setBlock(", "getChunk(", ".hurt(", "setInvulnerable("})
            check(!backend.contains(forbidden), "no movement, damage or world shortcut: " + forbidden);
        check(backend.contains("BooleanOp.ONLY_FIRST") && backend.contains("world.getBlockCollisions"), "full support footprint uses actual native collision shapes");
        int eligibility = backend.indexOf("private static boolean canAct");
        check(backend.indexOf("if (!loadedArea(p.serverLevel(), envelope)", eligibility)
                < backend.indexOf("p.onClimbable()", eligibility), "unloaded guard precedes native block-sensitive eligibility reads");
        check(backend.contains("HighPowerCombat.spendShield") && backend.contains("ElectromasterChargeDecoy.leased"), "exact shared ledger and cross-controller leases");
        String config=Files.readString(Path.of("src/main/java/cn/academy/port/AcademyConfig.java"));
        check(config.contains("config.getFullPath()")&&config.contains("MagneticGroundStepAdmission.migrate(")&&config.contains("SPEC.save()"),"actual migration is file-owned, versioned and saved rather than a transient QA opt-in");
        System.out.println("PASS " + checks + " NEW132 bounded motion, continuous costs, exact fee and invariant checks");
    }
}
