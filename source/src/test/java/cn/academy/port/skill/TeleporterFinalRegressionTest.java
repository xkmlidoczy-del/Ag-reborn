package cn.academy.port.skill;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static cn.academy.port.skill.TeleporterFinalRules.*;

/** Boundary, original ordering and fail-closed provenance checks for the three final Teleporter skills. */
public final class TeleporterFinalRegressionTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void exact(float value, float expected, String message) {
        check(Float.floatToIntBits(value) == Float.floatToIntBits(expected), message);
    }
    private static void rejects(Runnable action, String message) {
        try {
            action.run();
        } catch (AssertionError | IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError(message);
    }
    private static void contains(String source, String snippet) {
        TeleporterFinalSourceFixtures.contains(source, snippet);
        checks++;
    }

    public static void main(String[] args) {
        check(TeleporterFinalSourceFixtures.verifyAll() == 44, "All exact source fixture hashes");
        check(TeleporterFinalSourceFixtures.verifyMedia() == 28, "All exact original runtime media hashes");
        check(LOCATION.equals("location_teleport") && SHIFT.equals("shift_tp") && FLASH.equals("flashing"), "Original skill IDs");
        check(!crossDimension(.8F) && crossDimension(Math.nextUp(.8F)) && !crossDimension(Math.nextDown(.8F)), "Strict float experience unlock");
        check(!crossDimension(Math.nextUp((double).8F)), "Cross-dimension threshold uses classic float experience");
        exact(locationOverload(), 240F, "Location fixed overload");
        exact(locationConsumption(0, 0, false), 1600F, "Location minimum cost at zero distance");
        exact(locationConsumption(1, 64, false), 1200F, "Location minimum plateau includes distance64");
        exact(locationConsumption(.5, 63.999999, false), locationConsumption(.5, 0, false), "Location float distance plateaus through64");
        exact(locationConsumption(.5, 800, false), locationConsumption(.5, 120000, false), "Location distance cap800");
        exact(locationConsumption(.5, 200, true), 2 * locationConsumption(.5, 200, false), "Location dimension penalty2");
        check(locationCompanion(1, 2, 25) && !locationCompanion(1, 2, Math.nextUp(25D)), "Original within selector includes exact five-block radius");
        check(!locationCompanion(4, 5, 0) && locationCompanion(Math.nextDown(4F), 5, 0), "Strict float volume threshold80");
        check(!locationCompanion(Math.nextDown(4D), 5, 0), "Companion width and height retain original float precision");
        exact(locationExperience(0), .015F, "Location normal post-move experience");
        exact(locationExperience(Math.nextDown(200D)), .015F, "Location post-move distance200 exclusive low branch");
        exact(locationExperience(200), .03F, "Location post-move distance200 high branch");
        float priorExperience = .099F;
        float updatedExperience = priorExperience + locationExperience(0);
        check(locationCooldown(priorExperience) == 29 && locationCooldown(updatedExperience) == 28, "Location cooldown reads newly added experience");
        float cappedExperience = 1F + Math.min(1F - 1F, locationExperience(0));
        check(locationCooldown(cappedExperience) == 20, "Location capped experience final cooldown");

        int[][] adjacent = {{7, -4, -9}, {7, -2, -9}, {7, -3, -10}, {7, -3, -8}, {6, -3, -9}, {8, -3, -9}};
        for (int side = 0; side < 6; side++) {
            check(Arrays.equals(shiftBlock(7, -3, -9, side), adjacent[side]), "Shift places in adjacent Forge face " + side);
        }
        check(Arrays.equals(shiftFallback(new Point(-cn.academy.port.core.SkillTuning.number("shift_tp", "reach_novice") - .8, -1.2, -.9), new Point(1, 0, 0), 0), new int[]{0, -1, 0}), "Shift .toInt fallback truncates negative endpoints toward zero");
        exact(shiftExperience(0), .002F, "Shift no-target experience");
        check(Float.floatToIntBits(shiftExperience(4)) == 0x3c23d70b, "Shift four-target experience preserves float product rounding");
        check(shiftCooldown(.499F) == 80 && shiftCooldown(.501F) == 79, "Shift cooldown truncation boundary");
        rejects(() -> shiftBlock(0, 0, 0, -1), "Invalid Shift face rejected");
        rejects(() -> shiftBlock(0, 0, 0, 6), "Invalid Shift face rejected");

        for (double exp : new double[]{0, .5, 1}) {
            int limit = flashMaxTime(exp);
            check(!flashExpired(limit - 1, exp) && !flashExpired(limit, exp) && flashExpired(limit + 1, exp), "Flash strict ticks>max_time boundary");
        }
        check(flashDirection(1, 0, 90).y() == 0 && flashDirection(2, 0, 90).y() == 0, "Flash A,D remain level under classic Z rotation");
        check(flashDirection(3, 0, 90).y() < -.999 && flashDirection(4, 0, 90).y() > .999, "Flash W,S pitch signs under classic Z rotation");
        check(flashDirection(1, 0, 0).x() > .999 && flashDirection(2, 0, 0).x() < -.999, "Flash local A,D direction IDs");
        check(flashDirection(3, 0, 0).z() > .999 && flashDirection(4, 0, 0).z() < -.999, "Flash local W,S direction IDs");
        for (int key : new int[]{-1, 0, 5}) rejects(() -> flashDirection(key, 0, 0), "Invalid Flash local ID rejected");
        Point hit = new Point(-.2, 3.4, -.7);
        int[] observedHead = {999, 999, 999};
        Point adjusted = flashBlock(hit, 2, -3, (x, y, z) -> {observedHead[0] = x; observedHead[1] = y; observedHead[2] = z; return false;});
        check(Arrays.equals(observedHead, new int[]{0, 0, -1}), "Flash head check uses .toInt-style casts at negative coordinates");
        check(adjusted.equals(new Point(-.2, -2.55, -1.2999999999999998)), "Flash blocked-head downward offset");
        flashBlock(hit, 0, -3, (x, y, z) -> {throw new AssertionError("Down face must not query head");});
        flashBlock(hit, 1, -3, (x, y, z) -> {throw new AssertionError("Up face must not query head");});
        checks += 2;
        Point min = new Point(0, 0, 0), max = new Point(1, 1, 1);
        check(lineBox(min, max, new Point(-1, .5, .5), new Point(2, .5, .5)), "Shift intersects strict box interior");
        check(!lineBox(min, max, new Point(-1, 0, .5), new Point(2, 0, .5)), "Shift rejects tangent face");
        check(!lineBox(min, max, new Point(-1, 0, 0), new Point(2, 0, 0)), "Shift rejects box edge");
        check(!lineBox(min, max, new Point(-1, .5, .5), new Point(0, .5, .5)), "Shift rejects endpoint on plane");
        check(lineBox(min, max, new Point(.5, .5, .5), new Point(.5, .5, .5)), "Shift accepts stationary interior start");
        check(!lineBox(min, max, new Point(0, .5, .5), new Point(0, .5, .5)), "Shift rejects stationary boundary start");

        sourceContracts();
        String locationKey = "AcademyCraft-1.0.7/src/main/scala/cn/academy/vanilla/teleporter/skill/LocationTeleport.scala";
        byte[] sourceBytes = TeleporterFinalSourceFixtures.location().getBytes(StandardCharsets.UTF_8);
        byte[] altered = sourceBytes.clone();
        altered[altered.length / 2] ^= 1;
        rejects(() -> TeleporterFinalSourceFixtures.verifySourceBytes(locationKey, altered), "Mutated original source rejected");
        rejects(() -> TeleporterFinalSourceFixtures.verifySourceBytes(locationKey, new byte[0]), "Empty original source rejected");
        rejects(() -> TeleporterFinalSourceFixtures.verifySourceBytes("unlisted", sourceBytes), "Unlisted original source rejected");
        rejects(() -> TeleporterFinalSourceFixtures.verifyMediaBytes("assets/academy/textures/abilities/teleporter/skills/flashing.png", new byte[]{1, 2, 3}), "Mutated original media rejected");
        rejects(() -> TeleporterFinalSourceFixtures.verifyMediaBytes("assets/academy/textures/abilities/teleporter/skills/flashing.png", new byte[0]), "Empty original media rejected");
        rejects(() -> TeleporterFinalSourceFixtures.verifyMediaBytes("unlisted", new byte[]{1}), "Unlisted original media rejected");
        System.out.println("PASS " + checks + " Teleporter boundary, source-contract and fail-closed mutation regressions");
    }

    private static void sourceContracts() {
        String location = TeleporterFinalSourceFixtures.location(), shift = TeleporterFinalSourceFixtures.shift(), flash = TeleporterFinalSourceFixtures.flashing();
        contains(TeleporterFinalSourceFixtures.academy("build.properties"), "lambdalib_ver\t= 1.2.3");
        contains(location, "player :: WorldUtils.getEntities(player, 5,");
        contains(location, "t.width * t.width * t.height < 80f");
        contains(location, "ctx.consumeWithForce(o, cp)");
        check(location.indexOf("e.setPositionAndRotation(dest.x + dx") < location.indexOf("val dist = player.getDistance(dest.x"), "Original Location experience reads distance after positions change");
        check(location.indexOf("ctx.addSkillExp(expincr)") < location.indexOf("ctx.setCooldown(MathUtils.lerpf(30, 20, ctx.getSkillExp)"), "Original Location cooldown follows experience update");
        contains(location, "inputText.component[TextBox].content.take(16)");
        contains(location, "locationList.zipWithIndex.foreach { case (a, idx) => a.id = idx }");
        String locationData = location.substring(location.indexOf("class LocTeleportData extends DataPart[EntityPlayer]"));
        contains(locationData, "setNBTStorage()");
        check(!locationData.contains("setClearOnDeath()"), "Original saved locations survive player death");
        contains(TeleporterFinalSourceFixtures.lambda("src/main/java/cn/lambdalib/util/datapart/DataPart.java"), "boolean clearOnDeath = false;");
        String entityData = TeleporterFinalSourceFixtures.lambda("src/main/java/cn/lambdalib/util/datapart/EntityData.java");
        contains(entityData, "public void onPlayerClone(PlayerEvent.Clone evt)");
        contains(entityData, "data.entity = evt.entityPlayer;");
        contains(entityData, "evt.entityPlayer.registerExtendedProperties(ID, data);");
        contains(location, "player.worldObj.playSoundEffect(player.posX, player.posY, player.posZ,");
        contains(TeleporterFinalSourceFixtures.lambda("src/main/java/cn/lambdalib/util/mc/EntitySelectors.java"), "return e -> e.getDistanceSq(x, y, z) <= sq;");
        contains(shift, "private val exp = ctx.getSkillExp");
        contains(shift, "Array[Int](result.blockX + dir.offsetX, result.blockY + dir.offsetY, result.blockZ + dir.offsetZ)");
        contains(shift, "result.blockX += dir.offsetX");
        contains(shift, "result.blockY += dir.offsetY");
        contains(shift, "result.blockZ += dir.offsetZ");
        contains(shift, "new MovingObjectPosition(mo.px.toInt, mo.py.toInt, mo.pz.toInt, 0, VecUtils.vec(mo.px, mo.py, mo.pz))");
        contains(shift, "VecUtils.vec(dest(0) + .5, dest(1) + .5, dest(2) + .5)");
        contains(shift, "ctx.consume(getOverload(exp), getConsumption(exp))");
        check(shift.indexOf("item.placeBlockAt(stack") < shift.indexOf("stack.stackSize -= 1"), "Original Shift places before decrementing inventory");
        contains(shift, "if(!player.capabilities.isCreativeMode)");
        contains(flash, "final String[] strs = new String[] { null, \"a\", \"d\", \"w\", \"s\"}");
        contains(flash, "final int[] keys = new int[] { -1, Keyboard.KEY_A, Keyboard.KEY_D, Keyboard.KEY_W, Keyboard.KEY_S }");
        check(flash.indexOf("dir.rotateAroundZ(player.rotationPitch") < flash.indexOf("dir.rotateAroundY((-90 - player.rotationYaw)"), "Original Flash rotation ordering");
        contains(flash, "if(ticks > max_time) terminate();\n            ticks++;");
        contains(flash, "if(ctx.cpData.getOverload() < overloadKeep) ctx.cpData.setOverload(overloadKeep)");
        contains(flash, "if (ctx.consume(0, consumption))");
        contains(flash, "player.fallDistance = 0.0f;");
        contains(flash, "ctx.addSkillExp(.002f)");
        contains(flash, "cancellor = new GravityCancellor(player, 40)");
        contains(flash, "clientRuntime().clearKeys(KEY_GROUP)");
        contains(TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/vanilla/teleporter/client/MarkRender.java"), "Resources.getEffectSeq(\"tp_mark\", 7)");
        contains(TeleporterFinalSourceFixtures.academy("src/main/java/cn/academy/vanilla/teleporter/client/TPParticleFactory.java"), "ret.fadeAfter(20, 20)");
    }
}
