package cn.academy.port.gametest;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.highpower.core.ZenithDominionRules;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.NewSkillBudget;
import cn.academy.port.preset.PresetSkills;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
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
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** The seven post-0.81.2 skills (143, 153, 243, 332, 352, 451, 453) against a real connected server player and native entities. */
@GameTestHolder("academy_new_skills_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerNewSkillRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_new_skills";
    private HighPowerNewSkillRuntimeTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {48, 12, 48}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_new_skills_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    // ---- registration and learning ---------------------------------------------------------------------------------
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void seven_skills_are_registered_with_their_design_rows(GameTestHelper h) {
        Object[][] expected = {{HighPowerSkills.PULSE_SHIELD, HighPowerCategories.ELECTROMASTER, 4, 143}, {HighPowerSkills.RAIL_RELAY, HighPowerCategories.ELECTROMASTER, 5, 153},
                {HighPowerSkills.REVERSE_JET, HighPowerCategories.MELTDOWNER, 4, 243}, {HighPowerSkills.INCIDENT_BYPASS, HighPowerCategories.TELEPORTER, 3, 332},
                {HighPowerSkills.PHASE_CORRIDOR, HighPowerCategories.TELEPORTER, 5, 352}, {HighPowerSkills.ZENITH_DOMINION, HighPowerCategories.VECMANIP, 5, 451},
                {HighPowerSkills.IMPULSE_SPLIT, HighPowerCategories.VECMANIP, 5, 453}};
        h.assertTrue(HighPowerSkills.ALL.size() == 61 && HighPowerSkills.IDS.size() == 271, "61 native records and210 canon IDs retain the reviewed appended native rows");
        for (Object[] row : expected) {
            String id = (String) row[0];
            var skill = HighPowerSkills.find(id).orElse(null);
            h.assertTrue(skill != null && skill.category().equals(row[1]) && skill.level() == (int) row[2] && skill.controllable(), id + " category, level and controllable flag");
            h.assertTrue(skill.requirements().size() == 1, id + " requires exactly one parent");
            h.assertTrue(HighPowerSkills.find(skill.requirements().get(0).id()).isPresent(), id + " parent is a registered high-power skill");
            h.assertTrue(SkillCatalog.REGISTERED.contains(skill) && PresetSkills.REGISTERED.contains(id), id + " is visible to the catalog and the key presets");
            h.assertTrue(NewSkillRuntime.Kind.of(id).isPresent(), id + " has a runtime");
        }
        h.assertTrue(HighPowerSkills.ALL.get(52).id().equals(HighPowerSkills.AUTONOMOUS_VECTOR_CIRCLE) && HighPowerSkills.ALL.get(53).id().equals(HighPowerSkills.PULSE_SHIELD),
                "the original 53 keep their indices");
        h.succeed();
    }

    // ---- 143 pulse shield ------------------------------------------------------------------------------------------
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void pulse_shield_tap_interferes_with_tagged_targets_only(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            Entity tagged = f.spawn(EntityType.ZOMBIE, 4), untagged = f.spawn(EntityType.ZOMBIE, 5), far = f.spawn(EntityType.ZOMBIE, 30);
            tagged.getPersistentData().putBoolean(NewSkillRuntime.CIRCUIT_FLAG, true); far.getPersistentData().putBoolean(NewSkillRuntime.CIRCUIT_FLAG, true);
            double cp = HighPowerResources.cp(p), cost = f.cost(p);
            h.assertTrue(f.start(p), "authenticated press is accepted"); f.release(p);
            h.assertTrue(tagged.getPersistentData().getLong(NewSkillRuntime.PULSE_UNTIL) > 0, "the circuit-tagged zombie is interfered with");
            h.assertFalse(untagged.getPersistentData().contains(NewSkillRuntime.PULSE_UNTIL), "an untagged zombie is untouched");
            h.assertFalse(far.getPersistentData().contains(NewSkillRuntime.PULSE_UNTIL), "a tagged target beyond 12 m is untouched");
            near(h, HighPowerResources.cp(p), cp - cost, "one budgeted debit");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 160)
    public static void pulse_shield_without_targets_and_long_press_are_free(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "press accepted"); f.release(p);
            near(h, HighPowerResources.cp(p), cp, "a tap with no circuit target is free");
            Entity tagged = f.spawn(EntityType.ZOMBIE, 4); tagged.getPersistentData().putBoolean(NewSkillRuntime.CIRCUIT_FLAG, true);
            h.assertTrue(f.start(p), "long press accepted");
            h.runAfterDelay(14, () -> {
                f.release(p);
                h.assertFalse(tagged.getPersistentData().contains(NewSkillRuntime.PULSE_UNTIL), "a long press only detects and disturbs nothing");
                near(h, HighPowerResources.cp(p), cp, "detection is free");
                h.succeed();
            });
        });
    }

    // ---- 153 rail relay --------------------------------------------------------------------------------------------
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void rail_relay_native_ground_contact_keeps_real_horizontal_motion(GameTestHelper h) {
        groundedRelay(h,false);
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void rail_relay_native_wall_contact_still_ends_the_paid_ride(GameTestHelper h) {
        groundedRelay(h,true);
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=120)
    public static void rail_relay_real_support_beats_one_stale_client_ground_bit(GameTestHelper h) {
        groundedRelay(h,false,true);
    }
    private static void groundedRelay(GameTestHelper h,boolean wall) {
        groundedRelay(h,wall,false);
    }
    private static void groundedRelay(GameTestHelper h,boolean wall,boolean staleGround) {
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.RAIL_RELAY);ServerPlayer p=f.player(8,24);
        f.ready(()->{
            Vec3 start=p.position();for(int z=0;z<=12;z++)for(int x=-1;x<=1;x++)h.getLevel().setBlock(BlockPos.containing(start.add(x,-1,z)),Blocks.STONE.defaultBlockState(),3);
            p.setShiftKeyDown(true);p.setPose(Pose.CROUCHING);p.setXRot(90);h.assertTrue(f.start(p),"first native floor anchor");f.release(p);
            h.runAfterDelay(6,()->{
                p.moveTo(start.x,start.y,start.z+8,0,90);p.connection.resetPosition();h.assertTrue(f.start(p),"second native floor anchor");f.release(p);
                h.runAfterDelay(6,()->{
                    h.assertTrue(NewSkillRuntime.anchors(p).size()==2,"two normally paid anchors");p.setShiftKeyDown(false);p.setPose(Pose.STANDING);p.moveTo(start.x,start.y,start.z,0,0);p.connection.resetPosition();p.setNoGravity(false);p.setOnGround(!staleGround);p.setDeltaMovement(0,-.0784000015258789,0);
                    if(wall)for(int y=0;y<3;y++)h.getLevel().setBlock(BlockPos.containing(start.add(0,y,1)),Blocks.STONE.defaultBlockState(),3);
                    double before=HighPowerResources.cp(p);h.assertTrue(f.start(p),"grounded route press accepted");h.assertTrue(HighPowerResources.cp(p)<before,"ride paid normally");
                    h.runAfterDelay(1,()->{
                        h.assertTrue(NewSkillRuntime.active(p)&&p.getDeltaMovement().z>0,"native floor clip preserved the first horizontal step");Vec3 actual=p.position();p.move(net.minecraft.world.entity.MoverType.SELF,p.getDeltaMovement().add(0,-.0784000015258789,0));h.assertTrue(p.getZ()>actual.z&&p.onGround(),"native SELF step with normal gravity really advanced on the supporting floor");
                        h.runAfterDelay(2,()->{if(wall)h.assertFalse(NewSkillRuntime.active(p),"horizontal native wall clip ends the ride");else h.assertTrue(NewSkillRuntime.active(p)&&p.getDeltaMovement().z>0,"ordinary flat ground keeps the route alive");h.succeed();});
                    });
                });
            });
        });
    }
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void rail_relay_places_three_anchors_then_rides_without_exceeding_the_cap(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.RAIL_RELAY); ServerPlayer p = f.player(8, 24);
        f.ready(() -> {
            for (int dx = 0; dx < 24; dx++) for (int dz = -2; dz <= 2; dz++) h.setBlock(BlockPos.containing(8 + dx, 1, 24 + dz), Blocks.STONE);
            placeAnchor(h, f, p, 0);
        });
    }

    /** Presses happen a real cooldown apart: crouch, look at the floor three metres further along, press, release. */
    private static void placeAnchor(GameTestHelper h, Fixture f, ServerPlayer p, int i) {
        if (i == 4) { ride(h, f, p); return; }
        p.setPose(Pose.CROUCHING); p.setShiftKeyDown(true);
        Vec3 base = f.anchors.get(p);
        p.moveTo(base.x + i * 3, base.y, base.z, 0, 90);   // look straight down at the floor
        p.connection.resetPosition();
        boolean accepted = f.start(p); f.release(p);
        h.assertTrue(accepted, "anchor press " + i + " accepted");
        h.assertTrue(NewSkillRuntime.anchors(p).size() == Math.min(3, i + 1), "never more than three anchors (press " + i + " gave " + NewSkillRuntime.anchors(p) + ")");
        h.runAfterDelay(10, () -> placeAnchor(h, f, p, i + 1));
    }

    private static void ride(GameTestHelper h, Fixture f, ServerPlayer p) {
        var anchors = NewSkillRuntime.anchors(p);
        h.assertTrue(anchors.size() == 3, "three anchors after four presses (oldest replaced)");
        p.setShiftKeyDown(false); p.setPose(Pose.STANDING);
        Vec3 start = new Vec3(anchors.get(0).x(), anchors.get(0).y(), anchors.get(0).z());
        p.moveTo(start.x, start.y, start.z, 0, 0); p.connection.resetPosition(); p.setDeltaMovement(Vec3.ZERO);
        double cp = HighPowerResources.cp(p);
        h.assertTrue(f.start(p), "riding a route within 3 m is accepted");
        h.assertTrue(HighPowerResources.cp(p) < cp, "the ride is paid");
        h.runAfterDelay(8, () -> {
            double speed = p.getDeltaMovement().length();
            h.assertTrue(speed > 0 && speed <= 1.5 + 1e-9, "the rider moves, never faster than the cap: " + speed);
            f.release(p);
            h.succeed();
        });
    }

    // ---- 243 reverse jet brake -------------------------------------------------------------------------------------
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 160)
    public static void reverse_jet_only_removes_momentum_and_a_body_at_rest_cannot_use_it(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.REVERSE_JET); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "a press is accepted at the protocol level");
            near(h, HighPowerResources.cp(p), cp, "a body at rest cannot spend the jet");
            p.setDeltaMovement(.4, -.8, .3); p.fallDistance = 6; p.setNoGravity(true);
            h.assertTrue(f.start(p), "a moving body can brake");
            h.assertTrue(HighPowerResources.cp(p) < cp, "braking is paid once, up front");
            Vec3 before = p.getDeltaMovement();
            h.runAfterDelay(6, () -> {
                Vec3 after = p.getDeltaMovement();
                h.assertTrue(after.length() < before.length(), "speed fell");
                h.assertTrue(after.dot(before) > 0, "the motion was never reversed");
                h.succeed();
            });
        });
    }

    // ---- 332 incident bypass ---------------------------------------------------------------------------------------
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 120)
    public static void incident_bypass_cuts_the_next_arrow_by_forty_percent_exactly_once(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.INCIDENT_BYPASS); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "hold accepted"); f.release(p);
            near(h, HighPowerResources.cp(p), cp, "releasing before 0.4 s opens nothing and costs nothing");
            h.assertFalse(NewSkillRuntime.windowArmed(p), "no window after an early release");
            h.assertTrue(f.start(p), "second hold accepted");
            h.runAfterDelay(10, () -> {
                h.assertTrue(NewSkillRuntime.windowArmed(p), "a 0.4 s hold opens the window");
                h.assertTrue(HighPowerResources.cp(p) < cp, "the window is paid when it opens");
                Arrow first = f.arrow(p, 6), second = f.arrow(p, 9);
                double base = first.getBaseDamage();
                h.runAfterDelay(3, () -> {
                    near(h, first.getBaseDamage(), base * .6, "the nearest arrow loses exactly 40%");
                    near(h, second.getBaseDamage(), base, "only the next projectile is diverted");
                    h.assertFalse(NewSkillRuntime.windowArmed(p), "the window is spent");
                    f.release(p);
                    h.succeed();
                });
            });
        });
    }

    // ---- 352 phase corridor ----------------------------------------------------------------------------------------
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 120)
    public static void phase_corridor_jumps_to_a_safe_spot_evades_projectiles_briefly_and_never_chains_cover(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PHASE_CORRIDOR); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            for (int dx = 0; dx < 40; dx++) for (int dz = -1; dz <= 1; dz++) h.setBlock(BlockPos.containing(8 + dx, 1, 8 + dz), Blocks.STONE);
            p.moveTo(h.absoluteVec(new Vec3(8.5, 2, 8.5)), -90, 0); p.connection.resetPosition();
            Vec3 from = p.position(); double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "the jump is accepted");
            double moved = p.position().distanceTo(from);
            double reach = 32 * cn.academy.port.highpower.core.MobilityScale.reach(HighPowerGrowth.values(p).position());
            h.assertTrue(reach > 32 && moved > 32 && moved <= reach + .01, "a LV5 jump reaches beyond the original 32 m, within its level-scaled range of " + reach + " m: " + moved);
            h.assertTrue(HighPowerResources.cp(p) < cp, "the jump is paid");
            h.assertTrue(NewSkillRuntime.evading(p), "0.1 s of projectile evasion follows the jump");
            double cp2 = HighPowerResources.cp(p);
            f.release(p);
            h.assertFalse(f.start(p), "a second jump inside the exposed window is refused");
            near(h, HighPowerResources.cp(p), cp2, "the refused second jump is free");
            h.runAfterDelay(8, () -> {
                h.assertFalse(NewSkillRuntime.evading(p), "evasion ended; the body is solid again");
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void phase_corridor_with_no_safe_landing_is_free(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PHASE_CORRIDOR); ServerPlayer p = f.player(8, 8);
        f.ready(() -> {
            // Look straight up into the empty template: no landing exists within range.
            p.moveTo(h.absoluteVec(new Vec3(8.5, 2, 8.5)), 0, -90); p.connection.resetPosition();
            Vec3 from = p.position(); double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "press accepted");
            h.assertTrue(p.position().distanceToSqr(from) < 1e-6, "no teleport without a safe landing");
            near(h, HighPowerResources.cp(p), cp, "no charge");
            h.succeed();
        });
    }

    // ---- 451 zenith vector dominion --------------------------------------------------------------------------------
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 160)
    public static void zenith_dominion_lifts_and_releases_all_eighteen_eligible_marks(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.ZENITH_DOMINION); ServerPlayer p = f.player(10, 6);
        f.ready(() -> {
            List<LivingEntity> targets = new ArrayList<>();
            List<BigDecimal> beforeHp = new ArrayList<>();
            for (int i = 0; i < 18; i++) {
                LivingEntity z = (LivingEntity) f.spawnAt(EntityType.HUSK, 4 + (i % 6) * 2, 1 + i / 6 * 2);
                z.setNoGravity(true); ((net.minecraft.world.entity.Mob)z).setNoAi(true); targets.add(z);
                h.assertTrue(HighPowerCombat.enroll(z,5), "durable exact-ledger target");
                beforeHp.add(new BigDecimal(HighPowerCombat.healthText(z)));
            }
            h.assertFalse(HighPowerCombat.attack(p,NewSkillRuntime.Kind.ZENITH_DOMINION.damageKey,targets.getFirst(),BigDecimal.ONE),"generic vector calls remain refused without a controller capability");
            h.assertFalse(HighPowerCombat.attackEmission(null), "a missing controller capability cannot emit");
            // The reserved skill names must also refuse a caller from the legacy generic entrance's admitted category.
            var ability=AbilityStorage.get(p);
            ability.category=HighPowerCategories.ELECTROMASTER;
            try {
                h.assertFalse(HighPowerCombat.attack(p,NewSkillRuntime.Kind.ZENITH_DOMINION.damageKey,targets.getFirst(),BigDecimal.ONE),"electromaster callers cannot impersonate the closed dominion skill");
                h.assertFalse(HighPowerCombat.attack(p,ConstructRuntime.DAMAGE_KEY,targets.getFirst(),BigDecimal.ONE),"electromaster callers cannot manufacture a construct contact");
            } finally { ability.category=HighPowerCategories.VECMANIP; }
            f.hook(new Object() {
                @SubscribeEvent public void reentry(cn.academy.port.api.SkillAttackEvent e) {
                    if(e.player==p && e.skill.equals(NewSkillRuntime.Kind.ZENITH_DOMINION.damageKey))
                        h.assertFalse(HighPowerCombat.attack(p,e.skill,(LivingEntity)e.target,BigDecimal.ONE),"callbacks cannot borrow the closed capability through the generic API");
                }
            });
            double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "the mark press is accepted");
            h.assertTrue(HighPowerResources.cp(p) < cp, "paid once");
            h.runAfterDelay(4, () -> {
                int lifted = 0;
                for (LivingEntity z : targets) if (z.getDeltaMovement().y > .1) lifted++;
                h.assertTrue(lifted == 18, "all eighteen eligible enemies are lifted: " + lifted);
                // GameTest actions run before the server post tick that applies the release.
                h.runAfterDelay(ZenithDominionRules.LIFT_TICKS - 4 + 1, () -> {
                    int damaged=0;
                    for(int i=0;i<targets.size();i++) if(new BigDecimal(HighPowerCombat.healthText(targets.get(i))).compareTo(beforeHp.get(i))<0) damaged++;
                    h.assertTrue(damaged==18,"the release damages all eighteen frozen ledger targets: "+damaged);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void zenith_dominion_without_hostile_targets_is_free(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.ZENITH_DOMINION); ServerPlayer p = f.player(10, 6);
        f.ready(() -> {
            f.spawn(EntityType.ARMOR_STAND, 5);
            double cp = HighPowerResources.cp(p);
            h.assertTrue(f.start(p), "press accepted");
            near(h, HighPowerResources.cp(p), cp, "no hostile target, no charge");
            h.succeed();
        });
    }

    // ---- 453 impulse split -----------------------------------------------------------------------------------------
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void impulse_split_turns_one_arrow_into_two_that_carry_no_more_than_one(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.IMPULSE_SPLIT); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p);
            p.moveTo(p.getX(), p.getY(), p.getZ(), -60, 0); p.connection.resetPosition();
            h.assertTrue(f.start(p), "first direction chosen"); f.release(p);
            near(h, HighPowerResources.cp(p), cp, "choosing the first direction is free");
            p.moveTo(p.getX(), p.getY(), p.getZ(), 60, 0); p.connection.resetPosition();
            h.assertTrue(f.start(p), "second direction chosen"); f.release(p);
            h.assertTrue(HighPowerResources.cp(p) < cp, "the window is paid when both directions are set");
            h.assertTrue(NewSkillRuntime.windowArmed(p), "the split window is open");
            p.moveTo(p.getX(), p.getY(), p.getZ(), 0, 0); p.connection.resetPosition();
            Arrow arrow = f.arrow(p, 6); arrow.setNoGravity(false);
            double base = arrow.getBaseDamage(); double speed = arrow.getDeltaMovement().length();
            double[] emissionEnergy={Double.NaN};
            f.hook(new Object(){@net.neoforged.bus.api.SubscribeEvent public void born(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){
                if(e.getEntity() instanceof Arrow b&&b!=arrow&&b.position().equals(arrow.position())&&ImpulseSplitArrowBudget.snapshot(b)!=null)
                    emissionEnergy[0]=arrow.getDeltaMovement().lengthSqr()+b.getDeltaMovement().lengthSqr();
            }});
            long arrowsBefore = h.getLevel().getEntitiesOfClass(Arrow.class, p.getBoundingBox().inflate(20)).size();
            h.runAfterDelay(3, () -> {
                List<Arrow> arrows = h.getLevel().getEntitiesOfClass(Arrow.class, p.getBoundingBox().inflate(30));
                h.assertTrue(arrows.size() == arrowsBefore + 1, "one arrow became two"); f.entities.addAll(arrows);
                double energy = 0;
                for (Arrow a : arrows) { energy += a.getDeltaMovement().lengthSqr(); h.assertTrue(a.getBaseDamage() <= base * Math.sqrt(.5) + 1e-9, "each share carries at most 0.707 of the base damage"); }
                h.assertTrue(Double.isFinite(emissionEnergy[0])&&emissionEnergy[0] <= speed * speed * (1 + 1e-6), "the actual emitted shares carry no more energy before subsequent native gravity: " + emissionEnergy[0] + " vs " + speed * speed);
                h.assertFalse(NewSkillRuntime.windowArmed(p), "the window is spent");
                f.drain(p);
                var cues = f.visuals.stream().filter(v -> v.owner().equals(p.getUUID()) && v.input() == 2).toList();
                var armed = cues.stream().filter(v -> v.phase() == NewSkillRuntime.ARMED).findFirst().orElseThrow();
                var diverted = cues.stream().filter(v -> v.phase() == NewSkillRuntime.DIVERT).findFirst().orElseThrow();
                var ended = cues.stream().filter(v -> v.phase() == NewSkillRuntime.END).findFirst().orElseThrow();
                h.assertTrue(armed.token() == diverted.token() && diverted.token() == ended.token()
                        && armed.epoch() == f.epoch(p) && ended.epoch() == armed.epoch() && diverted.epoch() == armed.epoch(),
                        "real second-input ownership survives arming and native arrow consumption");
                h.assertTrue(armed.sequence() < diverted.sequence() && diverted.sequence() < ended.sequence(),
                        "one diversion followed by one ordered input END");
                h.assertTrue(cues.stream().filter(v -> v.phase() == NewSkillRuntime.END).count() == 1, "one terminal cue");
                h.succeed();
            });
        });
    }

    // ---- protocol abuse --------------------------------------------------------------------------------------------
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 190)
    public static void impulse_split_unused_window_ends_the_exact_second_input_without_spending_again(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.IMPULSE_SPLIT); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            armSplitDirections(f, p);
            double cp = HighPowerResources.cp(p);
            int windowTicks = SkillTuning.integer("impulse_split", "duration_ticks");
            h.runAfterDelay(windowTicks + 2, () -> {
                f.drain(p);
                var cues = f.visuals.stream().filter(v -> v.owner().equals(p.getUUID()) && v.input() == 2).toList();
                var armed = cues.stream().filter(v -> v.phase() == NewSkillRuntime.ARMED).findFirst().orElseThrow();
                var ended = cues.stream().filter(v -> v.phase() == NewSkillRuntime.END).findFirst().orElseThrow();
                h.assertFalse(NewSkillRuntime.windowArmed(p), "original split window expires");
                h.assertTrue(ended.token() == armed.token() && ended.epoch() == armed.epoch() && ended.sequence() > armed.sequence(), "expiry closes the real armed identity");
                h.assertTrue(cues.stream().noneMatch(v -> v.phase() == NewSkillRuntime.DIVERT), "no fake diversion on expiry");
                h.assertTrue(cues.stream().filter(v -> v.phase() == NewSkillRuntime.END).count() == 1, "expiry closes once");
                h.assertTrue(HighPowerResources.cp(p) >= cp, "expiry adds no payment");
                h.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void forged_replayed_and_unlearned_inputs_never_spend_anything(GameTestHelper h) {
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.PULSE_SHIELD); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            double cp = HighPowerResources.cp(p);
            String good = new HighPowerInput.Input(0, 5, p.getId(), f.epoch(p)).wire();
            h.assertFalse(NewSkillRuntime.start(p, "garbage"), "malformed wire refused");
            h.assertFalse(NewSkillRuntime.start(p, new HighPowerInput.Input(0, 6, p.getId(), f.epoch(p) + 1).wire()), "a wrong owner epoch is refused");
            h.assertFalse(NewSkillRuntime.start(p, new HighPowerInput.Input(0, 7, p.getId() + 999, f.epoch(p)).wire()), "a wrong actor is refused");
            h.assertTrue(NewSkillRuntime.start(p, good), "the genuine input is accepted once");
            h.assertFalse(NewSkillRuntime.start(p, good), "the same input number cannot be replayed");
            NewSkillRuntime.abort(p);
            near(h, HighPowerResources.cp(p), cp, "none of the refusals spent CP");
            AbilityStorage.get(p).unlearn(HighPowerSkills.PULSE_SHIELD);
            h.assertFalse(NewSkillRuntime.start(p, new HighPowerInput.Input(0, 8, p.getId(), f.epoch(p)).wire()), "an unlearned skill cannot start");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void impulse_split_integer_three_is_two_plus_one_at_two_real_native_impacts(GameTestHelper h){
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.IMPULSE_SPLIT);ServerPlayer p=f.player(10,10);
        f.ready(()->splitPair(f,p,2.1,pair->{
            int budget=pair.stream().map(ImpulseSplitArrowBudget::snapshot).mapToInt(ImpulseSplitArrowBudget.Snapshot::budget).sum();
            h.assertTrue(budget==3,"one native ceil(2.1) budget is three, not two separately rounded halves");
            double actual=impact(f,pair.get(0),0,false)+impact(f,pair.get(1),1,false);
            near(h,actual,3,"actual separate native body impacts spend only two plus one");h.succeed();
        }));
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void impulse_split_lost_sibling_never_reallocates_its_integer_share(GameTestHelper h){
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.IMPULSE_SPLIT);ServerPlayer p=f.player(10,10);
        f.ready(()->splitPair(f,p,2.1,pair->{Arrow lost=pair.get(0),survivor=pair.get(1);int own=ImpulseSplitArrowBudget.snapshot(survivor).budget();lost.discard();
            near(h,impact(f,survivor,0,false),own,"a missing sibling cannot add its share to the survivor");h.succeed();}));
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void impulse_split_serialized_copy_has_zero_damage_and_no_object_authority(GameTestHelper h){
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.IMPULSE_SPLIT);ServerPlayer p=f.player(10,10);
        f.ready(()->splitPair(f,p,2.1,pair->{Arrow live=pair.get(0);CompoundTag saved=live.saveWithoutId(new CompoundTag());saved.remove("UUID");
            Arrow restored=new Arrow(EntityType.ARROW,h.getLevel());restored.load(saved);restored.setUUID(UUID.randomUUID());h.assertTrue(h.getLevel().addFreshEntity(restored),"saved split arrow re-enters real level");f.entities.add(restored);
            h.assertTrue(restored.getBaseDamage()==0&&ImpulseSplitArrowBudget.snapshot(restored)==null,"NBT cannot restore the per-object positive permit");
            near(h,impact(f,restored,0,false),0,"reloaded/copy entity cannot mint native ceil damage");h.succeed();}));
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void impulse_split_same_owner_write_retires_the_share_without_refund(GameTestHelper h){
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.IMPULSE_SPLIT);ServerPlayer p=f.player(10,10);
        f.ready(()->splitPair(f,p,2.1,pair->{Arrow a=pair.get(0);a.setOwner(a.getOwner());
            h.assertTrue(ImpulseSplitArrowBudget.snapshot(a).retired(),"even a same-owner setter invalidates the old identity contract");
            near(h,impact(f,a,0,false),0,"retired share is not regenerated by an unchanged visible owner");h.succeed();}));
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void impulse_split_canceled_native_hurt_consumes_share_before_callback(GameTestHelper h){
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.IMPULSE_SPLIT);ServerPlayer p=f.player(10,10);
        f.ready(()->splitPair(f,p,2.1,pair->{Arrow a=pair.get(0);near(h,impact(f,a,0,true),0,"foreign native hurt cancellation prevents actual health loss");
            h.assertTrue(ImpulseSplitArrowBudget.snapshot(a).consumed(),"the attempt consumed its own share before foreign cancellation");
            if(!a.isRemoved())near(h,impact(f,a,1,false),0,"a later retry never refunds or reuses the canceled share");h.succeed();}));
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void impulse_split_failed_copy_birth_keeps_only_original_nonrefundable_share(GameTestHelper h){
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.IMPULSE_SPLIT);ServerPlayer p=f.player(10,10);
        f.ready(()->{
            armSplitDirections(f,p);Arrow a=f.arrow(p,6);a.setNoGravity(false);a.setBaseDamage(2.1);a.setDeltaMovement(0,0,-1);
            f.hook(new Object(){@net.neoforged.bus.api.SubscribeEvent public void born(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){if(e.getEntity() instanceof Arrow b&&b!=a&&b.position().equals(a.position())&&ImpulseSplitArrowBudget.snapshot(b)!=null)e.setCanceled(true);}});
            h.runAfterDelay(2,()->{var share=ImpulseSplitArrowBudget.snapshot(a);h.assertTrue(share!=null&&share.budget()==2,"birth failure retains only the original two-point share");
                near(h,impact(f,a,0,false),2,"canceled copy does not transfer the missing one-point share");h.succeed();});
        });
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void impulse_split_unknown_fake_nbt_is_not_a_damage_budget(GameTestHelper h){
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.IMPULSE_SPLIT);ServerPlayer p=f.player(10,10);
        f.ready(()->{Arrow a=f.arrow(p,6);a.setNoGravity(false);a.setBaseDamage(2.1);a.getPersistentData().putInt("academy:impulse_split_budget",999);a.getPersistentData().putBoolean(NewSkillRuntime.BYPASSED,true);
            h.assertTrue(ImpulseSplitArrowBudget.snapshot(a)==null,"fake marker has no server-object authority");
            double actual=impact(f,a,0,false);near(h,actual,3,"untracked ordinary native arrow keeps its original damage; fake NBT cannot mint 999");h.succeed();});
    }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void impulse_split_narrow_actual_twenty_one_degree_outputs_do_not_amplify_net_impulse(GameTestHelper h){
        Fixture f=new Fixture(h,NewSkillRuntime.Kind.IMPULSE_SPLIT);ServerPlayer p=f.player(10,10);
        f.ready(()->{
            p.moveTo(p.getX(),p.getY(),p.getZ(),-10.5f,0);p.connection.resetPosition();h.assertTrue(f.start(p),"real narrow first choice");f.release(p);
            p.moveTo(p.getX(),p.getY(),p.getZ(),10.5f,0);p.connection.resetPosition();h.assertTrue(f.start(p),"real narrow second choice");f.release(p);
            Arrow a=f.arrow(p,6);a.setNoGravity(false);a.setDeltaMovement(0,0,-1);double[] emitted={Double.NaN};
            f.hook(new Object(){@net.neoforged.bus.api.SubscribeEvent public void born(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){if(e.getEntity() instanceof Arrow b&&b!=a&&b.position().equals(a.position())&&ImpulseSplitArrowBudget.snapshot(b)!=null){
                emitted[0]=a.getDeltaMovement().add(b.getDeltaMovement()).length();f.entities.add(b);
                h.assertTrue(emitted[0]<=1,"actual narrow emitted vector sum is no larger than the original impulse");
            }}});
            h.runAfterDelay(2,()->{h.assertTrue(Double.isFinite(emitted[0]),"actual native copy birth was observed");h.succeed();});
        });
    }
    private static void armSplitDirections(Fixture f,ServerPlayer p){
        p.moveTo(p.getX(),p.getY(),p.getZ(),-45,0);p.connection.resetPosition();f.h.assertTrue(f.start(p),"actual first direction");f.release(p);
        p.moveTo(p.getX(),p.getY(),p.getZ(),45,0);p.connection.resetPosition();f.h.assertTrue(f.start(p),"actual paid second direction");f.release(p);
        p.moveTo(p.getX(),p.getY(),p.getZ(),0,0);p.connection.resetPosition();
    }
    private static void splitPair(Fixture f,ServerPlayer p,double base,java.util.function.Consumer<List<Arrow>> work){
        armSplitDirections(f,p);Arrow a=f.arrow(p,6);a.setNoGravity(false);a.setBaseDamage(base);a.setDeltaMovement(0,0,-1);
        f.h.runAfterDelay(2,()->{var pair=f.h.getLevel().getEntitiesOfClass(Arrow.class,p.getBoundingBox().inflate(20),x->ImpulseSplitArrowBudget.snapshot(x)!=null);
            f.h.assertTrue(pair.size()==2,"one supported native arrow produced exactly two real object-bound shares");f.entities.addAll(pair);work.accept(pair);});
    }
    /** Real Arrow.tick collision with an ordinary zero-armor zombie, not a direct hurt adapter call. */
    private static double impact(Fixture f,Arrow a,int lane,boolean cancel){
        Vec3 point=f.h.absoluteVec(new Vec3(28+lane*5,5,28));a.setPos(point);a.setDeltaMovement(0,0,ImpulseSplitArrowBudget.snapshot(a)==null&&a.getBaseDamage()>0?1:.7071067811865476);
        // AbstractArrow's native ProjectileUtil overload clips the target box inflated by .3F.
        // At +.5 the arrow starts inside that box, so native AABB.clip returns no entry at all.
        // Start outside the box and cross its near face at the unchanged native speed instead.
        var target=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,f.h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setPos(point.add(0,-.5,.9));
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).setBaseValue(0);f.h.assertTrue(f.h.getLevel().addFreshEntity(target),"actual native target");f.entities.add(target);
        var nativeBounds=target.getBoundingBox().inflate((double).3F);
        f.h.assertTrue(!nativeBounds.contains(point)&&nativeBounds.clip(point,point.add(a.getDeltaMovement())).isPresent(),"real arrow starts outside and crosses the native inflated target, including at the actual far test origin");
        int[] contacts={0,0};float[] incoming={Float.NaN};
        Object hook=new Object(){
            @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST,receiveCanceled=true)
            public void contact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent e){
                if(e.getProjectile()==a&&e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity()==target)contacts[0]++;
            }
            @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST,receiveCanceled=true)
            public void hurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e){
                if(e.getEntity()==target&&e.getSource().getDirectEntity()==a){contacts[1]++;incoming[0]=e.getAmount();if(cancel)e.setCanceled(true);}
            }
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(hook);float before=target.getHealth();
        try{a.tick();}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(hook);}
        f.h.assertTrue(contacts[0]==1&&contacts[1]==1,"one real native body impact and same-arrow Incoming are required even for zero-damage/rejected permits; impact="+contacts[0]+" incoming="+contacts[1]+" nativeAmount="+incoming[0]+" arrow="+a.position()+" target="+target.getBoundingBox());
        return before-target.getHealth();
    }

    static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected); }

    /** One connected, enrolled, learned player bound to slot 0 for a single new skill. */
    private static final class Fixture {
        final GameTestHelper h; final NewSkillRuntime.Kind kind; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>(); final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>();
        final List<NewSkillRuntime.Visual> visuals = new ArrayList<>();
        final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>();
        final Map<ServerPlayer, String> held = new IdentityHashMap<>(); boolean closed;
        Fixture(GameTestHelper h, NewSkillRuntime.Kind kind) {
            this.h = h; this.kind = kind;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player(double x, double z) {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "NS" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 4; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, kind.level), "explicit native connected enrollment");
            var s = AbilityStorage.get(p); s.selectCategory(kind.category); s.setLevel(kind.level);
            s.learn(HighPowerCategories.recoveryRoot(kind.category)); s.learn(kind.id); s.activated = true; s.overloadFine = true;
            h.assertTrue(s.presets.edit(0, 0, kind.id, s::learned), "real learned native key binding");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        long epoch(ServerPlayer p) { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(ServerPlayer p) { long input = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, input); return new HighPowerInput.Input(0, input, p.getId(), epoch(p)).wire(); }
        boolean start(ServerPlayer p) { String wire = wire(p); held.put(p, wire); return NewSkillRuntime.start(p, wire); }
        void release(ServerPlayer p) { NewSkillRuntime.release(p, held.get(p)); }
        double cost(ServerPlayer p) { return NewSkillBudget.cp(budget(), HighPowerGrowth.values(p).position()); }
        NewSkillBudget.Skill budget() { return NewSkillBudget.Skill.valueOf(kind.name()); }
        Entity spawn(EntityType<?> type, double ahead) { return spawnAt(type, ahead, 0); }
        /** Spawns {@code ahead} metres in front of the player (looking toward +Z) and {@code side} metres to the right. */
        Entity spawnAt(EntityType<?> type, double ahead, double side) {
            Entity e = type.create(h.getLevel()); Vec3 at = anchors.values().iterator().next();
            e.setPos(at.x - side, at.y, at.z + ahead); h.assertTrue(h.getLevel().addFreshEntity(e), "entity joined the world"); entities.add(e); return e;
        }
        Arrow arrow(ServerPlayer p, double distance) {
            Arrow a = new Arrow(h.getLevel(), p.getX(), p.getEyeY(), p.getZ() + distance, new ItemStack(Items.ARROW), null);
            a.setNoGravity(true); a.setDeltaMovement(0, 0, -.6); h.assertTrue(h.getLevel().addFreshEntity(a), "arrow joined the world"); entities.add(a); return a;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void drain(ServerPlayer p) {
            EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom && custom.payload() instanceof NewSkillRuntime.Visual visual) visuals.add(visual);
                if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet);
            }
        }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer p : players) { NewSkillRuntime.remove(p); DevelopmentController.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
