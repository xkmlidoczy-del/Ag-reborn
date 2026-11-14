package cn.acamech.qa;

import cn.acamech.Chassis;
import cn.acamech.MechAttack;
import cn.acamech.PulseBolt;
import cn.acamech.RangedWeapon;
import cn.acamech.MechEntity;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import static cn.acamech.qa.NativeRun.*;

/** Isolated filming harness. All driving, attacks and mounting go through the released client controls. */
final class DriveShowcase {
    private enum View { LINEUP, PILOT, TRAIL, FRONT, SPRINT, LIFT, RANGE, EXIT, WEIGHT, GAIT_DETAIL }
    private static final int FLOOR = -60, OPENING = 56, WEIGHT_DRIVE = 202;
    private static final int ATLAS_END = 438 + WEIGHT_DRIVE, AETHER_END = 784 + 2 * WEIGHT_DRIVE, END = 1094 + 3 * WEIGHT_DRIVE;
    private static final double LANE_X = 32.5, LANE_Z = -13.5;
    private static final InputConstants.Key AUX = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_X);
    private static final InputConstants.Key STRIKE = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_Z);
    private static final InputConstants.Key FIRE = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G);
    private static final Map<String, Object> checks = new LinkedHashMap<>();
    private static final Map<String, Integer> cues = new LinkedHashMap<>();
    private static String cue, chapter, action, detail;
    private static int age = -1;
    private static boolean cockpitStill;
    private static boolean strikeStill;
    private static boolean rangedStill, chargeStill, rangeCover;
    private static Chassis chassis;
    private static View view = View.LINEUP;
    private static ShowcaseCapture recorder;
    private static Husk dummy;
    private static Husk flank;
    private static float hpBefore, cruise, initialYaw;
    private static int initialCharge;
    private static double peak;
    private static Vec3 initialPosition;
    private static int chargeAtStart;
    private static BlockPos cover;
    private static Vec3 shotPoint;
    private static double testDistance;
    private static float walkSpeed, runSpeed, brakeBlend;
    private static Vec3 brakePoint;
    private static float angleOnRun;
    private static GaitProbe gaitProbe;
    private static int gaitTick = -1;
    private static boolean runStill, brakeStill, turnStill, chargeHatchStill, strikeHatchStill;

    static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((CalculateDetachedCameraDistanceEvent event) -> {
            if (usesCamera(Minecraft.getInstance())) event.setDistance(0f);
        });
        bus.addListener(DriveShowcase::frameCamera);
        bus.addListener(DriveShowcase::capture);
    }

    static void prepare(ServerLevel level) {
        level.setDayTime(8840);
        var strays = new java.util.ArrayList<Mob>();
        level.getAllEntities().forEach(entity -> { if (entity instanceof Mob mob) strays.add(mob); });
        strays.forEach(Mob::discard);
        for (int z = -55; z <= -6; z++) for (int x = -37; x <= 45; x++) {
            var surface = Blocks.GRAY_CONCRETE;
            if (x == -37 || x == 45 || z == -55) surface = Blocks.POLISHED_ANDESITE;
            else if (Math.floorMod(x, 8) == 4 && Math.floorMod(z, 6) < 3) surface = Blocks.LIGHT_GRAY_CONCRETE;
            else if (z == -11 && Math.floorMod(x, 4) < 2) surface = Blocks.WHITE_CONCRETE;
            level.setBlockAndUpdate(new BlockPos(x, FLOOR - 1, z), surface.defaultBlockState());
        }
        // A continuous flat surface with meter marks makes any planted-boot drift visible.
        for (int z = -84; z <= -9; z++) for (int x = -9; x <= 72; x++) {
            var block = (x == -9 || x == 72) ? Blocks.LIGHT_GRAY_CONCRETE
                    : x == 26 || x == 39 ? Blocks.YELLOW_CONCRETE
                    : Math.floorMod(z, 4) == 0 && x % 4 == 0 ? Blocks.WHITE_CONCRETE
                    : x < 26 || x > 39 ? Blocks.GRAY_CONCRETE : Blocks.SMOOTH_STONE;
            level.setBlockAndUpdate(new BlockPos(x, FLOOR - 1, z), block.defaultBlockState());
        }
        for (int z = -75; z <= -10; z += 13) {
            int x = 70;
            for (int y = FLOOR; y <= FLOOR + 5; y++) {
                var trim = y == FLOOR + 4 ? Blocks.SEA_LANTERN : Blocks.POLISHED_DEEPSLATE;
                level.setBlockAndUpdate(new BlockPos(x, y, z), trim.defaultBlockState());
            }
        }
    }

    static boolean tick(Minecraft mc, int time) throws Exception {
        age = time;
        if (time == 0) {
            mc.options.fov().set(51);
            mc.options.bobView().set(true);
            mc.options.hideGui = false;
            var version = ModList.get().getModContainerById("acamech").orElseThrow().getModInfo().getVersion();
            String scene = ModList.get().isLoaded("academy") ? "Ag 重生 a3.63 同装  /  试车场" : "独立运行  /  试车场";
            card("lineup", version.toString(), "三台机甲 · 大步跑、急停与转向", scene);
        }
        if (time >= END) return true;
        if (time < OPENING) return false;
        int local;
        if (time < ATLAS_END) { chassis = Chassis.ATLAS; local = time - OPENING; }
        else if (time < AETHER_END) { chassis = Chassis.AETHER; local = time - ATLAS_END; }
        else { chassis = Chassis.SERAPH; local = time - AETHER_END; }
        MechEntity mech = mech(chassis);
        if (local == 0) {
            view = chassis == Chassis.ATLAS ? View.PILOT : View.FRONT;
            card(chassis.id + "_mount", caption(), "空手右键  /  上机", "进入驾驶座后，驾驶面板自动出现");
            server(mc, () -> {
                var pilot = player(mc);
                pilot.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
                pilot.connection.teleport(mech.getX(), FLOOR, mech.getZ() - chassis.width * .5 - 1.12, 0, -4);
                initialPosition = mech.position(); initialYaw = mech.getYRot(); initialCharge = mech.charge();
                return null;
            });
        }
        if (local == 9) mc.gameMode.interact(mc.player, clientMech(mc), InteractionHand.MAIN_HAND);
        if (local == 20) {
            require(mc.player.getVehicle() == clientMech(mc), "showcase client mounted " + chassis);
            server(mc, () -> { require(mech.getFirstPassenger() == player(mc), "showcase server mounted " + chassis); return null; });
        }
        if (local >= 21 && local < 21 + WEIGHT_DRIVE) {
            weightDrive(mc, mech, local - 21);
            return false;
        }
        if (local == 21 + WEIGHT_DRIVE) {
            gaitTick = -1;
            checks.put(chassis.id + "_rendered_gait", gaitProbe.results(chassis.id));
            server(mc, () -> {
                mech.moveTo(initialPosition.x, initialPosition.y, initialPosition.z, initialYaw, 0);
                return null;
            });
            mc.player.setYRot(initialYaw);
            mc.player.setXRot(12);
            view = chassis == Chassis.ATLAS ? View.PILOT : View.FRONT;
            card(chassis.id + "_return", caption(), "返回试车位", "继续测试机型专属动作和攻击");
        }
        if (local >= 21 + WEIGHT_DRIVE) local -= WEIGHT_DRIVE;
        switch (chassis) {
            case ATLAS -> atlas(mc, mech, local);
            case AETHER -> aether(mc, mech, local);
            case SERAPH -> seraph(mc, mech, local);
        }
        return false;
    }

    private static void weightDrive(Minecraft mc, MechEntity mech, int t) throws Exception {
        gaitTick = t;
        if (t == 0) {
            walkSpeed = runSpeed = brakeBlend = 0;
            runStill = brakeStill = turnStill = false;
            gaitProbe = new GaitProbe();
            server(mc, () -> {
                mech.moveTo(LANE_X, FLOOR, LANE_Z, 180, 0);
                return null;
            });
            view = View.WEIGHT;
            card(chassis.id + "_weight_start", caption(), "W  /  行走预备", "参照跑道标线观察支撑脚");
        }
        if (t == 10) mc.options.keyUp.setDown(true);
        if (t == 36) walkSpeed = server(mc, mech::driveSpeed);
        if (t == 41) {
            mc.options.keySprint.setDown(true);
            String note = switch (chassis) {
                case ATLAS -> "髋部先下沉，肩臂慢摆，低重心蹬地";
                case AETHER -> "高抬膝跨步，前臂快速回收再摆出";
                case SERAPH -> "背翼随髋部换重心，肩和手臂稳住机身";
            };
            card(chassis.id + "_weight_run", caption(), "W + Ctrl  /  走路切大步跑", note);
        }
        if (t == 69) {
            view = View.GAIT_DETAIL;
            card(chassis.id + "_gait_hydraulics", caption(), "蹬地、承重与液压伸缩", "看髋、膝、肘的滑杆和跨过标线的鞋底");
        }
        if (t == 83) {
            runSpeed = server(mc, mech::driveSpeed);
            require(runSpeed > walkSpeed * 1.22f && clientMech(mc).run(1) > .82f,
                    "walk transitions to run " + chassis + " " + walkSpeed + " → " + runSpeed);
        }
        if (t == 94) {
            view = View.WEIGHT;
            angleOnRun = server(mc, mech::getYRot);
            mc.options.keyLeft.setDown(true);
            card(chassis.id + "_gait_turn", caption(), "W + Ctrl + A  /  跑动转向", "外侧脚跨入弧线，支撑脚留在落点，肩带反向稳住");
        }
        if (t == 117) {
            float turn = server(mc, () -> Math.abs(Mth.wrapDegrees(mech.getYRot() - angleOnRun)));
            require(turn > 35 && clientMech(mc).running() && clientMech(mc).driveSpeed() > walkSpeed,
                    "curved running at speed " + chassis + ": " + turn + " deg");
            checks.put(chassis.id + "_running_turn_degrees", round(turn));
            mc.options.keyLeft.setDown(false);
        }
        if (t == 136) {
            brakePoint = server(mc, mech::position);
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            card(chassis.id + "_gait_brake", caption(), "松开 W  /  急停", "最后一步落地，髋膝吸收惯性后回正");
        }
        if (t >= 137 && t < 165) brakeBlend = Math.max(brakeBlend, clientMech(mc).braking(1));
        if (t == 165) {
            var model = clientMech(mc);
            server(mc, () -> {
                double brakingDistance = brakePoint.distanceTo(mech.position());
                require(brakeBlend > .53f && mech.driveSpeed() < .003f && brakingDistance > .15 && brakingDistance < 2.6
                        && model.feet() != null && model.feet().swingSide() == 0, "settled running stop " + chassis
                        + " distance=" + brakingDistance + " blend=" + brakeBlend);
                checks.put(chassis.id + "_gait_controls", Map.of("walk_kmh", round(walkSpeed * 72), "run_kmh", round(runSpeed * 72),
                        "stop_distance_blocks", round(brakingDistance), "peak_brake_pose", round(brakeBlend)));
                return null;
            });
            mc.options.keyUp.setDown(true);
            card(chassis.id + "_gait_resume", caption(), "W  /  急停后重新起步", "双脚从最后的落点接回行走节奏");
        }
        if (t == 186) {
            require(clientMech(mc).driveSpeed() > walkSpeed * .8, "restart after braking " + chassis
                    + " speed=" + clientMech(mc).driveSpeed() + " position=" + clientMech(mc).position());
            mc.options.keyUp.setDown(false);
        }
    }

    private static void atlas(Minecraft mc, MechEntity mech, int t) throws Exception {
        if (t == 14) { mc.player.setYRot(180); mc.player.setXRot(12); }
        if (t == 21) {
            card("atlas_hud", caption(), "W  /  驾驶舱起步", "左上是原生 HUD：速度、耐久、电量");
            mc.options.keyUp.setDown(true);
        }
        if (t == 50) {
            mc.options.keyLeft.setDown(true);
            card("atlas_heading", caption(), "W + A  /  航向变化", "上方罗盘跟随机身航向实时转动");
        }
        if (t == 61) {
            view = View.TRAIL;
            card("atlas_steer", caption(), "W + A  /  行走并左转", "脚掌依次承重，髋膝吸收落脚冲击");
        }
        if (t == 71) mc.options.keyLeft.setDown(false);
        if (t == 87) mc.options.keyUp.setDown(false);
        if (t == 95) {
            card("atlas_reverse", caption(), "S  /  倒车", "松开方向键后减速");
            mc.options.keyDown.setDown(true);
        }
        if (t == 112) mc.options.keyDown.setDown(false);
        if (t == 118) server(mc, () -> {
            double distance = mech.position().distanceTo(initialPosition);
            float turn = Math.abs(Mth.wrapDegrees(mech.getYRot() - initialYaw));
            require(distance > 5 && turn > 23 && mech.charge() < initialCharge, "atlas driving and steering");
            checks.put("atlas_net_movement_blocks", round(distance)); checks.put("atlas_turn_degrees", round(turn));
            return null;
        });
        if (t == 120) {
            view = View.FRONT;
            KeyMapping.set(AUX, true);
            card("atlas_brace", caption(), "按住 X  /  架盾", "机体下沉，双臂抬起进入防御架势");
        }
        if (t == 140) {
            int damage = server(mc, () -> {
                require(mech.auxiliary(), "atlas brace active");
                int before = mech.hull();
                mech.hurt(mech.damageSources().generic(), 20);
                int lost = before - mech.hull();
                require(lost == 9, "atlas brace damage reduction");
                checks.put("atlas_braced_damage_from_20", lost);
                return lost;
            });
            detail = "施加 20 点测试伤害，机体扣 " + damage + " 点";
        }
        if (t == 165) KeyMapping.set(AUX, false);
        strike(mc, mech, t, 175, 195, 231, false);
        ranged(mc, mech, t, 239, 258, 358, false, false);
        if (t == 362) dismount(mc, true);
        if (t == 376) dismount(mc, false);
    }

    private static void aether(Minecraft mc, MechEntity mech, int t) throws Exception {
        if (t == 22) {
            view = View.SPRINT;
            mc.options.keyUp.setDown(true);
            card("aether_cruise", caption(), "W  /  巡航", "轻型底盘用更快的抬步和肩臂摆动");
        }
        if (t == 48) {
            cruise = server(mc, mech::driveSpeed);
            checks.put("aether_cruise_kmh", Math.round(cruise * 72));
            KeyMapping.set(AUX, true);
            card("aether_sprint", caption(), "W + X  /  冲刺", "左上显示冲刺状态，机体前倾提速");
        }
        if (t == 67) server(mc, () -> {
            require(mech.auxiliary() && mech.driveSpeed() > cruise * 1.4f,
                    "aether sprint speed: " + mech.driveSpeed() + " from " + cruise + " at " + mech.position());
            checks.put("aether_sprint_kmh", Math.round(mech.driveSpeed() * 72));
            return null;
        });
        if (t == 80) {
            KeyMapping.set(AUX, false);
            mc.options.keyUp.setDown(false);
        }
        strike(mc, mech, t, 91, 110, 142, false);
        ranged(mc, mech, t, 151, 178, 227, true, false);
        ranged(mc, mech, t, 239, 258, 302, false, true);
        if (t == 307) {
            server(mc, () -> {
                dummy = spawnDummy(player(mc).serverLevel(), mech, 23, 0);
                chargeAtStart = mech.charge();
                hpBefore = dummy.getHealth();
                shotPoint = dummy.position().add(0, 1.33, 0);
                return null;
            });
            aim(mc, shotPoint);
            card("aether_cancel", caption(), "下机取消充能", "驱动器蓄势时退出，武器不会发射");
        }
        if (t == 315) KeyMapping.click(FIRE);
        if (t == 320) {
            require(clientMech(mc).rangedAge() >= 0 && clientMech(mc).rangedAge() < chassis.ranged.windup,
                    "driver still charging before exit");
            dismount(mc, true);
        }
        if (t == 331) {
            dismount(mc, false);
            server(mc, () -> {
                require(mech.rangedAge() < 0 && mech.rangedCooldown() == 0 && mech.charge() == chargeAtStart
                        && dummy.getHealth() == hpBefore, "early dismount canceled shot without cost");
                checks.put("unmount_cancels_charge_without_spending_energy", true);
                dummy.discard();
                return null;
            });
        }
    }

    private static void seraph(Minecraft mc, MechEntity mech, int t) throws Exception {
        if (t == 26) {
            view = View.LIFT;
            peak = FLOOR;
            KeyMapping.set(AUX, true);
            card("seraph_lift", caption(), "按住 X  /  展翼抬升", "展开背翼，离地向上抬升");
        }
        if (t == 40 || t == 47 || t == 50 || t == 55 || t == 58) server(mc, () -> {
            peak = Math.max(peak, mech.getY());
            if (t == 40) require(mech.auxiliary() && peak > FLOOR + 2, "seraph lift active");
            return null;
        });
        if (t == 61) {
            KeyMapping.set(AUX, false);
            require(peak > FLOOR + 5, "seraph rose above stage");
            checks.put("seraph_peak_rise_blocks", round(peak - FLOOR));
            card("seraph_land", caption(), "抬升结束  /  落地", "实测抬升约 " + Math.round(peak - FLOOR) + " 格，随后收翼");
        }
        if (t == 92) server(mc, () -> { require(mech.onGround() && !mech.auxiliary(), "seraph landed"); return null; });
        strike(mc, mech, t, 94, 117, 170, false);
        ranged(mc, mech, t, 174, 195, 271, false, false);
        if (t == 275) {
            view = View.EXIT;
            card("exit", caption(), "Shift  /  离开驾驶舱", "下机后，左上原生驾驶面板消失");
            dismount(mc, true);
        }
        if (t == 290) dismount(mc, false);
    }

    private static void ranged(Minecraft mc, MechEntity mech, int time, int setup, int trigger, int clean,
                               boolean firstPerson, boolean blocked) throws Exception {
        var weapon = chassis.ranged;
        if (time < setup || time > clean) return;
        String key = chassis.id + (blocked ? "_ranged_cover" : "_ranged");
        if (time == setup) {
            view = firstPerson ? View.PILOT : View.RANGE;
            rangeCover = blocked;
            rangedStill = false;
            chargeStill = false;
            chargeHatchStill = false;
            testDistance = switch (weapon) { case PULSE -> 20; case NEEDLE -> 23; case CONVERGENCE -> 18; };
            shotPoint = server(mc, () -> {
                var level = player(mc).serverLevel();
                dummy = spawnDummy(level, mech, testDistance, 0);
                flank = weapon == RangedWeapon.PULSE ? spawnDummy(level, mech, testDistance, 4.4f) : null;
                if (blocked) {
                    cover = BlockPos.containing(mech.position().add(forward(mech.getYRot()).scale(10)));
                    for (int y = 0; y < 5; y++) for (int x = -1; x <= 1; x++) {
                        level.setBlockAndUpdate(cover.offset(x, y, 0), Blocks.GRAY_CONCRETE.defaultBlockState());
                    }
                }
                chargeAtStart = mech.charge();
                hpBefore = dummy.getHealth();
                return dummy.position().add(0, 1.33, 0);
            });
            aim(mc, shotPoint);
            String title = switch (weapon) {
                case PULSE -> "G  /  重型脉冲";
                case NEEDLE -> "G  /  针束点射";
                case CONVERGENCE -> "G  /  交汇翼束";
            };
            String note = switch (weapon) {
                case PULSE -> "胸部挡板展开 · 脉冲弹飞向 20 格外靶位";
                case NEEDLE -> blocked ? "实心掩体挡在 23 格外的训练靶前" : "驾驶舱瞄准，右臂点射后切到散热细节";
                case CONVERGENCE -> "双翼展开 · 两道翼束向 18 格外靶位交汇";
            };
            card(key, caption(), title, note);
        }
        if (time == trigger - 1) aim(mc, shotPoint);
        if (time == trigger) {
            KeyMapping.click(FIRE);
            cues.put(key + "_g_pressed", recorder.currentFrame());
        }
        if (time == trigger + 6) server(mc, () -> {
            require(mech.rangedAge() >= 1 && mech.rangedAge() < weapon.windup && mech.rangedCooldown() == 0,
                    "ranged winding up " + key + " age=" + mech.rangedAge());
            require(dummy.getHealth() == hpBefore && mech.charge() == chargeAtStart, "windup leaves health and charge alone " + key
                            + " hp=" + dummy.getHealth() + "/" + hpBefore + " energy=" + mech.charge() + "/" + chargeAtStart
                            + " at " + dummy.position());
            return null;
        });
        if (time == trigger + weapon.windup + 5) server(mc, () -> {
            require(mech.rangedCooldown() > 0 && mech.charge() == chargeAtStart - weapon.charge,
                    "ranged exact charge and cooldown " + key + " result=" + mech.rangedResult());
            if (weapon == RangedWeapon.PULSE) {
                var activeBolts = player(mc).serverLevel().getEntitiesOfClass(PulseBolt.class, mech.getBoundingBox().inflate(34));
                require(activeBolts.size() == 1 && dummy.getHealth() == hpBefore, "pulse flies before contact");
                checks.put("atlas_pulse_has_travel_time", true);
            }
            return null;
        });
        if (firstPerson && time == trigger + weapon.windup + 3) view = View.RANGE;
        if (weapon == RangedWeapon.PULSE && time == trigger + weapon.windup + 15) KeyMapping.click(FIRE);
        int checkTick = trigger + weapon.windup + (weapon == RangedWeapon.PULSE ? 23 : 5);
        if (time == checkTick) {
            int expected = blocked ? RangedWeapon.BLOCKED : weapon == RangedWeapon.PULSE ? 2 : 1;
            float after = server(mc, () -> {
                float hp = dummy.getHealth();
                require((blocked ? hp == hpBefore : hp < hpBefore) && mech.rangedResult() == expected,
                        key + " health=" + hp + " result=" + mech.rangedResult());
                if (weapon == RangedWeapon.CONVERGENCE) {
                    require(hpBefore - hp > weapon.damage * 1.8, "both wing rays reach target");
                    checks.put("seraph_both_rays_contributed", true);
                }
                if (flank != null) {
                    require(flank.getHealth() < flank.getMaxHealth() && mech.charge() == chargeAtStart - weapon.charge,
                            "pulse splash damage and repeated G during cooldown");
                    checks.put("atlas_ranged_splash_target_hp", round(flank.getHealth()));
                    checks.put("atlas_ranged_cooldown_blocks_retrigger", true);
                }
                checks.put(key, Map.of("before", round(hpBefore), "after", round(hp), "blocks", testDistance,
                        "result", mech.rangedResult(), "discharge_cost", weapon.charge));
                return hp;
            });
            require(clientMech(mc).rangedResult() == expected, "client received ranged confirmation " + key);
            detail = blocked ? "光束止于掩体 · 靶标仍为 " + hp(after) + " HP"
                    : "主靶 HP  " + hp(hpBefore) + " → " + hp(after) + "  （服务器读数）";
        }
        if (weapon == RangedWeapon.PULSE && time == trigger + weapon.windup + weapon.cooldown + 6) {
            require(clientMech(mc).rangedCooldown() == 0, "pulse became available again");
            checks.put("atlas_ranged_returns_to_ready", true);
            detail = "冷却完成 · 远程再次就绪";
        }
        if (time == clean) server(mc, () -> {
            dummy.discard();
            if (flank != null) flank.discard();
            if (blocked) {
                var level = player(mc).serverLevel();
                for (int y = 0; y < 5; y++) for (int x = -1; x <= 1; x++) level.removeBlock(cover.offset(x, y, 0), false);
            }
            return null;
        });
    }

    private static void aim(Minecraft mc, Vec3 target) {
        Vec3 delta = target.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        mc.player.setXRot((float) Math.toDegrees(Math.atan2(-delta.y, delta.horizontalDistance())));
    }

    private static void strike(Minecraft mc, MechEntity mech, int time, int setup, int hit, int clean,
                               boolean firstPerson) throws Exception {
        String name = switch (chassis.attack) {
            case HYDRAULIC -> "液压重拳";
            case SWEEP -> "伺服横扫";
            case WINGS -> "双翼斩";
        };
        String shot = chassis.id + (firstPerson ? "_pilot_strike" : "_strike");
        if (time == setup) {
            view = firstPerson ? View.PILOT : View.FRONT;
            strikeStill = false;
            strikeHatchStill = false;
            if (firstPerson) {
                mc.player.setYRot(clientMech(mc).getYRot());
                mc.player.setXRot(36);
            }
            server(mc, () -> {
                var level = player(mc).serverLevel();
                double reach = switch (chassis) { case ATLAS -> 3.55; case AETHER -> 2.53; case SERAPH -> 3.02; };
                dummy = spawnDummy(level, mech, reach, 0);
                flank = !firstPerson && chassis == Chassis.AETHER ? spawnDummy(level, mech, 2.94, 49) : null;
                hpBefore = dummy.getHealth();
                chargeAtStart = mech.charge();
                return null;
            });
            String note = switch (chassis.attack) {
                case HYDRAULIC -> firstPerson ? "驾驶舱视角 · 看准星的命中标记" : "左臂护板打开，液压缸出拳，背部换热器排气";
                case SWEEP -> "右臂侧板掀开，腰肩带动横扫，随后回震";
                case WINGS -> "翼锁抬起，双翼交斩后分段回弹并泄压";
            };
            card(shot, caption(), "Z  /  " + name, note);
        }
        if (time == hit) {
            KeyMapping.click(STRIKE);
            cues.put(shot + "_z_pressed", recorder.currentFrame());
        }
        if (time == hit + 3) server(mc, () -> {
            require(mech.strikeAge() >= 0 && mech.strikeResult() < 0 && dummy.getHealth() == hpBefore,
                    "no early damage before contact " + shot);
            require(mech.charge() == chargeAtStart - MechAttack.CHARGE_COST, "exact attack charge cost " + shot);
            return null;
        });
        if (time == hit + chassis.attack.contactTick + 4) {
            int expected = flank == null ? 1 : 2;
            float after = server(mc, () -> {
                float health = dummy.getHealth();
                require(health < hpBefore && mech.strikeResult() == expected, "native hit and count " + shot);
                checks.put(shot + "_target_health", Map.of("before", round(hpBefore), "after", round(health)));
                if (flank != null) {
                    require(flank.getHealth() < flank.getMaxHealth(), "sweep picked up side target");
                    checks.put(shot + "_side_target_health", Map.of("angle_degrees", 49, "before", round(flank.getMaxHealth()),
                            "after", round(flank.getHealth())));
                }
                checks.put(shot + "_contact_tick", chassis.attack.contactTick);
                return health;
            });
            require(clientMech(mc).strikeResult() == expected && clientMech(mc).strikeImpacts().size() == expected,
                    "client got hit confirmation and world positions " + shot);
            action = name + "  /  命中 ×" + expected;
            detail = "靶标 HP  " + hp(hpBefore) + " → " + hp(after) + "  （服务器读数）";
        }
        if (time == clean) server(mc, () -> {
            dummy.discard();
            if (flank != null) flank.discard();
            return null;
        });
    }

    private static Husk spawnDummy(ServerLevel level, MechEntity mech, double distance, float degrees) {
        var entity = EntityType.HUSK.create(level);
        Vec3 at = mech.position().add(forward(mech.getYRot() + degrees).scale(distance));
        entity.moveTo(at.x, at.y, at.z, mech.getYRot() + degrees + 180, 0);
        entity.setNoAi(true);
        require(level.addFreshEntity(entity), "training husk spawned");
        return entity;
    }

    private static void snapshot(Minecraft mc) {
        if (chassis == null || cue == null) return;
        var model = clientMech(mc);
        if (model == null) return;
        if (!runStill && cue.endsWith("_weight_run") && model.run(1) > .85f && model.feet() != null
                && Mth.frac(model.phase(1) * 2) > .22f && Mth.frac(model.phase(1) * 2) < .52f) {
            Screenshot.grab(mc.gameDirectory, chassis.id + "-run.png", mc.getMainRenderTarget(), ignored -> {});
            runStill = true;
        }
        if (!brakeStill && cue.endsWith("_gait_brake") && model.braking(1) > .62f) {
            Screenshot.grab(mc.gameDirectory, chassis.id + "-brake.png", mc.getMainRenderTarget(), ignored -> {});
            brakeStill = true;
        }
        if (!turnStill && cue.endsWith("_gait_turn") && Math.abs(model.turning(1)) > .46f) {
            Screenshot.grab(mc.gameDirectory, chassis.id + "-turn.png", mc.getMainRenderTarget(), ignored -> {});
            turnStill = true;
        }
        if (!strikeHatchStill && cue.endsWith("_strike") && model.strikeAge() == chassis.attack.contactTick + 5) {
            Screenshot.grab(mc.gameDirectory, chassis.id + "-melee-exhaust.png", mc.getMainRenderTarget(), ignored -> {});
            strikeHatchStill = true;
        }
        if (!strikeStill && cue.endsWith("_strike") && model.strikeResult() > 0 && !model.strikeImpacts().isEmpty()
                && model.strikeImpactAge(0) >= 1) {
            Screenshot.grab(mc.gameDirectory, cue + "-native.png", mc.getMainRenderTarget(), ignored -> {});
            strikeStill = true;
        }
        if (cue.contains("_ranged")) {
            var weapon = chassis.ranged;
            if (!chargeStill && model.rangedAge() >= weapon.windup - 3 && model.rangedAge() < weapon.windup) {
                Screenshot.grab(mc.gameDirectory, cue + "-charge.png", mc.getMainRenderTarget(), ignored -> {});
                chargeStill = true;
            }
            if (!chargeHatchStill && !rangeCover && model.rangedAge() == weapon.windup + 5) {
                Screenshot.grab(mc.gameDirectory, chassis.id + "-ranged-exhaust.png", mc.getMainRenderTarget(), ignored -> {});
                chargeHatchStill = true;
            }
            if (!rangedStill && model.rangedCooldown() > 0 && model.rangedResult() != RangedWeapon.PENDING) {
                Screenshot.grab(mc.gameDirectory, cue + "-impact.png", mc.getMainRenderTarget(), ignored -> {});
                rangedStill = true;
            }
        }
    }

    private static void dismount(Minecraft mc, boolean pressed) throws Exception {
        mc.options.keyShift.setDown(pressed);
        if (!pressed) {
            require(mc.player.getVehicle() == null, "client dismounted " + chassis);
            server(mc, () -> { require(!player(mc).isPassenger(), "server dismounted " + chassis); return null; });
            checks.put(chassis.id + "_dismounted", true);
        }
    }

    private static void frameCamera(RenderFrameEvent.Pre event) {
        if (age < 0 || age >= END) return;
        var mc = Minecraft.getInstance();
        if (view == View.PILOT) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            if (usesCamera(mc)) mc.setCameraEntity(mc.player);
            return;
        }
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (view == View.LINEUP) {
            look(mc, new Vec3(-1.65 + (age + partial) * .053, FLOOR + 3.58, -15.7),
                    new Vec3(.45, FLOOR + 2.9, .5));
            return;
        }
        var model = clientMech(mc);
        if (model == null) return;
        Vec3 pos = model.getPosition(partial);
        float yaw = Mth.rotLerp(partial, model.yRotO, model.getYRot());
        Vec3 ahead = forward(yaw), east = new Vec3(-ahead.z, 0, ahead.x);
        double height = chassis.height;
        Vec3 at = pos.add(0, height * .46, 0);
        Vec3 from = switch (view) {
            case TRAIL -> pos.add(ahead.scale(-height * 1.23)).add(east.scale(height * 1.21)).add(0, height * .69, 0);
            case SPRINT -> pos.add(ahead.scale(-3.7)).add(east.scale(7.7)).add(0, 3.13, 0);
            case LIFT -> pos.add(ahead.scale(-2.7)).add(east.scale(11.6)).add(0, 4 - (pos.y - FLOOR) * .47, 0);
            case WEIGHT -> {
                Vec3 track = forward(163);
                Vec3 side = new Vec3(-track.z, 0, track.x);
                yield pos.add(track.scale(2.2)).add(side.scale(-height * 1.55)).add(0, 2.02, 0);
            }
            case GAIT_DETAIL -> pos.add(ahead.scale(-height * .74)).add(east.scale(height * 1.04)).add(0, height * .40, 0);
            case EXIT -> pos.add(ahead.scale(6.7)).add(east.scale(8.8)).add(0, 3.9, 0);
            default -> pos.add(ahead.scale(height * 1.04)).add(east.scale(-height * 1.35)).add(0, height * .63, 0);
        };
        if (view == View.WEIGHT) {
            at = pos.add(ahead.scale(.79)).add(0, height * (chassis == Chassis.SERAPH ? .49 : .43), 0);
        } else if (view == View.GAIT_DETAIL) {
            at = pos.add(0, height * .41, 0);
        }
        if (view == View.RANGE) {
            boolean projectile = chassis.ranged == RangedWeapon.PULSE;
            // Oblique side view includes the full firing lane, actuator motion and the target.
            boolean close = model.rangedAge() >= 0 && model.rangedAge() < chassis.ranged.windup
                    && (chassis == Chassis.ATLAS || chassis == Chassis.SERAPH);
            from = pos.add(ahead.scale(rangeCover ? 6 : 7.7)).add(east.scale(projectile ? -18.7 : rangeCover ? 16.0 : 18.2))
                    .add(0, rangeCover ? 6.5 : 5.7, 0);
            at = pos.add(ahead.scale(rangeCover ? 6.9 : projectile ? 9.1 : 8.9)).add(0, 2.75, 0);
            if (close) {
                float ratio = (model.rangedAge() + partial) / chassis.ranged.windup;
                float zoom = Mth.clamp(Math.min(ratio * 4.5f, (1 - ratio) * 5.5f), 0, 1);
                Vec3 closer = pos.add(ahead.scale(8.6)).add(east.scale(projectile ? -7.6 : 9.7)).add(0, 4.28, 0);
                from = from.lerp(closer, zoom);
                at = at.lerp(pos.add(ahead.scale(1.5)).add(0, 3.43, 0), zoom);
            }
            if (!rangeCover && model.rangedAge() > chassis.ranged.windup) {
                float dt = model.rangedAge() - chassis.ranged.windup + partial;
                float lookback = AttackWeight.recoveryShot(dt);
                from = from.lerp(exhaustView(pos, ahead, east, chassis), lookback);
                at = at.lerp(pos.add(0, height * .59, 0), lookback);
            }
            if (shotPoint != null && projectile && model.rangedCooldown() > chassis.ranged.cooldown - 12) {
                float travel = (chassis.ranged.cooldown - model.rangedCooldown() + partial) / 12f;
                at = at.lerp(shotPoint, .18f * Mth.clamp(travel, 0, 1));
            }
        }
        if (view == View.FRONT && cue != null && (cue.endsWith("_strike") || cue.endsWith("_miss") || cue.endsWith("_cover"))) {
            at = at.add(ahead.scale(chassis == Chassis.AETHER ? 1.1 : 1.3)).add(0, -.48, 0);
            from = switch (chassis) {
                case ATLAS -> pos.add(ahead.scale(6.1)).add(east.scale(-8.6)).add(0, 3.25, 0);
                case AETHER -> pos.add(ahead.scale(6.3)).add(east.scale(7.9)).add(0, 4.36, 0);
                case SERAPH -> pos.add(ahead.scale(8.3)).add(east.scale(-7.8)).add(0, 3.6, 0);
            };
        }
        if (view == View.FRONT && cue != null && cue.endsWith("_strike") && model.strikeAge() > chassis.attack.contactTick) {
            float lookback = AttackWeight.recoveryShot(model.strikeAge() - chassis.attack.contactTick + partial);
            from = from.lerp(exhaustView(pos, ahead, east, chassis), lookback);
            at = at.lerp(pos.add(0, height * .59, 0), lookback);
        }
        if (view == View.TRAIL || view == View.SPRINT) at = at.add(ahead.scale(.62));
        look(mc, from, at);
    }

    private static Vec3 exhaustView(Vec3 pos, Vec3 ahead, Vec3 east, Chassis chassis) {
        double side = chassis == Chassis.AETHER ? 1 : -1;
        return pos.add(ahead.scale(-chassis.height * .91)).add(east.scale(side * chassis.height * 1.33))
                .add(0, chassis.height * .79, 0);
    }

    private static final class AttackWeight {
        static float recoveryShot(float t) {
            float pulse = Mth.clamp((t - 1.4f) / 3.3f, 0, 1);
            return pulse * pulse * (3 - 2 * pulse) * (.83f - .56f * Mth.clamp((t - 6) / 4, 0, 1));
        }
    }

    private static void look(Minecraft mc, Vec3 point, Vec3 target) {
        Vec3 d = target.subtract(point);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-d.y, d.horizontalDistance()));
        camera(mc, point.x, point.y, point.z, yaw, pitch);
    }

    private static void capture(RenderGuiEvent.Post event) {
        if (age < 0 || age >= END || cue == null) return;
        var mc = Minecraft.getInstance();
        var g = event.getGuiGraphics();
        snapshot(mc);
        if (!cockpitStill && age >= OPENING + 57 && age < OPENING + 61) {
            g.flush();
            Screenshot.grab(mc.gameDirectory, "cockpit-native.png", mc.getMainRenderTarget(), ignored -> {});
            cockpitStill = true;
        }
        String title = "ACA MECH  /  实机  " + chapter;
        int right = g.guiWidth() - 34, left = right - mc.font.width(title) - 14, y = g.guiHeight() - 57;
        int accent = switch (chassis == null ? Chassis.SERAPH : chassis) {
            case ATLAS -> 0xff83c6ff; case AETHER -> 0xff8df9e4; case SERAPH -> 0xffa8defa;
        };
        g.fill(left, 11, right, 38, 0xdd0c1822);
        g.fill(left, 11, right, 12, accent);
        g.drawString(mc.font, title, left + 7, 21, 0xffeaf3fc);
        int panel = Math.max(mc.font.width(action), mc.font.width(detail)) + 18;
        g.fill(10, y, 10 + panel, y + 33, 0xe3101923);
        g.fill(10, y, 12, y + 33, accent);
        g.drawString(mc.font, action, 18, y + 5, 0xfff1f8fc);
        g.drawString(mc.font, detail, 18, y + 20, 0xffc5d9e6);
        g.flush();
        try {
            if (gaitTick >= 56 && gaitTick < 163) {
                var mech = clientMech(mc);
                float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
                if (mech != null) gaitProbe.sample(mc, mech, partial);
            }
            if (recorder == null) recorder = new ShowcaseCapture(mc);
            cues.putIfAbsent(cue, recorder.currentFrame());
            recorder.frame(mc);
        } catch (Exception error) { NativeRun.finish(mc, error); }
    }

    static Map<String, Object> finish() throws Exception {
        return Map.of("showcase_checks", checks, "cues_frames", cues, "capture", recorder.finish());
    }

    static void abort() {
        age = END;
        if (recorder != null) recorder.abort();
    }

    private static void card(String code, String head, String title, String note) {
        cue = code; chapter = head; action = title; detail = note;
    }
    private static String caption() { return "0" + (chassis.ordinal() + 1) + " " + chassis.id.toUpperCase(java.util.Locale.ROOT); }
    private static MechEntity clientMech(Minecraft mc) { return (MechEntity) mc.level.getEntity(mech(chassis).getId()); }
    private static Vec3 forward(float yaw) { return new Vec3(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))); }
    private static double round(double value) { return Math.round(value * 100d) / 100d; }
    private static String hp(float value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
}
