package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CrossSubstitutionRules;
import cn.academy.port.highpower.core.DamageMitigation;
import cn.academy.port.highpower.core.DefenseProfile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * NEW321 real A/B floor preparation and native cross-needle combat. Learned flags,
 * bounded floors, resident mocked-network players, and stationary native husks are
 * declared fixtures. Timers use genuine server ticks. Explicit medium.tick calls
 * exercise native collision, never direct contact methods or constructed permits.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerCrossSubstitutionRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_cross_substitution";
    private static final String ID = CrossSubstitution.ID, SKILL = "teleporter.cross_substitution";
    private static final String FENCES = "academy:cross_substitution_fences";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_cross_substitution_fixture", UUID::compareTo);
    private HighPowerCrossSubstitutionRuntimeTests() {}
    private static BigDecimal n(String s) { return new BigDecimal(s); }
    private static BigDecimal hp(LivingEntity e) { return HighPowerCombat.enabled(e) ? n(HighPowerCombat.healthText(e)) : BigDecimal.valueOf(e.getHealth()); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static void near(GameTestHelper h, double actual, double expected, String why) {
        h.assertTrue(Math.abs(actual - expected) < 1e-6, why + ": " + actual + " != " + expected);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_catalog_prerequisite_and_canonical_requests_keep_preparation_closed(GameTestHelper h) {
        Fixture f=new Fixture(h,true);var skill=SkillCatalog.find(HighPowerCategories.TELEPORTER,ID).orElseThrow();
        h.assertTrue(skill.level()==2&&skill.requirements().equals(List.of(new SkillCatalog.Requirement(DisplacedProjection.ID,0))),
                "native LV2 catalog requires learned NEW311 without an invented mastery threshold");
        f.aim(f.pointA);f.inputs=1;double cp=f.cp();
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("hp_cross_substitution_start",f.wire(1))),
                "canonical sender-bound request captures first actual native surface");
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("hp_cross_substitution_release",f.wire(1))),
                "canonical release retains prepared point");
        h.assertTrue(AcademyGameplay.requestFromClient(f.p,new AcademyNetwork.Request("hp_cross_substitution_abort",f.wire(1))),
                "canonical matching abort clears preparation");
        h.assertFalse(CrossSubstitution.marked(f.p)||CrossSubstitution.active(f.p),"canonical abort consumes saved preparation");
        near(h,f.cp(),cp,"canonical free controls consume no resources");h.assertValueEqual(f.needles(),3,"canonical preparation consumes no medium");h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=16)
    public static void real_two_floor_marks_are_free_exact_and_do_not_renew_first_lifetime(GameTestHelper h) {
        Fixture f = new Fixture(h, true); double cp = f.cp(); int stock = f.needles();
        f.ready(() -> {
            h.assertTrue(CrossSubstitution.preparedPoints(f.p) == 2 && CrossSubstitution.marked(f.p)
                    && !CrossSubstitution.active(f.p), "two genuine first-UP floor captures remain preparation only");
            h.assertValueEqual(CrossSubstitution.remainingTicks(f.p), CrossSubstitutionRules.PREPARATION_TICKS - 8, "second mark does not restart first mark's lifetime");
            h.assertTrue(f.originA.equals(f.surfaceA.add(0,1.2,0)) && f.originB.equals(f.surfaceB.add(0,1.2,0)),
                    "emitters exactly retain native first-UP hits plus 1.2m; A="+f.originA+" surface="+f.surfaceA+" aimed="+f.pointA
                            +"; B="+f.originB+" surface="+f.surfaceB+" aimed="+f.pointB);
            near(h, f.cp(), cp, "preparation has no CP debit"); h.assertValueEqual(f.needles(), stock, "preparation escrows no stock");
            h.assertTrue(f.spawns.media.isEmpty() && f.p.position().equals(f.source), "preparation creates no projectile or player movement");
            near(h, AbilityStorage.get(f.p).exp(ID), 0, "preparation creates no mastery");
        }); f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=515)
    public static void preparation_expires_at_its_real_tick_boundary_without_acceleration(GameTestHelper h) {
        Fixture f = new Fixture(h, true); f.markA(); double cp = f.cp();
        for (int i=0; i<1000; i++) CrossSubstitution.tick(f.p);
        h.assertValueEqual(CrossSubstitution.remainingTicks(f.p), CrossSubstitutionRules.PREPARATION_TICKS, "duplicate same-tick calls do not age preparation");
        f.post(CrossSubstitutionRules.PREPARATION_TICKS - 1, () -> h.assertTrue(CrossSubstitution.marked(f.p) && CrossSubstitution.remainingTicks(f.p)==1,
                "first anchor remains until final actual native tick"));
        f.post(CrossSubstitutionRules.PREPARATION_TICKS, () -> {
            h.assertFalse(CrossSubstitution.marked(f.p) || CrossSubstitution.active(f.p), "the full number of actual ticks remove preparation");
            h.assertTrue(CrossSubstitution.receipt(f.p).reason().equals("expired") && !CrossSubstitution.receipt(f.p).paid(), "expiry is a free terminal receipt");
            near(h, f.cp(), cp, "expiry spends no CP"); h.assertValueEqual(f.needles(), 3, "expiry consumes no needles");
        }); f.finish(CrossSubstitutionRules.PREPARATION_TICKS + 1);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_incomplete_crouch_refuses_free_without_consuming_mark_and_release_cannot_replay(GameTestHelper h) {
        Fixture f = new Fixture(h, true); f.markA(); double cp=f.cp();
        h.assertTrue(CrossSubstitution.release(f.p,f.wire(1)), "authenticated release is harmless");
        h.assertFalse(CrossSubstitution.press(f.p,f.wire(1)), "duplicate first input is not another mark");
        f.p.setShiftKeyDown(true);
        int remaining=CrossSubstitution.remainingTicks(f.p);
        h.assertFalse(f.press(), "fresh crouch with one mark is a free incomplete-pair refusal");
        h.assertTrue(CrossSubstitution.preparedPoints(f.p)==1 && !CrossSubstitution.active(f.p)
                && CrossSubstitution.remainingTicks(f.p)==remaining, "incomplete crouch preserves original free mark and lifetime");
        near(h,f.cp(),cp,"crouch and release have no payment"); h.assertValueEqual(f.needles(),3,"crouch returns no invented items");
        h.assertTrue(CrossSubstitution.receipt(f.p).reason().equals("two_points_required"),"incomplete-pair crouch refusal is explicit"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void overlapping_second_floor_point_refuses_freely_without_relocating_first(GameTestHelper h) {
        Fixture f=new Fixture(h,true);f.markA();Vec3 a=f.originA;double cp=f.cp();
        f.post(4,()->{
            f.aim(f.pointA.add(.25,0,0));h.assertFalse(f.press(),"distinct B coordinate with overlapping full standing body is refused");
            h.assertTrue(CrossSubstitution.receipt(f.p).reason().equals("points_overlap")&&!CrossSubstitution.marked(f.p),
                    "overlap closes both prepared marks with the exact refusal");
            near(h,f.cp(),cp,"overlapping B is free");h.assertTrue(f.spawns.media.isEmpty()&&f.needles()==3&&f.originA.equals(a),
                    "overlap invents no alternative floor, medium, movement or inventory mutation");
        });f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=12)
    public static void native_player_save_reload_and_logout_clear_preparation_preserving_depleted_budget(GameTestHelper h) {
        Fixture f=new Fixture(h,true);f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp",37);f.markA();
        CompoundTag fences=f.p.getPersistentData().getCompound(FENCES).copy();String health=HighPowerCombat.healthText(f.p);
        AbilityStorage.save(f.p);CompoundTag saved=f.p.saveWithoutId(new CompoundTag());
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
        h.assertFalse(CrossSubstitution.marked(f.p)||CrossSubstitution.active(f.p),"native logout event clears transient preparation");
        // Declared native codec reload, not a claimed process restart or fresh connection.
        f.p.load(saved);AbilityStorage.remove(f.p);AbilityStorage.get(f.p);f.p.setOnGround(true);f.p.setDeltaMovement(0,-.0784,0);f.p.connection.resetPosition();f.wire(2);
        CrossSubstitution.tick(f.p);h.assertFalse(CrossSubstitution.marked(f.p)||CrossSubstitution.active(f.p),"native player NBT contains no restorable prepared points");
        near(h,f.cp(),37,"native reload cannot refill depleted CP");
        h.assertTrue(f.p.getPersistentData().getCompound(FENCES).equals(fences)&&HighPowerCombat.healthText(f.p).equals(health),
                "native reload retains exact remaining request fence and HP");
        h.assertFalse(CrossSubstitution.press(f.p,f.wire(2)),"new authenticated input cannot erase persisted request fence");h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void native_logout_and_reload_cancel_future_slots_without_refund_or_custody_reset(GameTestHelper h) {
        Fixture f=new Fixture(h,true);f.begin(()->{
            var medium=f.first();var identity=medium.identity();double cp=f.cp();CompoundTag fences=f.p.getPersistentData().getCompound(FENCES).copy();
            var ledger=CrossNeedleRegistry.get(f.p.serverLevel()).snapshots(f.p.getUUID());
            AbilityStorage.save(f.p);CompoundTag saved=f.p.saveWithoutId(new CompoundTag());
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(f.p));
            h.assertFalse(CrossSubstitution.active(f.p)||medium.isContactEnabled(),"native logout retires the paid first flight and all queued emissions");
            f.p.load(saved);AbilityStorage.remove(f.p);AbilityStorage.get(f.p);f.p.setOnGround(true);f.p.setDeltaMovement(0,-.0784,0);f.p.connection.resetPosition();f.wire(4);
            h.assertTrue(f.p.getPersistentData().getCompound(FENCES).equals(fences),"native reload preserves full remaining cooldown and cast fences");
            near(h,f.cp(),cp,"native reload neither repays nor refunds the published needle");
            h.assertTrue(CrossNeedleRegistry.get(f.p.serverLevel()).snapshots(f.p.getUUID()).equals(ledger)
                    &&identity.equals(medium.identity())&&CrossNeedleRegistry.authentic(f.p,medium),"reload preserves exact physical custody identity");
            f.post(9,()->h.assertTrue(f.spawns.media.size()==1&&!CrossSubstitution.active(f.p)&&f.p.getOffhandItem().getCount()==2,
                    "no queued future publication revives after logout and native reload"));
        });f.finish(18);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void actual_clone_event_copies_paid_fences_and_retires_original_queued_emissions(GameTestHelper h) {
        Fixture f=new Fixture(h,true);f.begin(()->{
            Fixture replacement=new Fixture(h,true,16);CompoundTag fences=f.p.getPersistentData().getCompound(FENCES).copy();var medium=f.first();
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement.p,f.p,false));
            h.assertFalse(CrossSubstitution.active(f.p)||CrossSubstitution.marked(f.p)||CrossSubstitution.marked(replacement.p),
                    "native clone copies no free preparation or queued shooting context");
            h.assertTrue(replacement.p.getPersistentData().getCompound(FENCES).equals(fences)&&!medium.isContactEnabled(),
                    "native clone copies exact remaining cooldown/cast fences while retiring old attack");
            // Shared native clone policy owns replacement pools; retry must not alter them.
            double cp=replacement.cp();replacement.aim(replacement.pointA);h.assertFalse(replacement.press(),"clone cannot bypass copied request fence");
            near(h,replacement.cp(),cp,"clone retry cannot refill or debit shared budget");
            h.assertTrue(CrossNeedleRegistry.authentic(f.p,medium)&&f.p.getOffhandItem().getCount()==2,"clone leaves old one-item custody intact");
            f.post(9,()->h.assertValueEqual(f.spawns.media.size(),1,"original queued slots remain retired after actual clone event"));
        });f.finish(18);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void unsupported_first_surface_and_changed_saved_floor_refuse_without_payment(GameTestHelper h) {
        Fixture f = new Fixture(h,true); double cp=f.cp(); f.markA();
        f.block(BlockPos.containing(f.pointA).below(),Blocks.MAGMA_BLOCK.defaultBlockState());
        f.post(4,()->{
            f.aim(f.pointB); h.assertFalse(f.press(),"changed unsafe A floor cannot be retained while recording B");
            h.assertTrue(!CrossSubstitution.marked(f.p)&&!CrossSubstitution.active(f.p)&&f.spawns.media.isEmpty(),"unsafe preparation never launches");
            near(h,f.cp(),cp,"changed floor refusal is free"); h.assertValueEqual(f.needles(),3,"unsafe preparation leaves offhand originals");
        }); f.finish(5);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=60)
    public static void absent_target_two_needles_decorated_stock_and_blocked_origin_are_free(GameTestHelper h) { refusedBegin(h,0); }
    private static void refusedBegin(GameTestHelper h,int mode) {
        Fixture f=new Fixture(h,true); f.ready(()->{
            switch(mode) {
                case 0 -> f.target.discard();
                case 1 -> f.p.getOffhandItem().setCount(2);
                case 2 -> f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME,Component.literal("Decorated fixture"));
                default -> f.block(BlockPos.containing(f.originA),Blocks.STONE.defaultBlockState());
            }
            double cp=f.cp();int stock=f.needles();f.aim(f.target.getBoundingBox().getCenter());
            h.assertFalse(f.press(),"third tap refuses missing or unsafe preparation case "+mode+": "+CrossSubstitution.receipt(f.p));
            h.assertTrue(!CrossSubstitution.receipt(f.p).paid()&&f.spawns.media.isEmpty(),"rejection precedes first publication and payment");
            near(h,f.cp(),cp,"third-tap refusal is free");h.assertValueEqual(f.needles(),stock,"third-tap refusal leaves original stock");
        }); h.runAfterDelay(9,()->{f.verifyPosts();f.close();if(mode<3)refusedBegin(h,mode+1);else h.succeed();});
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void real_native_A_B_A_publications_use_zero_four_eight_ticks_and_exact_three_slots(GameTestHelper h) {
        Fixture f=new Fixture(h,true); Observation hit=f.observe(f.target); BigDecimal before=hp(f.target);
        BigDecimal share=CrossSubstitutionRules.damage(HighPowerGrowth.values(f.p).damage());
        f.begin(()->{
            h.assertValueEqual(f.spawns.media.size(),1,"third tap publishes only slot zero immediately");
            double paid=f.cp();for(int i=0;i<64;i++)CrossSubstitution.tick(f.p);
            for(int i=0;i<8;i++){CrossSubstitution.pre(new ServerTickEvent.Pre(()->true,f.p.server));CrossSubstitution.post(new ServerTickEvent.Post(()->true,f.p.server));}
            h.assertTrue(f.spawns.media.size()==1&&f.cp()==paid,"same-tick direct ticking neither catches up nor repays");
            f.post(3,()->h.assertValueEqual(f.spawns.media.size(),1,"three actual ticks cannot publish second needle"));
            f.post(4,()->h.assertValueEqual(f.spawns.media.size(),2,"second publication occurs on actual tick four"));
            f.post(7,()->h.assertValueEqual(f.spawns.media.size(),2,"seven actual ticks cannot publish third needle"));
            f.post(8,()->{
                h.assertValueEqual(f.spawns.media.size(),3,"third publication occurs on actual tick eight");
                h.assertTrue(f.spawns.ticks.equals(List.of(f.started,f.started+4,f.started+8)),"native join callbacks prove exact observed cadence");
                h.assertTrue(f.spawns.origins.get(0).equals(f.originA)&&f.spawns.origins.get(1).equals(f.originB)
                        &&f.spawns.origins.get(2).equals(f.originA),"published native positions follow frozen A/B/A order");
                h.assertValueEqual(new HashSet<>(f.spawns.media.stream().map(Entity::getUUID).toList()).size(),3,"three distinct native physical UUIDs");
                h.assertValueEqual(f.needles(),0,"exactly three actual offhand originals are in entity custody");
                h.assertTrue(CrossSubstitution.receipt(f.p).paidNeedles()==3&&CrossSubstitution.receipt(f.p).published()==3,
                        "receipt records three separate confirmed debits and publications");
                h.assertTrue(f.spawns.exactPayments,"each genuine join is followed by one exact per-needle CP commit");
            });
            f.post(12,()->{
                eq(h,hp(f.target),before.subtract(share),"ordinary native same-rank iframes suppress later equal .5D shares");
                h.assertTrue(hit.posts==1&&CrossSubstitution.receipt(f.p).contacts()==3&&CrossSubstitution.receipt(f.p).hurts()==1,
                        "three native selected contacts retain normal hurt cooldown without pooling failed shares");
                h.assertFalse(CrossSubstitution.active(f.p),"finished three-flight sequence closes controller");
                h.assertTrue(f.spawns.media.stream().allMatch(m->!m.isRemoved()&&!m.isContactEnabled()&&m.mediumSnapshot().getCount()==1),
                        "spent original needles remain harmless recoverable native entities");
                near(h,AbilityStorage.get(f.p).exp(ID),0,"three shots award zero mastery");
                h.assertTrue(DisplacedNeedleRegistry.existing(f.p).isEmpty(),"NEW321 never populates NEW311 one-flight registry");
            });
        }); f.finish(21);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void partial_abort_keeps_one_paid_real_needle_and_two_unused_offhand_items(GameTestHelper h) {
        Fixture f=new Fixture(h,true); f.begin(()->{
            double cp=f.cp();var medium=f.first();
            h.assertTrue(CrossSubstitution.abort(f.p,f.wire(f.inputs)),"authenticated active input abort closes scheduled continuation");
            h.assertFalse(CrossSubstitution.active(f.p)||CrossSubstitution.marked(f.p),"partial abort consumes preparation");
            h.assertTrue(!medium.isContactEnabled()&&!medium.isRemoved(),"already published item survives harmlessly");
            h.assertValueEqual(f.p.getOffhandItem().getCount(),2,"two not-yet-used actual items remain in the same offhand");
            near(h,f.cp(),cp,"partial abort refunds no committed CP");
            f.post(9,()->h.assertTrue(f.spawns.media.size()==1&&CrossSubstitution.receipt(f.p).paidNeedles()==1
                    &&CrossNeedleRegistry.availability(f.p)==CrossNeedleRegistry.Availability.OUTSTANDING,
                    "scheduled second and third slots never publish after cancellation; existing custody blocks a new batch"));
        });f.finish(18);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void second_native_publication_error_immediately_retires_existing_flights_and_queued_slots(GameTestHelper h) {
        Fixture f=new Fixture(h,true);AssertionError fault=new AssertionError("declared second native publication Error");
        f.begin(()->{
            var first=f.first();double cp=f.cp();h.assertTrue(first.isContactEnabled(),"first genuine native publication is still armed before controlled next boundary");
            f.hook(new Object(){@SubscribeEvent public void join(EntityJoinLevelEvent e){
                if(e.getEntity() instanceof DisplacedNeedleEntity m&&m.isCrossNeedle()&&m.getOwner()==f.p&&m!=first)throw fault;
            }});
            // Declared fault-boundary fixture: invoke the controller's existing private next
            // launch transition early, without constructing a Context, ledger token or permit.
            // The separate ordinary-world test proves genuine 0/4/8 schedule timing.
            h.assertTrue(invokeNextLaunchError(f.p)==fault,"the exact injected native Error is rethrown to this bounded fixture");
            h.assertFalse(CrossSubstitution.active(f.p)||CrossSubstitution.marked(f.p)||first.isContactEnabled(),
                    "Error immediately closes queued context and every previously armed flight");
            var receipt=CrossSubstitution.receipt(f.p);
            h.assertTrue(receipt.paidNeedles()==1&&receipt.published()==1,"unconfirmed second publication cannot claim another paid share");
            near(h,f.cp(),cp,"Error neither debits second share nor refunds the first");
            h.assertTrue(CrossNeedleRegistry.authentic(f.p,first)&&first.mediumSnapshot().getCount()==1,
                    "already published original remains exact harmless physical custody");
            h.assertTrue(f.p.getOffhandItem().getCount()==1&&CrossNeedleRegistry.get(f.p.serverLevel()).retainedEscrowCount(f.p.getUUID())==1,
                    "uncertain second original remains retained once; unused third item stays offhand");
            f.post(9,()->h.assertTrue(f.spawns.media.size()==2&&!CrossSubstitution.active(f.p)
                    &&CrossNeedleRegistry.availability(f.p)==CrossNeedleRegistry.Availability.OUTSTANDING,
                    "no future slot or new batch bypasses uncertain native publication"));
        });f.finish(18);
    }

    private static Throwable invokeNextLaunchError(ServerPlayer owner) {
        try {
            var contexts=CrossSubstitution.class.getDeclaredField("CONTEXTS");contexts.setAccessible(true);
            Object context=((Map<?,?>)contexts.get(null)).get(owner.getUUID());
            if(context==null)throw new AssertionError("Declared next-launch fixture has no genuine live context");
            var launch=CrossSubstitution.class.getDeclaredMethod("launch",context.getClass());launch.setAccessible(true);
            try {launch.invoke(null,context);return null;}
            catch(java.lang.reflect.InvocationTargetException failure){return failure.getCause();}
        } catch(ReflectiveOperationException failure){throw new AssertionError("Pinned private launch-boundary fixture unavailable",failure);}
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=50)
    public static void throwing_native_skill_callback_immediately_ends_all_queued_publications_without_refund(GameTestHelper h){throwingContactCase(h,false);}
    private static void throwingContactCase(GameTestHelper h,boolean error){
        Fixture f=new Fixture(h,true);Throwable fault=error?new AssertionError("declared native contact Error")
                :new IllegalStateException("declared native contact runtime failure");
        f.begin(()->{
            var medium=f.first();double cp=f.cp();BigDecimal before=hp(f.target);
            f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent e){
                if(e.target!=f.target||!e.skill.equals(SKILL))return;
                if(fault instanceof Error serious)throw serious;throw (RuntimeException)fault;
            }});
            Throwable observed=null;try{medium.tick();}catch(RuntimeException|Error failure){observed=failure;}
            h.assertTrue(observed==fault,"bounded native tick receives the exact injected contact failure");
            h.assertFalse(CrossSubstitution.active(f.p)||CrossSubstitution.marked(f.p)||medium.isContactEnabled(),
                    "throwing native contact immediately retires its whole queue and original armed medium");
            h.assertTrue(CrossSubstitution.receipt(f.p).reason().equals("contact_unconfirmed")
                    &&CrossSubstitution.receipt(f.p).paidNeedles()==1&&CrossSubstitution.receipt(f.p).published()==1,
                    "unconfirmed contact retains original confirmed publication and payment facts");
            eq(h,hp(f.target),before,"SkillAttack exception precedes native HP debit");near(h,f.cp(),cp,"throwing contact cannot refund its committed fee");
            h.assertTrue(f.p.getOffhandItem().getCount()==2&&CrossNeedleRegistry.authentic(f.p,medium),
                    "two unused originals remain in hand and one paid medium retains physical custody");
            f.post(9,()->h.assertTrue(f.spawns.media.size()==1&&!CrossSubstitution.active(f.p),"no future slot publishes after thrown native contact"));
        });h.runAfterDelay(18,()->{f.verifyPosts();f.close();if(!error)throwingContactCase(h,true);else h.succeed();});
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void source_context_loss_after_first_publication_spends_only_that_original(GameTestHelper h) {
        Fixture f=new Fixture(h,true);f.begin(()->{
            AbilityStorage.get(f.p).activated=false;
            f.post(5,()->{
                h.assertTrue(f.spawns.media.size()==1&&!CrossSubstitution.active(f.p),"next actual tick cancels all unlaunched slots on source loss");
                h.assertValueEqual(f.p.getOffhandItem().getCount(),2,"source loss leaves two unused offhand originals");
                h.assertTrue(!f.first().isContactEnabled()&&CrossSubstitution.receipt(f.p).paidNeedles()==1,"only published shot remains paid and harmless");
            });
        });f.finish(14);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void first_native_contact_alone_spends_one_frozen_half_D_without_bypasses(GameTestHelper h) {
        Fixture f=new Fixture(h,true);Observation hit=f.observe(f.target);BigDecimal before=hp(f.target);
        f.begin(()->{
            var medium=f.first();Vec3 origin=medium.position();BigDecimal damage=CrossSubstitutionRules.damage(HighPowerGrowth.values(f.p).damage());
            medium.tick(); // Declared single native tick: nearby side emitter reaches the real target body.
            eq(h,hp(f.target),before.subtract(damage),"actual first native intersection spends exactly frozen .5D");
            h.assertTrue(hit.posts==1&&CrossSubstitution.receipt(f.p).contacts()==1&&CrossSubstitution.receipt(f.p).hurts()==1,"one contact has one native hurt receipt");
            assertSource(h,hit.source,f.p,medium,origin);BigDecimal after=hp(f.target);medium.tick();medium.tick();
            eq(h,hp(f.target),after,"spent original cannot replay its damage generation");
            CrossSubstitution.cancelActive(f.p);
        });f.finish(9);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=50)
    public static void native_impact_cancel_throw_or_moving_target_never_retries_same_share(GameTestHelper h){impactCase(h,0);}
    private static void impactCase(GameTestHelper h,int mode){
        Fixture f=new Fixture(h,true);BigDecimal before=hp(f.target);boolean[] called={false};
        f.begin(()->{
            var medium=f.first();f.hook(new Object(){@SubscribeEvent public void impact(ProjectileImpactEvent e){
                if(e.getEntity()!=medium)return;called[0]=true;if(mode==0)e.setCanceled(true);
                else if(mode==1)throw new IllegalStateException("declared native impact refusal fixture");
                else f.target.setPos(f.target.position().add(0,4,0));
            }});
            medium.tick();medium.tick();h.assertTrue(called[0],"real native ProjectileImpact callback executed case "+mode);
            eq(h,hp(f.target),before,"native cancellation, exception or target mutation cannot claim frozen share");
            h.assertFalse(medium.isContactEnabled(),"terminal native witness cannot rearm");
            h.assertTrue(CrossSubstitution.receipt(f.p).paidNeedles()==1&&f.needles()==2,"refused contact retains exactly one paid real original");
            CrossSubstitution.cancelActive(f.p);
        });h.runAfterDelay(9,()->{f.verifyPosts();f.close();if(mode<2)impactCase(h,mode+1);else h.succeed();});
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=35)
    public static void skill_zero_denial_and_amplification_cannot_reassign_frozen_half_D_share(GameTestHelper h){skillAmountCase(h,false);}
    private static void skillAmountCase(GameTestHelper h,boolean amplified){
        Fixture f=new Fixture(h,true);boolean[] called={false};BigDecimal before=hp(f.target);
        f.hook(new Object(){@SubscribeEvent public void skill(SkillAttackEvent e){if(e.target!=f.target||!e.skill.equals(SKILL))return;
            called[0]=true;e.amount=amplified?e.amount*2:0;}});
        f.begin(()->{
            f.first().tick();h.assertTrue(called[0],"actual native SkillAttack callback executes");
            eq(h,hp(f.target),before,"zeroed or amplified contact cannot change frozen .5D allocation");
            h.assertTrue(!f.first().isContactEnabled()&&CrossSubstitution.receipt(f.p).paidNeedles()==1,
                    "callback refusal spends the one published attempt and cannot pool its share");CrossSubstitution.cancelActive(f.p);
        });h.runAfterDelay(9,()->{f.verifyPosts();f.close();if(!amplified)skillAmountCase(h,true);else h.succeed();});
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=35)
    public static void generic_cross_skill_string_cannot_forge_native_contact_and_ordinary_combat_remains(GameTestHelper h){
        Fixture f=new Fixture(h,true);BigDecimal before=hp(f.target);double cp=f.cp();
        h.assertFalse(HighPowerCombat.attack(f.p,SKILL,f.target,n("5")),"skill string cannot construct a closed NEW321 impact proof");
        eq(h,hp(f.target),before,"spoof causes no exact debit");near(h,f.cp(),cp,"spoof causes no CP debit");
        h.assertTrue(f.target.hurt(f.p.damageSources().playerAttack(f.p),5),"ordinary native combat remains available");
        eq(h,hp(f.target),before.subtract(n("5")),"ordinary native exact adapter damage remains intact");h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=60)
    public static void armor_protection_resistance_and_absorption_match_paired_native_thrown_controls(GameTestHelper h){defenseCase(h,true);}
    private static void defenseCase(GameTestHelper h,boolean exact){
        Fixture f=new Fixture(h,exact);Zombie control=f.mob(f.target.position().add(5,0,0),false);defenses(h,f.target);defenses(h,control);
        f.hook(new Discount(f.target));Observation hit=f.observe(f.target),ordinary=f.observe(control);
        f.begin(()->{
            var medium=f.first();Vec3 origin=medium.position(),nativeOrigin=origin.add(control.position().subtract(f.target.position()));BigDecimal before=hp(f.target);
            h.assertTrue(control.hurt(new DamageSource(f.p.damageSources().thrown(medium,f.p).typeHolder(),medium,f.p,nativeOrigin),5),"paired ordinary THROWN control accepts");
            medium.tick();h.assertTrue(hit.posts==1&&ordinary.posts==1&&hit.armor>0&&hit.effects>0&&hit.enchantments>0&&hit.absorption==.5F,
                    "every native reducer executes for actual cross-needle contact");
            h.assertTrue(hit.armor==ordinary.armor&&hit.effects==ordinary.effects&&hit.enchantments==ordinary.enchantments
                    &&hit.absorption==ordinary.absorption&&Math.abs(hit.damage-ordinary.damage)<.00001,"ordinary native thrown control preserves all reducer observations");
            if(exact){BigDecimal residual=DamageMitigation.mitigate(n("5"),new DefenseProfile(BigDecimal.valueOf(f.target.getArmorValue()),
                    BigDecimal.valueOf(f.target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)),1,4)).subtract(n(".5"));
                eq(h,hp(f.target),before.subtract(residual),"exact health retains only native-mitigated residual");}
            else h.assertTrue(f.target.getHealth()==control.getHealth(),"ordinary hostile keeps native float damage");
            assertSource(h,hit.source,f.p,medium,origin);CrossSubstitution.cancelActive(f.p);
        });h.runAfterDelay(9,()->{f.verifyPosts();f.close();if(exact)defenseCase(h,false);else h.succeed();});
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=60)
    public static void directional_shield_uses_actual_needle_origin_and_preserves_native_control(GameTestHelper h){shieldCase(h,0);}
    private static void shieldCase(GameTestHelper h,int mode){
        Fixture f=new Fixture(h,mode<2);Zombie control=f.mob(f.target.position().add(5,0,0),false);float yaw=mode%2==0?45:225;
        shield(f.target,yaw);shield(control,yaw);f.hook(new Discount(f.target));Observation hit=f.observe(f.target),ordinary=f.observe(control);
        f.begin(()->{
            face(f.target,yaw);face(control,yaw);h.assertTrue(f.target.isBlocking()&&control.isBlocking(),"native shield raise delay elapsed during real preparation");
            var medium=f.first();Vec3 origin=medium.position();BigDecimal before=hp(f.target);boolean blocked=mode%2==0;
            h.assertTrue(f.target.getLookAngle().dot(f.p.position().subtract(f.target.position()))
                    *f.target.getLookAngle().dot(origin.subtract(f.target.position()))<0,"caster and actual needle occupy opposite native shield hemispheres");
            Vec3 nativeOrigin=origin.add(control.position().subtract(f.target.position()));
            boolean accepted=control.hurt(new DamageSource(f.p.damageSources().thrown(medium,f.p).typeHolder(),medium,f.p,nativeOrigin),5);
            medium.tick();h.assertTrue(accepted!=blocked&&hit.blocked==blocked&&ordinary.blocked==blocked,"native directional shield uses real incoming needle position");
            eq(h,hp(f.target),blocked?before:before.subtract(n("5")),"actual shield hemisphere controls exact and ordinary native HP");
            h.assertTrue(hit.blockedDamage==ordinary.blockedDamage&&hit.damage==ordinary.damage,"paired native block and residual amounts agree");
            assertSource(h,hit.source,f.p,medium,origin);CrossSubstitution.cancelActive(f.p);
        });h.runAfterDelay(9,()->{f.verifyPosts();f.close();if(mode<3)shieldCase(h,mode+1);else h.succeed();});
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=40)
    public static void existing_native_iframes_keep_absorption_and_refuse_equal_cross_share(GameTestHelper h){iframeCase(h,true);}
    private static void iframeCase(GameTestHelper h,boolean exact){
        Fixture f=new Fixture(h,exact);f.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,2));f.hook(new Discount(f.target));
        f.begin(()->{
            f.target.setAbsorptionAmount(10);BigDecimal before=hp(f.target);
            h.assertTrue(f.target.hurt(f.p.damageSources().playerAttack(f.p),5),"ordinary native hit establishes real same-rank iframe");
            h.assertTrue(f.target.getAbsorptionAmount()==5,"ordinary native hit spends absorption exactly once");
            f.first().tick();eq(h,hp(f.target),before,"equal cross share cannot bypass native iframe");
            h.assertTrue(f.target.getAbsorptionAmount()==5&&CrossSubstitution.receipt(f.p).hurts()==0,"rejected equal share cannot re-spend absorption");
            CrossSubstitution.cancelActive(f.p);
        });h.runAfterDelay(9,()->{f.verifyPosts();f.close();if(exact)iframeCase(h,false);else h.succeed();});
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=260)
    public static void mutable_skill_incoming_pre_and_post_absorption_callbacks_revalidate_exact_native_contact(GameTestHelper h){mutationCase(h,0,0,true);}
    private static void mutationCase(GameTestHelper h,int stage,int mutation,boolean exact){
        Fixture f=new Fixture(h,exact);f.hook(new Discount(f.target));Mutation gate=new Mutation(f,stage,mutation);f.hook(gate);Observation hit=f.observe(f.target);
        if(stage==3){f.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,0));f.target.setAbsorptionAmount(2);}
        f.begin(()->{
            BigDecimal before=hp(f.target);f.first().tick();h.assertTrue(gate.called,"actual mutable callback executes stage="+stage+" mutation="+mutation+" exact="+exact);
            eq(h,hp(f.target),before,"late mutation cannot retain exact or ordinary HP debit");
            h.assertTrue(hit.damage==0&&!f.first().isContactEnabled(),"late invalidation consumes flight without a positive native proxy debit");
            if(stage==3)h.assertTrue(gate.afterPre&&gate.reduction==2&&f.target.getAbsorptionAmount()==2,"positive post-Pre native absorption callback preserves original pool on invalidation");
            CrossSubstitution.cancelActive(f.p);
        });h.runAfterDelay(9,()->{f.verifyPosts();f.close();if(exact)mutationCase(h,stage,mutation,false);
            else if(mutation<2)mutationCase(h,stage,mutation+1,true);else if(stage<3)mutationCase(h,stage+1,0,true);else h.succeed();});
    }

    private static void face(LivingEntity e,float yaw){e.setYRot(yaw);e.setYHeadRot(yaw);e.yBodyRot=yaw;e.setXRot(0);}
    private static void shield(LivingEntity e,float yaw){face(e,yaw);e.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));e.startUsingItem(InteractionHand.OFF_HAND);}
    private static void defenses(GameTestHelper h,LivingEntity e){var helmet=new ItemStack(Items.DIAMOND_HELMET);helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),4);helmet.set(DataComponents.UNBREAKABLE,new Unbreakable(true));e.setItemSlot(EquipmentSlot.HEAD,helmet);e.getAttribute(Attributes.ARMOR).setBaseValue(5);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,0));e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,200,0));e.setAbsorptionAmount(.5F);}
    private static void assertSource(GameTestHelper h,DamageSource source,ServerPlayer p,DisplacedNeedleEntity medium,Vec3 origin){
        h.assertTrue(source!=null&&source.is(DamageTypes.THROWN)&&source.is(DamageTypeTags.IS_PROJECTILE)&&source.getEntity()==p
                &&source.getDirectEntity()==medium&&origin.equals(source.getSourcePosition())&&origin.equals(source.sourcePositionRaw()),"native THROWN source retains actual cross needle and incoming origin");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR)||source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)||source.is(DamageTypeTags.BYPASSES_EFFECTS)
                ||source.is(DamageTypeTags.BYPASSES_RESISTANCE)||source.is(DamageTypeTags.BYPASSES_SHIELD)||source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                ||source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||source.is(DamageTypeTags.IS_FIRE)||source.is(DamageTypeTags.IS_EXPLOSION),"cross needle adds no defense bypass or environmental damage family");
    }
    public static final class Discount{
        final LivingEntity target;Discount(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void skill(SkillAttackEvent e){if(e.target==target&&e.skill.equals(SKILL))e.amount=5;}
    }
    public static final class Mutation{
        final Fixture f;final int stage,mutation;boolean called,sawPre,afterPre;float reduction;
        Mutation(Fixture f,int stage,int mutation){this.f=f;this.stage=stage;this.mutation=mutation;}
        void change(){if(called)return;called=true;if(mutation==0)f.p.setPos(f.p.position().add(.1,0,0));else if(mutation==1)f.target.setPos(f.target.position().add(0,4,0));else f.first().setOwner(f.target);}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e){if(stage==0&&e.target==f.target&&e.skill.equals(SKILL))change();}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()!=f.target)return;if(stage==1)change();if(stage==3)e.addReductionModifier(DamageContainer.Reduction.ABSORPTION,(container,amount)->{afterPre=sawPre;reduction=amount;change();return amount;});}
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e){if(e.getEntity()!=f.target)return;sawPre=true;if(stage==2)change();}
    }
    public static final class Observation{
        final LivingEntity target;DamageSource source;int posts;boolean blocked;float damage,armor,effects,enchantments,absorption,blockedDamage;
        Observation(LivingEntity target){this.target=target;}
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e){if(e.getEntity()==target)source=e.getSource();}
        @SubscribeEvent public void shield(LivingShieldBlockEvent e){if(e.getEntity()==target){blocked=e.getBlocked();blockedDamage=e.getBlockedDamage();}}
        @SubscribeEvent public void post(LivingDamageEvent.Post e){if(e.getEntity()!=target)return;posts++;damage=e.getNewDamage();armor=e.getReduction(DamageContainer.Reduction.ARMOR);effects=e.getReduction(DamageContainer.Reduction.MOB_EFFECTS);enchantments=e.getReduction(DamageContainer.Reduction.ENCHANTMENTS);absorption=e.getReduction(DamageContainer.Reduction.ABSORPTION);}
    }
    public static final class Spawns{
        final Fixture f;final List<DisplacedNeedleEntity> media=new ArrayList<>();final List<Integer> ticks=new ArrayList<>();final List<Vec3> origins=new ArrayList<>();
        int inspected;double before;boolean exactPayments=true;
        Spawns(Fixture f){this.f=f;}
        @SubscribeEvent public void join(EntityJoinLevelEvent e){
            if(!(e.getEntity() instanceof DisplacedNeedleEntity m)||!m.isCrossNeedle()||m.getOwner()!=f.p)return;
            media.add(m);f.entities.add(m);ticks.add(f.p.server.getTickCount());origins.add(m.position());before=f.cp();
            f.h.assertFalse(m.isAdmitted(),"native join exposes only inert pending entity before CP commit");
            f.h.assertValueEqual(f.p.getOffhandItem().getCount(),3-media.size(),"one actual item is escrowed before each native join callback");
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void pre(ServerTickEvent.Pre e){
            if(e.getServer()!=f.p.server||media.size()==inspected)return;
            exactPayments&=Math.abs(f.cp()-(before-CrossSubstitutionRules.needleCpCost(HighPowerGrowth.values(f.p).position())))<1e-6;inspected=media.size();
        }
        void firstPayment(){exactPayments&=Math.abs(f.cp()-(before-CrossSubstitutionRules.needleCpCost(HighPowerGrowth.values(f.p).position())))<1e-6;inspected=media.size();}
    }
    public static final class PostCheck{
        final Fixture f;final int tick;final Runnable action;boolean ran;
        PostCheck(Fixture f,int delay,Runnable action){this.f=f;tick=f.p.server.getTickCount()+delay;this.action=action;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post e){
            if(ran||e.getServer()!=f.p.server||e.getServer().getTickCount()!=tick)return;ran=true;
            try{action.run();}catch(Throwable failure){if(f.postFailure==null)f.postFailure=failure;}
        }
    }
    private static final class Fixture implements AutoCloseable{
        final GameTestHelper h;final ServerPlayer p;final EmbeddedChannel channel;final Zombie target;final Vec3 source,pointA,pointB;
        final List<Entity> entities=new ArrayList<>();final List<Object> hooks=new ArrayList<>();final List<PostCheck> checks=new ArrayList<>();
        final Map<BlockPos,BlockState> changed=new LinkedHashMap<>();final Set<ChunkPos> held=new LinkedHashSet<>();final UUID ticket=UUID.randomUUID();final Spawns spawns;
        Vec3 originA,originB,surfaceA,surfaceB;long inputs;int started;Throwable postFailure;boolean closed;
        Fixture(GameTestHelper h,boolean exact){this(h,exact,0);}
        Fixture(GameTestHelper h,boolean exact,int offset){
            this.h=h;source=h.absoluteVec(new Vec3(10.5+offset,5,6.5));pointA=h.absoluteVec(new Vec3(9+offset,5,12.5));pointB=h.absoluteVec(new Vec3(12+offset,5,12.5));
            for(int x=5;x<=14;x++)for(int z=3;z<=17;z++)block(h.absolutePos(new BlockPos(x+offset,4,z)),Blocks.STONE.defaultBlockState());
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"CS"+UUID.randomUUID().toString().substring(0,10)),false);
            p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);acknowledgeLogin();
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=false;p.getAbilities().instabuild=false;p.moveTo(source.x,source.y,source.z,0,0);
            p.setPose(Pose.STANDING);p.setShiftKeyDown(false);p.setNoGravity(false);p.fallDistance=0;p.setOnGround(true);
            p.move(MoverType.SELF,new Vec3(0,-.0784,0));p.setDeltaMovement(0,-.0784,0);p.connection.resetPosition();
            h.assertTrue(p.onGround()&&p.position().equals(source),"native downward floor collision establishes genuine safe source");
            h.assertTrue(HighPowerCombat.enroll(p,3),"declared real caster enrollment");var state=AbilityStorage.get(p);state.selectCategory(HighPowerCategories.TELEPORTER);state.setLevel(3);
            state.learn(SafeLandingProbe.ID);state.learn(DisplacedProjection.ID);state.learn(ID);state.activated=true;state.overloadFine=true;
            h.assertTrue(state.presets.edit(0,0,ID,state::learned),"native learned skill occupies mapped slot zero");HighPowerResources.initializeBudget(p);
            p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(AcademyCraft.NEEDLE.get(),3));target=mob(h.absoluteVec(new Vec3(10.5+offset,5,12.5)),exact);
            spawns=new Spawns(this);hook(spawns);hook(new HighPowerPhaseSidestepRuntimeTests.NativePlayerPump(p,h.testInfo.getTimeoutTicks()+5));
            h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});
        }
        Zombie mob(Vec3 at,boolean exact){Zombie m=EntityType.HUSK.create(h.getLevel());m.setNoAi(true);m.setNoGravity(true);m.setSilent(true);m.getAttribute(Attributes.ARMOR).setBaseValue(0);m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);m.setPos(at);h.assertTrue(h.getLevel().addFreshEntity(m),"native stationary hostile admitted");entities.add(m);if(exact)h.assertTrue(HighPowerCombat.enroll(m,5),"declared exact hostile enrollment");return m;}
        void acknowledgeLogin(){channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){if(packet instanceof ClientboundPlayerPositionPacket position)p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));ReferenceCountUtil.release(packet);}}
        void block(BlockPos pos,BlockState state){ChunkPos chunk=new ChunkPos(pos);if(held.add(chunk)){h.getLevel().getChunkSource().addRegionTicket(TICKET,chunk,2,ticket);h.getLevel().getChunk(chunk.x,chunk.z);}changed.putIfAbsent(pos,h.getLevel().getBlockState(pos));h.getLevel().setBlockAndUpdate(pos,state);}
        void aim(Vec3 at){Vec3 d=at.subtract(p.getEyePosition());p.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));p.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));}
        String wire(long input){return new HighPowerInput.Input(0,input,p.getId(),LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),AbilityStorage.get(p))).wire();}
        boolean press(){return CrossSubstitution.press(p,wire(++inputs));}
        Vec3 nativeSurface(){var hit=h.getLevel().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(32)),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
            h.assertTrue(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getDirection()==net.minecraft.core.Direction.UP,
                    "independent native fixture ray confirms actual first-UP surface: "+hit.getLocation());return hit.getLocation();}
        void markA(){aim(pointA);surfaceA=nativeSurface();h.assertTrue(press(),"genuine authenticated first tap captures native floor A: "+CrossSubstitution.receipt(p));originA=CrossSubstitution.receipt(p).arrival();}
        void ready(Runnable action){markA();post(4,()->{aim(pointB);surfaceB=nativeSurface();h.assertTrue(press(),"genuine second tap captures native floor B: "+CrossSubstitution.receipt(p));originB=CrossSubstitution.receipt(p).arrival();});post(8,action);}
        void begin(Runnable action){ready(()->{aim(target.getBoundingBox().getCenter());double cp=cp();started=p.server.getTickCount();h.assertTrue(press(),"genuine third tap launches first actual original: "+CrossSubstitution.receipt(p));
            near(h,cp(),cp-CrossSubstitutionRules.needleCpCost(HighPowerGrowth.values(p).position()),"first native publication has exact per-needle debit");spawns.firstPayment();action.run();});}
        DisplacedNeedleEntity first(){return spawns.media.getFirst();}
        double cp(){return HighPowerResources.cp(p);}
        int needles(){int count=0;for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);if(stack.is(AcademyCraft.NEEDLE.get()))count+=stack.getCount();}return count;}
        void hook(Object hook){hooks.add(hook);NeoForge.EVENT_BUS.register(hook);}
        Observation observe(LivingEntity e){Observation o=new Observation(e);hook(o);return o;}
        void post(int delay,Runnable action){PostCheck c=new PostCheck(this,delay,action);checks.add(c);hook(c);}
        void verifyPosts(){if(postFailure instanceof RuntimeException failure)throw failure;if(postFailure instanceof Error failure)throw failure;if(postFailure!=null)throw new AssertionError(postFailure);h.assertTrue(checks.stream().allMatch(c->c.ran),"all requested actual native Post observations executed");}
        void finish(int ticks){h.runAfterDelay(ticks,()->{verifyPosts();h.succeed();});}
        public void close(){if(closed)return;closed=true;hooks.forEach(NeoForge.EVENT_BUS::unregister);CrossSubstitution.clear(p);HighPowerBracerSession.remove(p);AbilityStorage.remove(p);p.server.getPlayerList().remove(p);entities.forEach(Entity::discard);changed.forEach((pos,state)->h.getLevel().setBlockAndUpdate(pos,state));held.forEach(chunk->h.getLevel().getChunkSource().removeRegionTicket(TICKET,chunk,2,ticket));channel.finishAndReleaseAll();}
    }
}
