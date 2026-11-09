package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.YawPalm;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.DefenseProfile;
import cn.academy.port.highpower.core.YawPalmRules;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.SpatialRelayMedium;
import cn.academy.port.highpower.SpatialRelayProvenance;
import cn.academy.port.highpower.core.HighPowerLevel;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.minecraft.server.network.CommonListenerCookie;

/** NEW423: connected native input, authenticated Q drops, and actual same-call hurt knockback. */
@GameTestHolder("academy_yaw_palm_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerYawPalmRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_yaw_palm";
    private static final String ID = "yaw_palm", SKILL = "vecmanip.yaw_palm";
    private HighPowerYawPalmRuntimeTests() { }

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {32, 10, 32}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell);
        air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_yaw_palm_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }


    /** Saved historical and observed dispatch inputs exercise the actual private contact method. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 5)
    public static void rounded_native_entry_preserves_closed_geometry_and_thirteen_boundaries(GameTestHelper h) {
        try {
            java.lang.reflect.Method contact = YawPalm.class.getDeclaredMethod("contact",
                    net.minecraft.world.phys.AABB.class, Vec3.class, Vec3.class);
            contact.setAccessible(true);
            float pitch = 22.65334129333496F * (float)(Math.PI / 180.0);
            float yaw = -.6561474800109863F * (float)(Math.PI / 180.0);
            Vec3 historicalAxis = new Vec3((double)(net.minecraft.util.Mth.sin(yaw)*net.minecraft.util.Mth.cos(pitch)),
                    (double)-net.minecraft.util.Mth.sin(pitch),
                    (double)(net.minecraft.util.Mth.cos(yaw)*net.minecraft.util.Mth.cos(pitch))).normalize();
            Vec3 historicalStart = new Vec3(.5, -60 + (double)1.62F, .5);
            Vec3 historicalEnd = historicalStart.add(historicalAxis.scale(6));
            for (int i=0; i<8 && historicalStart.distanceToSqr(historicalEnd)>36; i++)
                historicalEnd = new Vec3(Math.nextAfter(historicalEnd.x,historicalStart.x),
                        Math.nextAfter(historicalEnd.y,historicalStart.y),Math.nextAfter(historicalEnd.z,historicalStart.z));
            var historicalBox = new net.minecraft.world.phys.AABB(.45897888136998083-.125,-60,4.0818687263339015-.125,
                    .45897888136998083+.125,-59.75,4.0818687263339015+.125).inflate(.035);
            Vec3 observedStart = new Vec3(.5,-58.730000019073486,.5);
            Vec3 observedEnd = observedStart.add(new Vec3(-.01653599925339222,-.3031025230884552,.9528128504753113).normalize().scale(6));
            for (int i=0; i<8 && observedStart.distanceToSqr(observedEnd)>36; i++)
                observedEnd = new Vec3(Math.nextAfter(observedEnd.x,observedStart.x),
                        Math.nextAfter(observedEnd.y,observedStart.y),Math.nextAfter(observedEnd.z,observedStart.z));
            var observedBox = new net.minecraft.world.phys.AABB(.3124313029776842,-60,3.973168258290336,
                    .5624313029776842,-59.75,4.223168258290336).inflate(.035);
            h.assertTrue(historicalBox.clip(historicalStart,historicalEnd).orElseThrow().z<historicalBox.minZ,
                    "historical native arithmetic actually rounds outside its entry face");
            h.assertTrue(observedBox.clip(observedStart,observedEnd).orElseThrow().z<observedBox.minZ,
                    "real observed input4 native arithmetic actually rounds outside its entry face");
            var unit = new net.minecraft.world.phys.AABB(0,0,0,1,1,1);
            record Geometry(String name,net.minecraft.world.phys.AABB box,Vec3 start,Vec3 end,boolean hit) { }
            List<Geometry> cases = List.of(
                    new Geometry("historical saved ordinary Q item",historicalBox,historicalStart,historicalEnd,true),
                    new Geometry("actual crouched input4 Q item",observedBox,observedStart,observedEnd,true),
                    new Geometry("ordinary native fast path",unit,new Vec3(.5,.5,-1),new Vec3(.5,.5,2),true),
                    new Geometry("native1e-7 tolerance-only miss",unit,new Vec3(.5,1+5e-8,-1),new Vec3(.5,1+5e-8,2),false),
                    new Geometry("cross axis one ULP outside",unit,new Vec3(.5,Math.nextUp(1D),-1),new Vec3(.5,Math.nextUp(1D),2),false),
                    new Geometry("endpoint one ULP before entry",unit,new Vec3(.5,.5,-1),new Vec3(.5,.5,Math.nextDown(0D)),false),
                    new Geometry("exact closed corner edge",unit,new Vec3(1,1,-1),new Vec3(1,1,2),true),
                    new Geometry("parallel outside",unit,new Vec3(2,.5,-1),new Vec3(2,.5,2),false),
                    new Geometry("exact closed entry endpoint",unit,new Vec3(.5,.5,-1),new Vec3(.5,.5,0),true),
                    new Geometry("exact six metre entry",new net.minecraft.world.phys.AABB(0,0,6,1,1,7),new Vec3(.5,.5,0),new Vec3(.5,.5,6),true),
                    new Geometry("one ULP beyond six metres",new net.minecraft.world.phys.AABB(0,0,Math.nextUp(6D),1,1,7),new Vec3(.5,.5,0),new Vec3(.5,.5,6),false),
                    new Geometry("reverse native entry",unit,new Vec3(.5,.5,2),new Vec3(.5,.5,-1),true),
                    new Geometry("exact rational entry axis breaks double tie",new net.minecraft.world.phys.AABB(.3,.9,-.1,1,3,.1),Vec3.ZERO,new Vec3(1,3,0),true));
            for (Geometry g : cases) {
                @SuppressWarnings("unchecked") java.util.Optional<Vec3> hit =
                        (java.util.Optional<Vec3>)contact.invoke(null,g.box,g.start,g.end);
                h.assertTrue(hit.isPresent()==g.hit,"actual production contact: "+g.name);
                if (hit.isPresent()) {
                    Vec3 point=hit.get();
                    h.assertTrue(point.x>=g.box.minX&&point.x<=g.box.maxX&&point.y>=g.box.minY&&point.y<=g.box.maxY
                            &&point.z>=g.box.minZ&&point.z<=g.box.maxZ,"returned point remains in unexpanded box: "+g.name);
                    if (g.name.equals("ordinary native fast path"))
                        h.assertTrue(point.equals(g.box.clip(g.start,g.end).orElseThrow()),"existing native fast path stays byte-exact");
                    if (g.name.equals("actual crouched input4 Q item")) {
                        @SuppressWarnings("unchecked") java.util.Optional<Vec3> repeated =
                                (java.util.Optional<Vec3>)contact.invoke(null,g.box,g.start,point);
                        h.assertTrue(repeated.isPresent(),"real frozen contact survives exact closed endpoint revalidation");
                    }
                }
            }
            h.succeed();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Actual production contact reflection unavailable",e); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void five_active_q_anchors_charge_once_and_split_one_half_d_budget(GameTestHelper h) {
        double[] costs = {10, 5, 2, .5, .1}; int[] cooldowns = {10, 5, 4, 4, 4};
        for (int level = 1; level <= 5; level++) try (Fixture f = new Fixture(h, level)) {
            List<Zombie> targets = List.of(f.target(1.5, true), f.target(3, true), f.target(4.5, true), f.target(5.5, true));
            double cp = f.cp(), xp = AbilityStorage.get(f.p).levelExperience; long tick = h.getLevel().getGameTime();
            BigDecimal whole = total(level), q = HighPowerGrowth.values(f.p).position(), earned = HighPowerCombat.earnedGrowthPosition(f.p);
            eq(h, q, BigDecimal.valueOf(level), "actual certified active q anchor");
            h.assertTrue(f.cast(), "authenticated native yaw palm at active q " + level);
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < 3; i++) sum = sum.add(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(targets.get(i))));
            eq(h, sum, whole, "three hostile shares conserve exactly one half-D budget including final decimal residue");
            eq(h, hp(targets.get(3)), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "fourth actual contact receives no extra damage");
            var receipts = YawPalm.receipts(f.p);
            h.assertTrue(receipts.size() == 3, "one receipt per frozen mixed slot");
            for (int i = 0; i < 3; i++) {
                h.assertTrue(receipts.get(i).victim().equals(targets.get(i).getUUID()), "receipt retains native ordered UUID");
                eq(h, hp(targets.get(i)), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(receipts.get(i).damage()), "exact receipt share is actual unmitigated HP loss");
            }
            eq(h, receipts.stream().map(YawPalm.Receipt::damage).reduce(BigDecimal.ZERO, BigDecimal::add), whole, "receipt shares conserve the same whole budget");
            near(h, f.cp(), cp - costs[level - 1], "the entire three-slot palm pays once");
            h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ID, 0) == cooldowns[level - 1], "approved cooldown including physical four-tick floor");
            h.assertTrue(f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID) == tick + cooldowns[level - 1], "one persistent cooldown from first payment");
            eq(h, HighPowerGrowth.values(f.p).position(), q, "use never grows active q");
            eq(h, HighPowerCombat.earnedGrowthPosition(f.p), earned, "use never certifies earned Q");
            h.assertTrue(AbilityStorage.get(f.p).exp(ID) == 0 && AbilityStorage.get(f.p).levelExperience == xp, "neither mastery nor level XP is farmed");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void zero_through_three_hostiles_partition_damage_independently_of_owned_q_slots(GameTestHelper h) {
        for (int count = 0; count <= 3; count++) try (Fixture f = new Fixture(h, 2)) {
            List<Zombie> hostiles = new ArrayList<>(); List<ItemEntity> items = new ArrayList<>();
            for (int slot = 0; slot < 3; slot++) {
                if (slot < count) hostiles.add(f.target(1.5 + slot * 1.5, true));
                else items.add(f.toss(1.5 + slot * 1.5, slot + 2));
            }
            Zombie fourth = f.target(5.5, true); double cp = f.cp();
            h.assertTrue(f.cast(), "three mixed slots pay even when every admitted body is an owned Q drop");
            BigDecimal sum = hostiles.stream().map(target -> cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target))).reduce(BigDecimal.ZERO, BigDecimal::add);
            eq(h, sum, count == 0 ? BigDecimal.ZERO : total(2), "only the hostile partition divides the half-D total");
            eq(h, hp(fourth), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "item slots count toward the same maximum three contacts");
            for (ItemEntity item : items) vector(h, item.getDeltaMovement(), new Vec3(-.25, -.08, .04), "each ordinary Q receives one additive lateral impulse");
            near(h, f.cp(), cp - 5, "mixed partition has exactly one CP payment");
            h.assertTrue(YawPalm.receipts(f.p).size() == 3, "one bounded result per admitted mixed body");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void alternating_item_hostile_item_preserves_native_item_and_inventory_state(GameTestHelper h) {
        for (boolean crouch : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            f.p.setShiftKeyDown(crouch);
            ItemEntity first = f.toss(1.5, 3); Zombie target = f.target(3, true); ItemEntity third = f.toss(4.5, 7);
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 11));
            CompoundTag firstState = nativeItem(first), thirdState = nativeItem(third), inventory = inventory(f.p);
            UUID firstId = first.getUUID(), thirdId = third.getUUID(); Vec3 firstPos = first.position(), thirdPos = third.position();
            double firstY = first.getDeltaMovement().y, thirdY = third.getDeltaMovement().y;
            h.assertTrue(f.cast(), "alternating real Q and hostile contacts retain one ordered plan");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "single hostile owns the full half-D despite two Q slots");
            double side = crouch ? .25 : -.25;
            vector(h, first.getDeltaMovement(), new Vec3(side, firstY, .04), "default right and crouch left are exact additive side motion");
            vector(h, third.getDeltaMovement(), new Vec3(side, thirdY, .04), "later item keeps original Y and forward inertia");
            h.assertTrue(firstId.equals(first.getUUID()) && thirdId.equals(third.getUUID()) && first.position().equals(firstPos) && third.position().equals(thirdPos), "no item substitution, teleport, or pickup");
            h.assertTrue(nativeItem(first).equals(firstState) && nativeItem(third).equals(thirdState), "count, age, owner, thrower, delay, lifetime and native physics remain exact");
            h.assertTrue(inventory(f.p).equals(inventory), "both native hands and every inventory slot remain unchanged");
            var receipts = YawPalm.receipts(f.p);
            h.assertTrue(receipts.get(0).victim().equals(firstId) && receipts.get(1).victim().equals(target.getUUID()) && receipts.get(2).victim().equals(thirdId), "receipts retain mixed front-to-back identity");
            eq(h, receipts.get(0).damage(), BigDecimal.ZERO, "item has no HP authority"); eq(h, receipts.get(2).damage(), BigDecimal.ZERO, "second item also has no HP authority");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void native_hurt_redirects_one_same_call_impulse_and_keeps_resistance_and_y_formula(GameTestHelper h) {
        for (boolean crouch : List.of(false, true)) for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 2)) {
            f.p.setShiftKeyDown(crouch); Zombie target = f.target(3, true); f.discount(target);
            Zombie control = f.targetAt(f.eye().add(6, 0, 3), false);
            double resistance = mode == 1 ? .5 : mode == 2 ? 1 : 0; boolean grounded = mode != 3;
            Vec3 initial = new Vec3(.08, .03, -.02); double ratio = crouch ? -1 : 1;
            for (Zombie body : List.of(target, control)) { body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(resistance); body.setOnGround(grounded); body.setDeltaMovement(initial); }
            Observation actual = f.observe(target), ordinary = f.observe(control);
            f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent event) {
                if (event.getEntity() == control) { event.setStrength(.35F); event.setRatioX(ratio); event.setRatioZ(0); }
                if (event.getEntity() == target) {
                    near(h, event.getOriginalStrength(), .35F, "native hurt-owned call is redirected before ordinary listeners");
                    near(h, event.getOriginalRatioX(), ratio, "original event carries frozen lateral ratio");
                    near(h, event.getOriginalRatioZ(), 0, "no forward push is combined with the side impulse");
                }
            } });
            h.assertTrue(control.hurt(f.source(f.eye().add(control.position().subtract(target.position()))), 5), "ordinary native control enters hurt exactly once");
            h.assertTrue(f.cast(), "palm uses the native hurt path");
            h.assertTrue(actual.incoming == 1 && actual.posts == 1 && actual.knockbacks == 1 && ordinary.knockbacks == 1, "one native hurt and one native knockback call, never a second manual push");
            vector(h, target.getDeltaMovement(), control.getDeltaMovement(), "resistance and native grounded/airborne Y formula equal ordinary control");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "lateral redirection does not amplify native damage");
            assertSource(h, actual.source, f.p, f.eye());
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void canceling_native_knockback_keeps_external_velocity_without_refunding_damage(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); f.discount(target);
            Vec3 changed = new Vec3(.13, -.06, .21); Observation observed = f.observe(target);
            f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent event) {
                if (event.getEntity() == target) { event.getEntity().setDeltaMovement(changed); event.setCanceled(true); }
            } });
            double cp = f.cp(); h.assertTrue(f.cast(), "paid palm reaches cancellable same-call native impulse");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "completed native damage remains completed when its later impulse is canceled");
            vector(h, target.getDeltaMovement(), changed, "skill never overwrites a canceled event's external velocity");
            h.assertTrue(observed.knockbacks == 1, "canceled native impulse is never followed by a second push"); near(h, f.cp(), cp - 5, "cancel does not refund or repeat payment");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void actual_armor_toughness_enchantment_resistance_absorption_match_native_controls(GameTestHelper h) { defenseCase(h, true); }
    private static void defenseCase(GameTestHelper h, boolean exact) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(3, exact), control = f.targetAt(f.eye().add(6, 0, 3), false);
        defenses(h, target); defenses(h, control); f.discount(target); Observation actual = f.observe(target), ordinary = f.observe(control);
        h.runAfterDelay(2, () -> {
            BigDecimal before = hp(target); Vec3 origin = f.eye();
            h.assertTrue(control.hurt(f.source(origin.add(control.position().subtract(target.position()))), 5), "ordinary player-attack control accepts actual native reductions");
            h.assertTrue(f.cast(), "palm emits through ordinary native damage stages");
            h.assertTrue(actual.posts == 1 && ordinary.posts == 1 && actual.armor > 0 && actual.effects > 0 && actual.enchantments > 0 && actual.absorption == .5F, "every native reducer runs exactly once");
            h.assertTrue(actual.armor == ordinary.armor && actual.effects == ordinary.effects && actual.enchantments == ordinary.enchantments && actual.absorption == ordinary.absorption && Math.abs(actual.damage - ordinary.damage) < .00001, "native partial reductions match the real control");
            if (exact) {
                BigDecimal residual = DamageMitigation.mitigate(n("5"), new DefenseProfile(BigDecimal.valueOf(target.getArmorValue()), BigDecimal.valueOf(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)), 1, 4)).subtract(n(".5"));
                eq(h, hp(target), before.subtract(residual), "closed exact bridge keeps native mitigated units");
            } else near(h, target.getHealth(), control.getHealth(), "ordinary target retains ordinary native HP");
            assertSource(h, actual.source, f.p, origin); f.close(); if (exact) defenseCase(h, false); else h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 50)
    public static void true_origin_full_shield_keeps_both_ordinary_native_impulses_without_yaw_success(GameTestHelper h) { shieldCase(h, 0); }
    private static void shieldCase(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h, 2), nativeCaster = new Fixture(h, 2);
        nativeCaster.p.setPos(f.p.position().add(6, 0, 0)); nativeCaster.p.connection.resetPosition();
        Zombie target = f.target(3, mode < 2), control = nativeCaster.target(3, false);
        boolean blocked = mode % 2 == 0; float yaw = blocked ? 180 : 0;
        shield(target, yaw); shield(control, yaw); f.discount(target);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); control.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        Observation actual = f.observe(target), ordinary = f.observe(control);
        f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent event) {
            // Full block is a completely unmodified no-skill native control, including its .5 shield response.
            if (!blocked && event.getEntity() == control && event.getOriginalStrength() == .4F) { event.setStrength(.35F); event.setRatioX(1); event.setRatioZ(0); }
        } });
        h.runAfterDelay(7, () -> {
            face(target, yaw); face(control, yaw); h.assertTrue(target.isBlocking() && control.isBlocking(), "actual shield raising delay elapsed");
            target.setDeltaMovement(Vec3.ZERO); control.setDeltaMovement(Vec3.ZERO); BigDecimal before = hp(target); Vec3 origin = f.eye();
            Vec3 ownerBefore = f.p.getDeltaMovement();
            boolean accepted = control.hurt(nativeCaster.source(nativeCaster.eye()), 5);
            double cp = f.cp(); h.assertTrue(f.cast(), "palm pays before actual native shield result");
            h.assertTrue(accepted != blocked && actual.blocked == blocked && ordinary.blocked == blocked, "true source origin controls front/rear shield; side motion never fakes attack origin");
            eq(h, hp(target), blocked ? before : before.subtract(n("5")), "fully blocked native damage never becomes HP loss");
            int expectedCalls = blocked ? 2 : 1;
            h.assertTrue(actual.knockbacks == expectedCalls && ordinary.knockbacks == expectedCalls,
                    "native shield/hurt call counts match unmodified control: mode=" + mode + " actual=" + actual.originalImpulses + " control=" + ordinary.originalImpulses);
            vector(h, target.getDeltaMovement(), control.getDeltaMovement(), "full shield preserves both ordinary native impulses; rear shield keeps one side impulse");
            if (blocked) {
                h.assertTrue(actual.originalImpulses.equals(ordinary.originalImpulses), "both original native .5/.4 strengths and source-relative ratios exactly match no-skill control");
                h.assertTrue(actual.originalKnockbackStrengths.equals(List.of(.5F, .4F)), "full held shield keeps the native response followed by the ordinary hurt-owned operation");
                h.assertTrue(YawPalm.receipts(f.p).stream().allMatch(receipt -> receipt.status() == YawPalm.REFUSED && receipt.velocityDelta().equals(Vec3.ZERO)), "ordinary shield motion emits no successful damage, absorption or side-arrow receipt; a geometry-valid refusal alone may remain");
                vector(h, f.p.getDeltaMovement(), ownerBefore, "native shield response is applied to the same native recipient as the control");
            } else h.assertTrue(target.getDeltaMovement().x < 0 && target.getDeltaMovement().z == 0, "rear shield still permits the actual native lateral hurt impulse");
            near(h, f.cp(), cp - 5, "full block retains one paid CP debit"); assertSource(h, actual.source, f.p, origin);
            f.close(); nativeCaster.close(); if (mode < 3) shieldCase(h, mode + 1); else h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void native_iframe_equal_and_residual_paths_never_invent_missing_knockback(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (boolean residual : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, exact); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); f.discount(target);
            h.assertTrue(target.hurt(f.p.damageSources().playerAttack(f.p), residual ? 3 : 5), "real ordinary native hit establishes hurt cooldown");
            h.assertTrue(target.invulnerableTime > 10, "actual native iframe branch is active");
            target.setDeltaMovement(Vec3.ZERO); BigDecimal before = hp(target); Observation observed = f.observe(target);
            double cp = f.cp(); h.assertTrue(f.cast(), "paid cast enters existing native cooldown decision");
            eq(h, hp(target), residual ? before.subtract(n("2")) : before, "only native residual survives the genuine iframe comparison");
            h.assertTrue(observed.knockbacks == 0, "native cooldown residual has no hurt-owned knockback call to redirect");
            vector(h, target.getDeltaMovement(), Vec3.ZERO, "skill creates no fallback or post-hurt push"); near(h, f.cp(), cp - 5, "native iframe result pays once");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void native_damage_vetoes_never_become_hp_debits_or_fallback_impulses(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, exact); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); f.discount(target);
            int kind = mode; boolean[] called = {false}; Observation observed = f.observe(target);
            f.hook(new Object() {
                @SubscribeEvent public void skill(SkillAttackEvent event) { if (event.target == target && kind == 0) { called[0] = true; event.amount = 0; } }
                @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target && kind == 1) { called[0] = true; event.setCanceled(true); } }
                @SubscribeEvent public void pre(LivingDamageEvent.Pre event) { if (event.getEntity() == target && kind == 2) { called[0] = true; event.setNewDamage(0); } }
            });
            BigDecimal before = hp(target); double cp = f.cp(); h.assertTrue(f.cast() && called[0], "real native protection callback executes");
            eq(h, hp(target), before, "zero or canceled native damage grants no exact HP debit");
            if (mode < 2) { h.assertTrue(observed.knockbacks == 0, "early native veto has no owned knockback call"); vector(h, target.getDeltaMovement(), Vec3.ZERO, "no fallback push after early native refusal"); }
            near(h, f.cp(), cp - 5, "late protection does not refund or repeat paid emission");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void walls_and_unapproved_physical_bodies_stop_the_mixed_path_without_payment(GameTestHelper h) {
        for (int mode = 0; mode < 6; mode++) try (Fixture f = new Fixture(h, 2)) {
            Zombie rear = f.target(4.5, true); ItemEntity rearItem = f.toss(5.5, 2); Vec3 motion = rearItem.getDeltaMovement();
            switch (mode) {
                case 0 -> f.block(f.eye().add(0, 0, 2));
                case 1 -> f.add(new Cow(EntityType.COW, h.getLevel()), f.eye().add(0, 0, 2));
                case 2 -> f.add(new Husk(EntityType.HUSK, h.getLevel()) { }, f.eye().add(0, 0, 2));
                case 3 -> { ItemEntity raw = f.raw(2, new ItemStack(Items.IRON_INGOT)); raw.setThrower(f.p); raw.setTarget(f.p.getUUID()); }
                case 4 -> { ItemEntity decorated = f.toss(2, 2); decorated.getItem().set(DataComponents.CUSTOM_NAME, Component.literal("not an ordinary Q")); }
                default -> { Zombie protectedBody = f.target(2, true); protectedBody.getPersistentData().putBoolean("academy:protected", true); }
            }
            double cp = f.cp(); h.assertFalse(f.cast(), "unapproved first physical contact is a blocker, mode " + mode);
            eq(h, hp(rear), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "no hostile HP through an unknown or protected body");
            vector(h, rearItem.getDeltaMovement(), motion, "no Q impulse through an unknown or protected body");
            near(h, f.cp(), cp, "first-contact refusal is free"); f.assertBlocks();
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void genuinely_foreign_q_proof_cannot_be_laundered_with_native_owner_fields(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2); Fixture foreign = new Fixture(h, 2)) {
            ItemEntity item = foreign.toss(2, 5);
            foreign.p.setPos(f.p.position().add(10, 0, 0)); foreign.p.connection.resetPosition();
            item.setThrower(f.p); item.setTarget(f.p.getUUID()); Zombie rear = f.target(4, true);
            h.assertTrue(SpatialRelayProvenance.proof(item) != null && SpatialRelayProvenance.proof(item).owner().equals(foreign.p.getUUID()), "actual native Q retained another connected player's independently certified origin");
            Vec3 motion = item.getDeltaMovement(); double cp = f.cp(); h.assertFalse(f.cast(), "thrower and target fields do not transfer authenticated Q authority");
            vector(h, item.getDeltaMovement(), motion, "foreign real Q keeps its motion"); eq(h, hp(rear), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "foreign Q also blocks a later hostile"); near(h, f.cp(), cp, "foreign first contact is free");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void intervening_wall_truncates_admission_after_the_first_slot_without_rebudgeting(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie first = f.target(1.5, true), rear = f.target(4.5, true); ItemEntity item = f.toss(5.5, 2); Vec3 motion = item.getDeltaMovement();
            f.block(f.eye().add(0, 0, 3)); double cp = f.cp(); h.assertTrue(f.cast(), "front hostile remains an actual opportunity before the wall");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "only admitted hostile owns the half-D total"); eq(h, hp(rear), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "wall protects rear hostile");
            vector(h, item.getDeltaMovement(), motion, "wall protects rear Q drop"); h.assertTrue(YawPalm.receipts(f.p).size() == 1, "blocked contacts never enter the frozen plan");
            near(h, f.cp(), cp - 5, "front opportunity pays exactly once"); f.assertBlocks();
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void exact_entry_ties_are_uuid_ordered_before_the_three_contact_cap(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Vec3 at = f.eye().add(0, 0, 3); Zombie fourth = f.targetAt(at, true, f.uuid(4)), third = f.targetAt(at, true, f.uuid(3)), second = f.targetAt(at, true, f.uuid(2)), first = f.targetAt(at, true, f.uuid(1));
            h.assertTrue(f.cast(), "native equal-entry bodies form one frozen three-slot palm");
            var receipt = YawPalm.receipts(f.p);
            h.assertTrue(receipt.size() == 3 && receipt.get(0).victim().equals(first.getUUID()) && receipt.get(1).victim().equals(second.getUUID()) && receipt.get(2).victim().equals(third.getUUID()), "UUID tie order does not depend on native entity insertion order");
            eq(h, hp(fourth), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "UUID-last fourth body remains outside the mixed cap");
            vector(h, receipt.get(0).end(), receipt.get(2).end(), "all three frozen entries remain exactly tied");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void tied_unknown_item_or_nonhostile_contact_precedes_eligible_hostiles(GameTestHelper h) {
        for (boolean item : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            Zombie hostile = f.target(3, true); Entity blocker;
            if (item) blocker = f.raw(3, new ItemStack(Items.DIRT));
            else { Cow cow = new Cow(EntityType.COW, h.getLevel()); f.add(cow, f.eye().add(0, 0, 3)); blocker = cow; }
            f.center(blocker, new Vec3(f.eye().x, f.eye().y, hostile.getBoundingBox().minZ + blocker.getBbWidth() / 2));
            h.assertTrue(blocker.getBoundingBox().minZ == hostile.getBoundingBox().minZ, "native entry tie is exact");
            double cp = f.cp(); h.assertFalse(f.cast(), "an ineligible physical tie wins over every eligible UUID"); eq(h, hp(hostile), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "tied obstruction prevents damage"); near(h, f.cp(), cp, "tied obstruction refuses before payment");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void reach_endpoint_and_thin_radius_use_actual_native_contacts(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true); double half = target.getBbWidth() / 2;
            boolean inside = mode % 2 == 0;
            if (mode < 2) f.center(target, f.eye().add(half + (inside ? .034 : .036), 0, 3));
            else f.center(target, f.eye().add(0, 0, YawPalmRules.RANGE + half + (inside ? .034 : .036)));
            double cp = f.cp(); h.assertTrue(f.cast() == inside, "closed ray endpoint and .035 radius remain independent of body centers, case " + mode);
            eq(h, hp(target), inside ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)) : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "no broad nearby-body or excess-range damage"); near(h, f.cp(), cp - (inside ? 5 : 0), "geometric misses are free");
            if (inside) for (var receipt : YawPalm.receipts(f.p)) h.assertTrue(receipt.origin().distanceTo(receipt.end()) <= YawPalmRules.RANGE, "actual visual contact never extends the configured path");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void exactly_sixty_four_scan_bodies_admit_but_sixty_five_refuse_the_whole_cast(GameTestHelper h) {
        for (int count : new int[] {64, 65}) try (Fixture f = new Fixture(h, 2)) {
            List<Zombie> bodies = new ArrayList<>();
            for (int i = 0; i < count; i++) bodies.add(f.targetAt(f.eye().add(0, 0, 3), true, f.uuid(i + 1)));
            double cp = f.cp(); h.assertTrue(f.cast() == (count == 64), "bounded scan accepts exactly 64 and rejects 65 before selecting any arbitrary prefix");
            BigDecimal sum = bodies.stream().map(target -> cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target))).reduce(BigDecimal.ZERO, BigDecimal::add);
            eq(h, sum, count == 64 ? total(2) : BigDecimal.ZERO, "scan overflow cannot emit even one partial HP share");
            near(h, f.cp(), cp - (count == 64 ? 5 : 0), "overflow is entirely free");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void frozen_invalidated_share_is_lost_and_new_arrivals_cannot_take_it(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie first = f.target(1.5, true), second = f.target(3, true), third = f.target(4.5, true); Zombie[] newcomer = {null};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() == first && newcomer[0] == null) { second.discard(); newcomer[0] = f.target(5.5, true); }
            } });
            double cp = f.cp(); h.assertTrue(f.cast() && newcomer[0] != null, "real first native callback mutates later frozen contact membership");
            BigDecimal share = total(2).divide(BigDecimal.valueOf(3), CombatAmounts.MAX_ABSOLUTE_SCALE, java.math.RoundingMode.DOWN).stripTrailingZeros();
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "first share remains the original one-third of half-D");
            eq(h, hp(third), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2).subtract(share.multiply(BigDecimal.valueOf(2)))), "last frozen body retains its original remainder and no discarded share");
            eq(h, hp(newcomer[0]), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "new arrival cannot replace invalidated selection"); near(h, f.cp(), cp - 5, "late lost share neither repeats nor refunds payment");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void callback_item_replacement_count_or_owner_mutation_cannot_receive_frozen_impulse(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 2)) {
            Zombie first = f.target(1.5, true); ItemEntity item = f.toss(3, 4); Vec3 motion = item.getDeltaMovement(); int kind = mode;
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != first) return;
                switch (kind) {
                    case 0 -> item.setItem(item.getItem().copy());
                    case 1 -> item.getItem().shrink(1);
                    case 2 -> item.setTarget(UUID.randomUUID());
                    default -> item.discard();
                }
            } });
            h.assertTrue(f.cast(), "first native callback invalidates an independently frozen Q medium");
            vector(h, item.getDeltaMovement(), motion, "changed stack identity, count, owner or existence cannot receive an impulse");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "one actual hostile retains its own full partition allocation");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void incoming_owner_death_epoch_logout_or_geometry_changes_retire_authority(GameTestHelper h) {
        for (int mode = 0; mode < 10; mode++) try (Fixture f = new Fixture(h, 2)) {
            Zombie first = f.target(1.5, true), later = f.target(4.5, true); ItemEntity item = f.toss(3, 2); Vec3 motion = item.getDeltaMovement(); int kind = mode; boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != first || called[0]) return; called[0] = true;
                switch (kind) {
                    case 0 -> AbilityStorage.get(f.p).category = "electromaster";
                    case 1 -> AbilityStorage.get(f.p).presets.switchTo(1);
                    case 2 -> LegacySingleKeyOwnerEpoch.forget(f.p);
                    case 3 -> f.p.setHealth(0);
                    case 4 -> YawPalm.abort(f.p);
                    case 5 -> AbilityStorage.remove(f.p);
                    case 6 -> f.p.setPos(f.p.position().add(1, 0, 0));
                    case 7 -> f.p.setYRot(90);
                    case 8 -> f.block(f.eye().add(0, 0, .7));
                    default -> f.p.server.getPlayerList().remove(f.p);
                }
            } });
            h.assertTrue(f.cast() && called[0], "real native callback mutates the owning cast, case " + mode);
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "late owner guard protects current exact HP"); eq(h, hp(later), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "remaining hostile authority retires with owner");
            vector(h, item.getDeltaMovement(), motion, "later frozen Q cannot outlive its authority");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void native_target_callback_mutations_cannot_turn_stale_contact_into_hp_or_side_motion(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (int mode = 0; mode < 5; mode++) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, exact); f.discount(target); BigDecimal before = hp(target); int kind = mode; boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != target || called[0]) return; called[0] = true;
                switch (kind) {
                    case 0 -> target.setPos(target.position().add(2, 0, 0));
                    case 1 -> target.setBoundingBox(target.getBoundingBox().inflate(.05));
                    case 2 -> target.setDeltaMovement(.12, .04, .2);
                    case 3 -> target.setOnGround(!target.onGround());
                    default -> target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(.5);
                }
            } });
            h.assertTrue(f.cast() && called[0], "actual native incoming callback mutates captured body state"); eq(h, hp(target), before, "frozen state mismatch refuses before exact or ordinary HP write");
            if (mode != 2) vector(h, target.getDeltaMovement(), Vec3.ZERO, "stale contact cannot receive a manufactured side impulse");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void native_final_pre_damage_callback_rechecks_geometry_and_closed_amount(GameTestHelper h) {
        for (boolean exact : List.of(true, false)) for (boolean amplify : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, exact); f.discount(target); BigDecimal before = hp(target); boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void pre(LivingDamageEvent.Pre event) {
                if (event.getEntity() == target) { called[0] = true; if (amplify) event.setNewDamage(event.getNewDamage() * 2); else target.setPos(target.position().add(2, 0, 0)); }
            } });
            double cp = f.cp(); h.assertTrue(f.cast() && called[0], "actual final native pre-damage observer mutates the admitted hit");
            eq(h, hp(target), before, "guard rejects post-incoming geometry change and closed-share amplification before HP commit"); near(h, f.cp(), cp - 5, "native refusal keeps exactly one irreversible debit");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void captured_actual_native_source_cannot_recurse_change_victim_or_replay(GameTestHelper h) {
        for (boolean wrongVictim : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true), other = f.targetAt(f.eye().add(5, 0, 3), true); f.discount(target);
            DamageSource[] captured = {null};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != target || captured[0] != null) return; captured[0] = event.getSource();
                h.assertFalse((wrongVictim ? other : target).hurt(captured[0], 5), "captured source grants neither recursive native hurt nor a second target");
            } });
            double cp = f.cp(); h.assertTrue(f.cast() && captured[0] != null, "real paid native hit exposes the actual target-bound source");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "recursive source poisons outer closed authority before HP commit"); eq(h, hp(other), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "captured source cannot transfer HP authority");
            h.assertFalse(other.hurt(captured[0], 5), "finished native source cannot replay on another target");
            h.assertTrue(other.hurt(f.p.damageSources().playerAttack(f.p), 5), "independent ordinary native hurt still works after guarded source cleanup");
            eq(h, hp(other), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "ordinary player attack retains only its ordinary five units"); near(h, f.cp(), cp - 5, "poisoned attempt still paid once");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void original_and_fresh_inputs_cannot_reenter_a_paid_native_callback(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie first = f.target(2, true), later = f.target(4, true); String wire = f.wire(); int[] callbacks = {0};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != first) return; callbacks[0]++;
                h.assertFalse(YawPalm.tap(f.p, wire), "same wire cannot enter its processing native callback");
                h.assertFalse(f.cast(), "fresh input cannot recursively start another processing controller");
            } });
            double cp = f.cp(); h.assertTrue(YawPalm.tap(f.p, wire), "outer authenticated emission remains authoritative");
            eq(h, hp(first), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2).divide(BigDecimal.valueOf(2))), "first gets only its original half share");
            eq(h, hp(later), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2).divide(BigDecimal.valueOf(2))), "later target gets no reentrant duplicate share");
            h.assertTrue(callbacks[0] == 1, "one actual first-target native incoming pipeline"); near(h, f.cp(), cp - 5, "callback reentry cannot pay again");
            YawPalm.abort(f.p); h.assertFalse(YawPalm.tap(f.p, wire), "completion and explicit abort cannot recycle consumed wire identity");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void recursive_native_knockback_cannot_borrow_the_outer_hurt_owned_call(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true), control = f.targetAt(f.eye().add(6, 0, 3), false); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); control.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); f.discount(target); boolean[] nested = {false};
            f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent event) {
                if (event.getEntity() != target || nested[0]) return; nested[0] = true; target.knockback(.4F, 0, -1);
            } });
            control.knockback(.4F, 0, -1); double cp = f.cp(); h.assertTrue(f.cast() && nested[0], "actual owned native event invokes a recursive native impulse");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "already completed damage is not replayed or refunded by impulse recursion");
            vector(h, target.getDeltaMovement(), control.getDeltaMovement(), "nested ordinary call retains exactly its native control velocity while poisoning the owned outer lateral impulse"); near(h, f.cp(), cp - 5, "recursive impulse has no second payment");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void native_knockback_amplification_reversal_and_owner_callback_changes_are_rejected(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); f.discount(target); int kind = mode; boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent event) {
                if (event.getEntity() != target || called[0]) return; called[0] = true;
                switch (kind) {
                    case 0 -> event.setStrength(.7F);
                    case 1 -> event.setRatioX(-event.getRatioX());
                    case 2 -> f.p.setYRot(90);
                    default -> f.p.setDeltaMovement(.1, .02, .04);
                }
            } });
            h.assertTrue(f.cast() && called[0], "real same-call native event reaches late mutation callback");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "completed native HP debit remains singular");
            vector(h, target.getDeltaMovement(), Vec3.ZERO, "closed native redirect rejects amplified, reversed or stale-owner force");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void direct_native_knockback_and_forged_skill_names_have_no_yaw_damage_authority(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            double cp = f.cp(); h.assertFalse(HighPowerCombat.attack(f.p, SKILL, target, n("5")), "generic skill text cannot mint the private yaw emission permit");
            target.knockback(.4F, 0, -1);
            h.assertTrue(target.getDeltaMovement().z > 0 && target.getDeltaMovement().x == 0, "a genuine unowned native impulse retains ordinary forward direction");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "unowned native impulse carries no skill HP authority"); near(h, f.cp(), cp, "forged damage and ordinary external motion cannot charge skill CP");
            target.setDeltaMovement(Vec3.ZERO); f.discount(target); h.assertTrue(f.cast(), "proper authenticated cast still has its own independent real source");
            h.assertTrue(target.getDeltaMovement().x < 0 && target.getDeltaMovement().z == 0, "only actual owned hurt call gets the side direction");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void ordinary_knockback_inside_incoming_cannot_impersonate_later_owned_native_impulse(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); f.discount(target); boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != target || called[0]) return; called[0] = true; target.knockback(.4F, 0, -1);
            } });
            h.assertTrue(f.cast() && called[0], "real incoming listener invokes a native call without the verified hurt callsite");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "external velocity mutation invalidates frozen damage before HP commit");
            h.assertTrue(target.getDeltaMovement().z > 0 && target.getDeltaMovement().x == 0, "unowned callback motion is not stolen or overwritten by a lateral fallback");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void item_motion_bounds_refuse_excess_additive_speed_without_clamping_or_erasing_y(GameTestHelper h) {
        Vec3[] initial = {new Vec3(-.25, -.37, 0), new Vec3(-.251, -.37, 0), new Vec3(.501, -.37, 0), new Vec3(Double.NaN, -.37, 0)};
        for (int mode = 0; mode < initial.length; mode++) try (Fixture f = new Fixture(h, 2)) {
            ItemEntity item = f.toss(3, 4); item.setDeltaMovement(initial[mode]); Vec3 before = item.getDeltaMovement(); double cp = f.cp();
            h.assertTrue(f.cast() == (mode == 0), "output .5 boundary is closed, excess output/input and nonfinite motion refuse, case " + mode);
            if (mode == 0) vector(h, item.getDeltaMovement(), new Vec3(-.5, -.37, 0), "exact capped-boundary additive impulse preserves Y");
            else h.assertTrue(item.getDeltaMovement().equals(before), "refused result never clamps speed or rewrites native Y");
            near(h, f.cp(), cp - (mode == 0 ? 5 : 0), "unsafe first item is free");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void predicted_native_horizontal_overflow_refuses_before_damage_or_payment(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); Vec3 before = new Vec3(-.31, .07, 0); target.setDeltaMovement(before);
            double cp = f.cp(); h.assertFalse(f.cast(), "native .35 plus half current motion would exceed the .5 horizontal bound");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "unaffordable native-result bound refuses before damage"); vector(h, target.getDeltaMovement(), before, "preflight never normalizes or clamps native motion"); near(h, f.cp(), cp, "unsafe first native body is free");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void forged_actor_epoch_slot_noncanonical_and_cancel_before_start_are_free(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true); double cp = f.cp(); long epoch = f.epoch();
            h.assertFalse(YawPalm.tap(f.p, new HighPowerInput.Input(0, 1, f.p.getId() + 100, epoch).wire()), "wrong connected actor cannot authenticate");
            h.assertFalse(YawPalm.tap(f.p, new HighPowerInput.Input(0, 2, f.p.getId(), epoch + 1).wire()), "wrong owner epoch cannot authenticate");
            h.assertFalse(YawPalm.tap(f.p, new HighPowerInput.Input(1, 3, f.p.getId(), epoch).wire()), "unbound offhand-style input slot has no authority");
            h.assertFalse(YawPalm.tap(f.p, "0:0004:" + f.p.getId() + ":" + epoch), "noncanonical spellings cannot create alternate replay identities");
            String canceled = new HighPowerInput.Input(0, 5, f.p.getId(), epoch).wire(); YawPalm.abort(f.p, canceled);
            h.assertFalse(YawPalm.tap(f.p, canceled), "valid cancel before start retires exactly that input identity");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "all unauthenticated and canceled requests leave HP untouched"); near(h, f.cp(), cp, "all unauthenticated and canceled requests are free");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void physical_cooldown_survives_abort_native_owner_reload_and_a_fresh_epoch(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); Zombie first = f.target(3, true); long tick = h.getLevel().getGameTime();
        h.assertTrue(f.cast(), "initial level-five native palm"); YawPalm.abort(f.p);
        AbilityStorage.save(f.p); CompoundTag saved = f.p.saveWithoutId(new CompoundTag());
        YawPalm.remove(f.p); AbilityStorage.remove(f.p); LegacySingleKeyOwnerEpoch.forget(f.p); f.p.load(saved); f.place(); f.inputs = 0;
        first.discard(); Zombie next = f.target(3, true);
        h.runAfterDelay(1, () -> {
            f.p.doTick(); f.place(); double cp = f.cp(); h.assertTrue(h.getLevel().getGameTime() - tick < 4, "native reload probe is inside physical four-tick minimum");
            h.assertFalse(f.cast(), "new owner epoch cannot erase paid physical cooldown"); near(h, f.cp(), cp, "premature reload replay remains free"); eq(h, hp(next), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "no early HP hit survives reload");
        });
        h.runAfterDelay(5, () -> {
            f.p.doTick(); f.place(); f.center(next, f.eye().add(0, 0, 3)); double cp = f.cp();
            h.assertTrue(h.getLevel().getGameTime() - tick >= 4 && f.cast(), "later fresh native input works after physical fence expires");
            near(h, f.cp(), cp - .1, "legitimate later palm pays once"); eq(h, hp(next), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(5)), "reloaded owner retains the half-D budget"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void living_vertical_bound_is_closed_while_owned_item_y_remains_unmodified(GameTestHelper h) {
        for (double y : new double[] {1, -1, Math.nextUp(1.0), Math.nextDown(-1.0)}) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); target.setOnGround(false); target.setDeltaMovement(0, y, 0);
            boolean bounded = Math.abs(y) <= 1; double cp = f.cp(); h.assertTrue(f.cast() == bounded, "living vertical velocity has exact closed magnitude-one preflight bound");
            eq(h, hp(target), bounded ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)) : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "out-of-bound native Y cannot buy damage without an affordable native impulse");
            near(h, target.getDeltaMovement().y, y, "native airborne impulse never resets or clamps original Y"); near(h, f.cp(), cp - (bounded ? 5 : 0), "vertical refusal is free");
        }
        try (Fixture f = new Fixture(h, 2)) {
            ItemEntity item = f.toss(3, 2); item.setDeltaMovement(0, -2, 0); h.assertTrue(f.cast(), "finite native Q Y does not inherit living knockback bounds");
            vector(h, item.getDeltaMovement(), new Vec3(-.25, -2, 0), "item additive side impulse preserves its full finite Y exactly");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void immovable_hostile_is_a_physical_blocker_for_every_later_mixed_slot(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie immovable = f.target(1.5, true), rear = f.target(4.5, true); ItemEntity item = f.toss(3, 2); Vec3 motion = item.getDeltaMovement();
            immovable.getPersistentData().putBoolean("academy:immovable", true); double cp = f.cp(); h.assertFalse(f.cast(), "native immovable hostile blocks admission before payment");
            eq(h, hp(immovable), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "immovable protection cannot be bypassed by half-D damage"); eq(h, hp(rear), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "later hostile remains protected behind immovable body");
            vector(h, item.getDeltaMovement(), motion, "later Q receives no impulse through immovable contact"); near(h, f.cp(), cp, "immovable refusal remains free");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void genuine_native_entity_manager_unload_invalidates_current_or_later_frozen_identity(GameTestHelper h) {
        for (boolean current : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(1.5, true); ItemEntity item = f.toss(3, 3); Entity removed = current ? target : item; Vec3 itemMotion = item.getDeltaMovement(); boolean[] called = {false};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != target || called[0]) return; called[0] = true; nativeUnload(h, removed);
                h.assertTrue(removed.isRemoved() && removed.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK && h.getLevel().getEntity(removed.getUUID()) == null, "actual native entity-manager unload removes both live lookup and identity");
            } });
            double cp = f.cp(); h.assertTrue(f.cast() && called[0], "real incoming callback reaches native unload before the selected slot commits");
            eq(h, hp(target), current ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth() : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(total(2)), "native unload cannot authorize a stale current HP write");
            vector(h, item.getDeltaMovement(), current ? itemMotion.add(-.25, 0, 0) : itemMotion, "unloaded Q loses its own impulse; an independent surviving frozen Q keeps only its original impulse"); near(h, f.cp(), cp - 5, "unload after emission retains exactly one payment");
        }
        h.succeed();
    }
    private static void nativeUnload(GameTestHelper h, Entity entity) {
        try {
            var field = ServerLevel.class.getDeclaredField("entityManager"); field.setAccessible(true);
            var method = net.minecraft.world.level.entity.PersistentEntitySectionManager.class.getDeclaredMethod("unloadEntity", net.minecraft.world.level.entity.EntityAccess.class);
            method.setAccessible(true); method.invoke(field.get(h.getLevel()), entity);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native entity-manager unload unavailable", failure); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void lethal_native_hit_retains_its_one_same_call_side_impulse_on_ordinary_and_exact_health(GameTestHelper h) {
        for (boolean exact : List.of(false, true)) try (Fixture f = new Fixture(h, 5)) {
            Zombie target = f.target(3, false), control = f.targetAt(f.eye().add(6, 0, 3), false);
            if (exact) h.assertTrue(HighPowerCombat.enroll(target, 1), "declared exact low-health native hostile for actual lethal whole-budget test");
            else { target.setHealth(1); f.discount(target); }
            control.setHealth(1);
            for (Zombie body : List.of(target, control)) { body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); body.setDeltaMovement(Vec3.ZERO); body.setOnGround(false); }
            Observation actual = f.observe(target), ordinary = f.observe(control);
            f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent event) {
                if (event.getEntity() == control && event.getOriginalStrength() == .4F) { event.setStrength(.35F); event.setRatioX(1); event.setRatioZ(0); }
                if (event.getEntity() == target) near(h, event.getOriginalStrength(), .35F, "actual own lethal hit preserves its authenticated native redirection");
            } });
            h.assertTrue(control.hurt(f.source(f.eye().add(control.position().subtract(target.position()))), 5), "native control suffers an actual lethal player attack");
            h.assertTrue(f.cast(), "ordinary or exact lethal palm follows the complete native hurt call");
            h.assertTrue(!target.isAlive() && !control.isAlive() && actual.knockbacks == 1 && ordinary.knockbacks == 1, "both lethal native hurt calls reach exactly one native impulse");
            vector(h, target.getDeltaMovement(), control.getDeltaMovement(), "owned death transition preserves native Y and side force without a second push");
            h.assertTrue(target.getDeltaMovement().x < 0 && target.getDeltaMovement().z == 0, "own native lethal settlement cannot accidentally lose its lateral impulse");
            h.assertTrue(YawPalm.receipts(f.p).getFirst().status() == YawPalm.DAMAGED && target.getUUID().equals(YawPalm.receipts(f.p).getFirst().victim()), "actual own lethal death retains its damaged receipt and original UUID");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void three_lethal_native_bodies_keep_all_frozen_shares_and_side_impulses(GameTestHelper h) {
        for (boolean exact : List.of(false, true)) try (Fixture f = new Fixture(h, 5)) {
            List<Zombie> targets = List.of(f.target(1.5, false), f.target(3, false), f.target(4.5, false));
            List<Observation> observed = new ArrayList<>();
            for (Zombie target : targets) {
                if (exact) h.assertTrue(HighPowerCombat.enroll(target, 1), "declared exact low-health native body"); else { target.setHealth(1); f.discount(target); }
                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); observed.add(f.observe(target));
            }
            double cp = f.cp(); h.assertTrue(f.cast(), "three actual lethal native opportunities share one frozen palm");
            for (int i = 0; i < targets.size(); i++) {
                Zombie target = targets.get(i); Observation hit = observed.get(i);
                h.assertTrue(!target.isAlive() && hit.incoming == 1 && hit.knockbacks == 1, "earlier owned death pose cannot invalidate later frozen allocation " + i);
                h.assertTrue(target.getDeltaMovement().x < 0 && target.getDeltaMovement().z == 0, "each lethal body receives one native side impulse " + i);
            }
            h.assertTrue(YawPalm.receipts(f.p).size() == 3, "three completed lethal native slots retain all three bounded receipts");
            for (int i = 0; i < 3; i++) h.assertTrue(YawPalm.receipts(f.p).get(i).status() == YawPalm.DAMAGED && targets.get(i).getUUID().equals(YawPalm.receipts(f.p).get(i).victim()), "native DYING pose preserves only the actual settled damaged identity " + i);
            near(h, f.cp(), cp - .1, "lethal chain still pays only once");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void callback_killed_target_cannot_impersonate_the_casts_own_native_death_settlement(GameTestHelper h) {
        for (boolean late : List.of(false, true)) try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, false); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); f.discount(target); boolean[] called = {false};
            f.hook(new Object() {
                @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (!late && event.getEntity() == target && !called[0]) { called[0] = true; target.setHealth(0); } }
                @SubscribeEvent public void post(LivingDamageEvent.Post event) { if (late && event.getEntity() == target && !called[0]) { called[0] = true; target.setHealth(0); } }
            });
            h.assertTrue(f.cast() && called[0], "external callback sets HP zero independently of the five-point native palm");
            h.assertTrue(target.getDeltaMovement().x == 0, "external death may not borrow the narrow owned-native lethal side impulse exception");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void native_partial_shield_preserves_ordinary_residual_and_keeps_exact_ledger_refusal(GameTestHelper h) { partialShieldCase(h, false); }
    private static void partialShieldCase(GameTestHelper h, boolean exact) {
        Fixture f = new Fixture(h, 2), nativeCaster = new Fixture(h, 2);
        nativeCaster.p.setPos(f.p.position().add(6, 0, 0)); nativeCaster.p.connection.resetPosition();
        Zombie target = f.target(3, exact), control = nativeCaster.target(3, false);
        shield(target, 180); shield(control, 180); f.discount(target);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); control.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        f.hook(new Object() { @SubscribeEvent(priority = EventPriority.HIGHEST) public void shield(LivingShieldBlockEvent event) {
            if (event.getEntity() == target || event.getEntity() == control) { h.assertTrue(event.getOriginalBlock(), "real front shield supplies native partial-block opportunity"); event.setBlockedDamage(2); }
        } });
        Observation actual = f.observe(target), ordinary = f.observe(control);
        h.runAfterDelay(7, () -> {
            face(target, 180); face(control, 180); h.assertTrue(target.isBlocking() && control.isBlocking(), "real partial shields are fully raised");
            target.setDeltaMovement(Vec3.ZERO); control.setDeltaMovement(Vec3.ZERO); BigDecimal before = hp(target); double cp = f.cp();
            h.assertTrue(control.hurt(nativeCaster.source(nativeCaster.eye()), 5), "unmodified native partial-block control accepts three residual points");
            h.assertTrue(f.cast(), "palm enters the same partial native shield block");
            eq(h, hp(target), exact ? before : before.subtract(n("3")), "ordinary partial block keeps native three-point residual; existing exact-ledger partial-block refusal remains closed"); near(h, control.getHealth(), 17, "independent ordinary control confirms the actual native residual");
            h.assertTrue(actual.blocked && actual.blockedDamage == 2 && ordinary.blockedDamage == 2, "both paths use the actual bounded partial shield result");
            h.assertTrue(actual.originalImpulses.equals(ordinary.originalImpulses) && actual.originalKnockbackStrengths.equals(List.of(.5F, .4F)), "partial shield keeps both ordinary native strengths and source-relative ratios");
            vector(h, target.getDeltaMovement(), exact ? ordinary.beforeImpulses.get(1) : control.getDeltaMovement(), "ordinary partial block retains both native calls; rejected exact source retains completed ordinary shield response without a new impulse");
            h.assertTrue(YawPalm.receipts(f.p).stream().allMatch(receipt -> (exact ? receipt.status() == YawPalm.REFUSED : receipt.status() != YawPalm.PUSHED) && receipt.velocityDelta().equals(Vec3.ZERO)), "ordinary residual may retain a damage receipt, exact refusal remains refused, and neither native shield branch grants a Yaw side-arrow delta");
            near(h, f.cp(), cp - 5, "partial shield still spends one bounded cast cost"); f.close(); nativeCaster.close();
            if (!exact) partialShieldCase(h, true); else h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void synthetic_shield_event_cannot_replace_the_actual_hurt_containers_block_decision(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            Zombie target = f.target(3, true); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); f.discount(target); boolean[] posted = {false};
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
                if (event.getEntity() != target || posted[0]) return; posted[0] = true;
                DamageContainer decoy = new DamageContainer(event.getSource(), 5);
                LivingShieldBlockEvent forged = new LivingShieldBlockEvent(target, decoy, true); forged.setBlockedDamage(5); NeoForge.EVENT_BUS.post(forged);
            } });
            Observation actual = f.observe(target); h.assertTrue(f.cast() && posted[0], "actual incoming listener posts a separate shield container carrying the same source identity");
            eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "synthetic shield event grants no actual blocked damage");
            h.assertTrue(actual.knockbacks == 1 && actual.originalKnockbackStrengths.equals(List.of(.35F)), "only actual hurt container governs the one authenticated redirect");
            h.assertTrue(target.getDeltaMovement().x < 0 && target.getDeltaMovement().z == 0, "forged shield evidence cannot suppress or replace real side authority");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void real_shield_container_does_not_relax_victim_geometry_or_resource_authority(GameTestHelper h) { shieldAuthorityCase(h, 0); }
    private static void shieldAuthorityCase(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h, 2); Zombie target = f.target(3, false), other = f.targetAt(f.eye().add(5, 0, 3), true);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); other.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); shield(target, 180); f.discount(target);
        DamageSource[] captured = {null}; boolean[] changed = {false};
        f.hook(new Object() {
            @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target && captured[0] == null) captured[0] = event.getSource(); }
            @SubscribeEvent(priority = EventPriority.HIGHEST) public void shield(LivingShieldBlockEvent event) { if (event.getEntity() == target) event.setBlockedDamage(2); }
            @SubscribeEvent public void knockback(LivingKnockBackEvent event) {
                if (event.getEntity() != target || event.getOriginalStrength() != .5F || changed[0]) return; changed[0] = true;
                h.assertTrue(captured[0] != null, "actual current damage source is captured before the native shield response");
                if (mode == 0) h.assertFalse(other.hurt(captured[0], 5), "another native hurt frame cannot borrow the real shield container's speed exception");
                else if (mode == 1) target.setPos(target.position().add(2, 0, 0));
                else f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", f.cp() + 1);
            }
        });
        h.runAfterDelay(7, () -> {
            face(target, 180); h.assertTrue(target.isBlocking(), "real native shield is raised before authority mutation"); target.setDeltaMovement(Vec3.ZERO); double cp = f.cp();
            h.assertTrue(f.cast() && changed[0], "actual partial block reaches the ordinary native shield-response callback with real container present");
            eq(h, hp(target), n("20"), "real blocked-container evidence permits only its velocity transition, never invalid victim, body or resource authority");
            eq(h, hp(other), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "captured source cannot transfer its private native frame to another living body");
            vector(h, other.getDeltaMovement(), Vec3.ZERO, "unrelated target gets neither damage nor borrowed native side motion");
            h.assertTrue(YawPalm.receipts(f.p).stream().allMatch(receipt -> receipt.status() == YawPalm.REFUSED && receipt.velocityDelta().equals(Vec3.ZERO)), "lost closed authority cannot publish a side or damage success");
            near(h, f.cp(), cp - 5 + (mode == 2 ? 1 : 0), "one skill payment remains and external resource mutation is not rolled back");
            f.close(); if (mode < 2) shieldAuthorityCase(h, mode + 1); else h.succeed();
        });
    }

    private static BigDecimal total(int level) { return HighPowerLevel.forLevel(level).damage().multiply(n(".5")); }
    private static CompoundTag nativeItem(ItemEntity item) {
        CompoundTag tag = new CompoundTag(); item.addAdditionalSaveData(tag);
        tag.putInt("ExactAge", item.getAge()); tag.putInt("ExactLifespan", item.lifespan); tag.putFloat("FallDistance", item.fallDistance);
        tag.putBoolean("OnGround", item.onGround()); tag.putBoolean("NoGravity", item.isNoGravity()); tag.putBoolean("NoPhysics", item.noPhysics); return tag;
    }
    private static CompoundTag inventory(ServerPlayer p) { CompoundTag tag = new CompoundTag(); tag.put("items", p.getInventory().save(new ListTag())); return tag; }
    private static BigDecimal n(String text) { return new BigDecimal(text); }
    private static BigDecimal hp(LivingEntity entity) { return HighPowerCombat.enabled(entity) ? n(HighPowerCombat.healthText(entity)) : new BigDecimal(Float.toString(entity.getHealth())); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-5, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-10, why + ": " + actual + " != " + expected); }
    private static void face(LivingEntity target, float yaw) { target.setYRot(yaw); target.setYHeadRot(yaw); target.yBodyRot = yaw; }
    private static void shield(LivingEntity target, float yaw) { face(target, yaw); target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); target.startUsingItem(InteractionHand.OFF_HAND); }
    private static void defenses(GameTestHelper h, LivingEntity target) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
        target.getAttribute(Attributes.ARMOR).setBaseValue(5); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); target.setAbsorptionAmount(.5F);
    }
    private static void assertSource(GameTestHelper h, DamageSource source, ServerPlayer caster, Vec3 origin) {
        h.assertTrue(source != null && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                && source.getEntity() == caster && source.getDirectEntity() == caster && origin.equals(source.sourcePositionRaw()) && origin.equals(source.getSourcePosition()),
                "native player-attack source retains the frozen eye origin and real player attribution");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "NEW423 grants no native protection bypass tags");
    }
    public static final class Discount {
        final LivingEntity target; Discount(LivingEntity target) { this.target = target; }
        @SubscribeEvent(priority = EventPriority.HIGHEST) public void skill(SkillAttackEvent event) { if (event.target == target && event.skill.equals(SKILL)) event.amount = 5; }
    }
    private record NativeImpulse(float strength, double ratioX, double ratioZ) { }
    public static final class Observation {
        final LivingEntity target; DamageSource source; int incoming, posts, skills, knockbacks; boolean blocked;
        float damage, armor, effects, enchantments, absorption, blockedDamage; final List<Float> originalKnockbackStrengths = new ArrayList<>(); final List<NativeImpulse> originalImpulses = new ArrayList<>(); final List<Vec3> beforeImpulses = new ArrayList<>();
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void skill(SkillAttackEvent event) { if (event.target == target && event.skill.equals(SKILL)) skills++; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == target) { incoming++; source = event.getSource(); } }
        @SubscribeEvent(receiveCanceled = true) public void knockback(LivingKnockBackEvent event) { if (event.getEntity() == target) { knockbacks++; beforeImpulses.add(target.getDeltaMovement()); originalKnockbackStrengths.add(event.getOriginalStrength()); originalImpulses.add(new NativeImpulse(event.getOriginalStrength(), event.getOriginalRatioX(), event.getOriginalRatioZ())); } }
        @SubscribeEvent public void shield(LivingShieldBlockEvent event) { if (event.getEntity() == target) { blocked = event.getBlocked(); blockedDamage = event.getBlockedDamage(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) {
            if (event.getEntity() != target) return; posts++; damage = event.getNewDamage();
            armor = event.getReduction(DamageContainer.Reduction.ARMOR); effects = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS);
            enchantments = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Vec3 anchor;
        final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final Map<BlockPos, BlockState> previousBlocks = new LinkedHashMap<>(); long inputs; boolean closed;
        Fixture(GameTestHelper h, int level) {
            this.h = h; anchor = h.absoluteVec(new Vec3(8.5, 3, 5.5));
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "YP" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); acknowledgeLogin();
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); place();
            h.assertTrue(HighPowerCombat.enroll(p, Math.max(2, level)), "genuine connected caster enrollment");
            var state = AbilityStorage.get(p); state.selectCategory("vecmanip"); state.setLevel(Math.max(2, level)); state.learn("deflection_strike"); state.learn("braking_touch"); state.learn(ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "real learned Vector root input binding"); HighPowerResources.initializeBudget(p);
            if (level == 1) { state.setLevel(1); HighPowerGrowth.values(p); state.setLevel(2); HighPowerGrowth.values(p); state.activated = true; state.overloadFine = true; }
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) { }
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        void acknowledgeLogin() {
            channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet); }
        }
        void place() { p.moveTo(anchor.x, anchor.y, anchor.z, 0, 0); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        Vec3 eye() { return p.getEyePosition(); }
        void center(Entity entity, Vec3 point) { entity.setPos(point.add(0, -entity.getBbHeight() / 2, 0)); }
        void add(LivingEntity entity, Vec3 point) {
            if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
            entity.setNoGravity(true); entity.setSilent(true); center(entity, point);
            h.assertTrue(h.getLevel().addFreshEntity(entity), "native body admitted to actual level"); entities.add(entity);
        }
        Zombie target(double distance, boolean exact) { return targetAt(eye().add(0, 0, distance), exact); }
        UUID uuid(long order) { return new UUID(p.getUUID().getMostSignificantBits(), order); }
        Zombie targetAt(Vec3 point, boolean exact) { return targetAt(point, exact, UUID.randomUUID()); }
        Zombie targetAt(Vec3 point, boolean exact, UUID identity) {
            Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.setUUID(identity); mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
            mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1); add(mob, point);
            if (exact) h.assertTrue(HighPowerCombat.enroll(mob, 5), "declared exact native hostile enrollment"); return mob;
        }
        double cp() { return HighPowerResources.cp(p); }
        String wire() { return new HighPowerInput.Input(0, ++inputs, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        boolean cast() { return YawPalm.tap(p, wire()); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        ItemEntity toss(double distance, int count) {
            p.getInventory().setItem(p.getInventory().selected, new ItemStack(Items.IRON_INGOT, count));
            h.assertTrue(p.drop(true), "genuine native control-Q drops the selected stack");
            var found = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(2), item -> !entities.contains(item)
                    && item.getItem().getCount() == count && SpatialRelayProvenance.proof(item) != null
                    && SpatialRelayProvenance.proof(item).owner().equals(p.getUUID()));
            h.assertTrue(found.size() == 1, "one genuine Q item retains authenticated independent provenance");
            ItemEntity item = found.getFirst(); entities.add(item); center(item, eye().add(0, 0, distance));
            item.setPickUpDelay(200); item.setDeltaMovement(0, -.08, .04);
            h.assertTrue(SpatialRelayMedium.capture(p, item) != null, "genuine plain Q retains native gravity, owner and stack identity");
            return item;
        }
        ItemEntity raw(double distance, ItemStack stack) {
            ItemEntity item = new ItemEntity(h.getLevel(), p.getX(), p.getEyeY(), p.getZ() + distance, stack);
            center(item, eye().add(0, 0, distance)); item.setDeltaMovement(0, -.08, .04);
            h.assertTrue(h.getLevel().addFreshEntity(item), "unproven native item physically joins world"); entities.add(item); return item;
        }
        DamageSource source(Vec3 origin) { return new DamageSource(p.damageSources().playerAttack(p).typeHolder(), p, p, origin); }
        void block(Vec3 point) { BlockPos position = BlockPos.containing(point); previousBlocks.putIfAbsent(position, h.getLevel().getBlockState(position)); h.getLevel().setBlockAndUpdate(position, Blocks.STONE.defaultBlockState()); }
        void assertBlocks() { for (BlockPos position : previousBlocks.keySet()) h.assertTrue(h.getLevel().getBlockState(position).is(Blocks.STONE), "strike never destroys or transforms terrain"); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void discount(LivingEntity target) { hook(new Discount(target)); }
        Observation observe(LivingEntity target) { Observation result = new Observation(target); hook(result); return result; }
        @Override public void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            YawPalm.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p);
            entities.forEach(Entity::discard); previousBlocks.forEach((position, state) -> h.getLevel().setBlockAndUpdate(position, state)); channel.finishAndReleaseAll();
        }
    }
}

